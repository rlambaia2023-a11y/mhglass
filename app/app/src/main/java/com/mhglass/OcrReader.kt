package com.mhglass

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * OCR no próprio celular, sem nuvem, com ML Kit.
 *
 * Por que OCR separado, se o Gemini também "vê" a foto?
 * 1. O texto lido vira dado explícito, que o app pode falar, cruzar
 *    com a busca e guardar no histórico, mesmo sem internet depois.
 * 2. Para quem não enxerga, o texto lido é a prova do que foi lido,
 *    não só a interpretação da IA.
 * 3. Se o Gemini falhar, o app ainda consegue ler o texto em voz alta.
 */
object OcrReader {

    suspend fun readText(context: Context, image: File): String =
        suspendCancellableCoroutine { cont ->
            try {
                val input = InputImage.fromFilePath(context, Uri.fromFile(image))
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(input)
                    .addOnSuccessListener { visionText ->
                        if (cont.isActive) cont.resume(visionText.text.orEmpty().trim())
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume("")
                    }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
}
