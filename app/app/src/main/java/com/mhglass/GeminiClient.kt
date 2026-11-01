package com.mhglass

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * mhglass — só Gemini, sem OpenAI, conforme a regra permanente.
 *
 * Modelo Lite primeiro (custo menor), sem retry em 429: se a cota
 * acabar, devolve a mensagem de cota e o pipeline fala isso e para,
 * em vez de tentar outro modelo para contornar a cota.
 *
 * O Gemini recebe três coisas juntas: o texto do OCR (a fonte do
 * texto), a foto e os resultados da busca na internet. Regras do
 * prompt: responder em pt-BR falado e curto; se o OCR trouxer uma
 * pergunta, responder a pergunta; se OCR e imagem discordarem, dizer
 * isso; citar fonte só se ela realmente sustentar a resposta; sem
 * resultado de busca, não inventar site nenhum.
 */
object GeminiClient {

    private val http = OkHttpClient()

    /** Modelo Lite. Mudar só se um teste real provar falta de capacidade. */
    var model: String = "gemini-2.5-flash-lite"

    data class Outcome(val text: String, val quotaExhausted: Boolean)

    suspend fun answerTirar(
        apiKey: String,
        image: File?,
        ocrText: String,
        question: String,
        webHits: List<WebSearchClient.Hit>,
    ): Outcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Outcome(
                "Coloque sua chave Gemini em Configurações. Ela fica só neste celular.",
                false
            )
        }
        val instruction = buildString {
            append("Você responde para ser ouvido, não lido. Responda em português, ")
            append("de forma falada, clara e curta, sem listas longas e sem ")
            append("expressões de texto de IA.\n\n")
            append("Texto extraído por OCR da foto:\n")
            append(if (ocrText.isBlank()) "(nenhum texto encontrado)" else ocrText)
            append("\n\n")
            if (question.isNotBlank()) {
                append("Pergunta falada pelo usuário:\n").append(question).append("\n\n")
            }
            append("Resultados da busca na internet para cruzar:\n")
            append(WebSearchClient.toPromptText(webHits))
            append("\n\nRegras: ")
            append("o OCR é a fonte do texto. Se o OCR trouxer uma pergunta, responda-a. ")
            append("Se o OCR trouxer um texto comum, leia e explique o que ele diz. ")
            append("Se o OCR estiver vazio, descreva só a imagem. ")
            append("Cruze o OCR com a foto e com a busca. Se houver contradição entre ")
            append("OCR e imagem, diga isso. Cite a fonte de forma curta no fim só se ")
            append("ela realmente sustentar a resposta. Sem resultado de busca, não ")
            append("cite site nenhum e não invente fonte. Na dúvida, diga que não tem certeza.")
        }
        val parts = JSONArray().put(JSONObject().put("text", instruction))
        if (image != null && image.exists()) {
            val b64 = Base64.encodeToString(image.readBytes(), Base64.NO_WRAP)
            parts.put(JSONObject().apply {
                put("inlineData", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", b64))
            })
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().put("temperature", 0.4))
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (resp.code == 429) {
                        return@withContext Outcome(
                            "Cota do Gemini acabou. Não tentei outro modelo. Tente de novo mais tarde.",
                            true
                        )
                    }
                    return@withContext Outcome(
                        "Gemini retornou ${resp.code}. Verifique a chave e o modelo ($model).",
                        false
                    )
                }
                val answer = try {
                    JSONObject(text)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                } catch (e: Exception) {
                    "Não consegui ler a resposta do Gemini: ${e.message}"
                }
                Outcome(answer, false)
            }
        } catch (e: Exception) {
            Outcome("Falha ao falar com o Gemini: ${e.message}", false)
        }
    }
}
