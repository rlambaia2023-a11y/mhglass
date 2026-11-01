package com.mhglass

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * mhglass — o fluxo do comando Tirar, sempre nesta ordem:
 *
 * 1) foto (se takeNewPhoto = true; se false, importa a foto nova
 *    que o botão do óculos ou a vigia já detectaram)
 * 2) importação por Wi-Fi do óculos
 * 3) OCR no celular (ML Kit, sem nuvem) — "ler o que está na foto"
 * 4) busca na internet (Brave, opcional) — "cruzar informações"
 * 5) Gemini Lite cruzando OCR + foto + busca — resposta falada
 *
 * Cada passo é anunciado pela função `announce`, que quem chama
 * liga ao TTS do óculos. Se um passo falhar, o resultado traz
 * `failure` e a resposta é null: o chamador fala a falha e para,
 * sem inventar resposta. Cache de 24 h para o mesmo OCR evita
 * gastar cota do Gemini à toa.
 */
object MhGlassPipeline {

    data class Result(
        val answer: String?,
        val photo: File?,
        val ocrText: String,
        val sources: List<WebSearchClient.Hit>,
        val fromCache: Boolean,
        val crossedOnInternet: Boolean,
        val failure: String?,
    )

    data class Keys(
        val gemini: String,
        val brave: String,
        val glassesIp: String,
    )

    suspend fun run(
        context: Context,
        transport: GlassesTransport,
        takeNewPhoto: Boolean,
        question: String,
        keys: Keys,
        announce: (String) -> Unit,
    ): Result {
        if (takeNewPhoto) {
            announce("Tirando foto.")
            val photoOk = withTimeoutOrNull(15_000) {
                transport.takePhoto()
            } ?: false
            if (!photoOk) {
                return Result(
                    null, null, "", emptyList(), false, false,
                    "Não consegui tirar a foto. Verifique se o óculos está conectado."
                )
            }
            announce("Foto tirada. Importando do óculos.")
        } else {
            announce("Nova foto no óculos. Importando.")
        }

        // Importação: IP salvo primeiro, depois os IPs usuais de hotspot.
        // A foto nova pode demorar segundos para aparecer na lista.
        val candidates = listOfNotNull(
            keys.glassesIp.trim().ifBlank { null },
            "192.168.43.1", "192.168.4.1", "192.168.1.1",
            "192.168.0.1", "10.0.0.1"
        ).distinct()
        val downloader = AlbumDownloader(context)
        var imported: File? = null
        for (attempt in 1..6) {
            for (ip in candidates) {
                val list = try {
                    downloader.fetchFileList(ip)
                } catch (_: Exception) {
                    emptyList()
                }
                if (list.isNotEmpty()) {
                    // Último da lista tende a ser o mais recente; se a ordem
                    // do seu firmware for outra, ajuste aqui após o teste.
                    imported = downloader.fetchOne(ip, list.last())
                    if (imported != null) break
                }
            }
            if (imported != null) break
            delay(2_000)
        }
        if (imported == null) {
            return Result(
                null, null, "", emptyList(), false, false,
                "Foto tirada, mas não consegui importar. Confira o IP do óculos."
            )
        }

        announce("Foto importada. Lendo o que está na foto.")
        val ocrText = try {
            OcrReader.readText(context, imported)
        } catch (_: Exception) {
            ""
        }
        if (ocrText.isBlank()) {
            announce("Não encontrei texto na foto. Descrevendo a imagem.")
        } else {
            announce("Texto lido. Buscando na internet para cruzar.")
        }

        // Cache antes de gastar cota: mesmo OCR + mesma pergunta.
        val cacheKey = AnswerCache.keyFor(ocrText, question)
        val cached = AnswerCache.get(context, cacheKey)
        if (cached != null) {
            return Result(cached, imported, ocrText, emptyList(), true, false, null)
        }

        // Cruzamento na internet. Sem chave ou sem resultado, o app
        // AVISA e segue só com foto + OCR, sem inventar fonte.
        val searchQuery = question.ifBlank { ocrText }
        val hits = WebSearchClient.search(keys.brave, searchQuery)
        val crossed = hits.isNotEmpty()
        if (!crossed) {
            announce("Sem cruzamento na internet. Resposta só pela foto e pelo texto lido.")
        }

        val outcome = GeminiClient.answerTirar(
            keys.gemini, imported, ocrText, question, hits
        )
        if (!outcome.quotaExhausted && outcome.text.isNotBlank()) {
            AnswerCache.put(context, cacheKey, outcome.text)
        }
        return Result(
            outcome.text, imported, ocrText, hits, false, crossed, null
        )
    }
}
