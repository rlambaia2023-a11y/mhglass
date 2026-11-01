package com.mhglass

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Cache do mhglass, pela disciplina de cota do Gemini:
 * o mesmo texto lido (OCR igual) em 24 horas reaproveita a resposta,
 * sem gastar outra chamada. Chave = hash SHA-256 do OCR normalizado.
 * Nada de rede aqui, só arquivo no celular. Expira sozinho.
 */
object AnswerCache {

    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun keyFor(ocrText: String, question: String): String {
        val normalized = (ocrText.trim() + "\n" + question.trim())
            .lowercase().replace(Regex("\\s+"), " ")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun fileFor(context: Context, key: String): File {
        val dir = File(context.cacheDir, "mhglass-cache").apply { mkdirs() }
        return File(dir, "$key.txt")
    }

    fun get(context: Context, key: String): String? {
        return try {
            val file = fileFor(context, key)
            if (!file.exists()) return null
            if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
                file.delete()
                return null
            }
            file.readText().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun put(context: Context, key: String, answer: String) {
        try {
            fileFor(context, key).writeText(answer)
        } catch (_: Exception) {
            // Cache é otimização, nunca pode quebrar o fluxo.
        }
    }
}
