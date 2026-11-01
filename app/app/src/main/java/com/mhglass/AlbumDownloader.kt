package com.mhglass

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Importação por Wi-Fi do óculos.
 *
 * ATENÇÃO: o formato exato do manifesto varia conforme o firmware.
 * O exemplo usa http://<ip>/files/media.config com um nome por linha.
 * O documento WiFi do SDK descreve /manifest.json e /files/<nome>.
 * Na primeira importação real, veja o log, ajuste o parser e só
 * depois marque a importação como pronta. Não apague nada do óculos
 * até a foto abrir no celular.
 */
class AlbumDownloader(private val ctx: Context) {
    private val http = OkHttpClient()

    suspend fun fetchFileList(baseIp: String): List<String> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "http://$baseIp/files/media.config",
            "http://$baseIp/manifest.json",
        )
        for (url in urls) {
            try {
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string().orEmpty() }
                val names = body.lines()
                    .map { it.trim().trim('"', ',', ' ') }
                    .filter {
                        it.endsWith(".jpg", true) || it.endsWith(".mp4", true) ||
                            it.endsWith(".wav", true)
                    }
                if (names.isNotEmpty()) return@withContext names
            } catch (_: Exception) {
                // tenta o próximo formato
            }
        }
        emptyList()
    }

    suspend fun fetchOne(baseIp: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(ctx.getExternalFilesDir(null), "mhglass")
                    .apply { mkdirs() }
                val out = File(dir, fileName)
                http.newCall(
                    Request.Builder().url("http://$baseIp/files/$fileName").build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body!!.byteStream().use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
                out
            } catch (_: Exception) {
                null
            }
        }
}
