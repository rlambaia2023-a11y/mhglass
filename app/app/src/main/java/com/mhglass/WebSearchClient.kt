package com.mhglass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Cruzamento na internet do mhglass, via Brave Search.
 *
 * A chave Brave é opcional e fica só no celular. Sem ela, ou sem
 * internet, o cliente devolve lista vazia e o pipeline AVISA isso
 * na voz, em vez de fingir que cruzou. O que vai para a busca é
 * só o texto a pesquisar (OCR ou pergunta), nunca a foto.
 *
 * Retorno: até 3 resultados, cada um com título, trecho e link.
 * O Gemini recebe esses trechos e deve citar a fonte só se ela
 * realmente sustentar a resposta.
 */
object WebSearchClient {

    data class Hit(val title: String, val snippet: String, val url: String)

    private val http = OkHttpClient()

    suspend fun search(apiKey: String, query: String): List<Hit> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()
            try {
                val encoded = java.net.URLEncoder.encode(query.take(400), "UTF-8")
                val req = Request.Builder()
                    .url("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=5&search_lang=pt")
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey.trim())
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string().orEmpty()
                    val results = JSONObject(body)
                        .optJSONObject("web")?.optJSONArray("results")
                        ?: return@withContext emptyList()
                    val hits = mutableListOf<Hit>()
                    for (i in 0 until minOf(results.length(), 5)) {
                        val item = results.optJSONObject(i) ?: continue
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                        val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                        hits.add(Hit(title, item.optString("description"), url))
                    }
                    hits.take(3)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Texto curto para colar no prompt do Gemini, com fontes numeradas. */
    fun toPromptText(hits: List<Hit>): String {
        if (hits.isEmpty()) return "(sem resultados de busca)"
        return hits.mapIndexed { index, hit ->
            "[${index + 1}] ${hit.title} — ${hit.snippet} (${hit.url})"
        }.joinToString("\n")
    }
}
