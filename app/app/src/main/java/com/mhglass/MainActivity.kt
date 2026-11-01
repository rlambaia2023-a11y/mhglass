package com.mhglass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * mhglass — tela acessível, para teste e configuração.
 *
 * O uso no dia a dia não precisa desta tela: é só ligar o serviço
 * ("Ligar mhglass") e usar o botão do óculos ou dizer "tirar".
 *
 * Tela: um botão TIRAR grande, ligar/desligar o serviço, teste de
 * som no óculos, configurações locais (chaves ficam só no celular)
 * e histórico da sessão (texto lido, resposta e fontes).
 */
class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val history = mutableStateListOf<String>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* O usuário pode tocar de novo se negar alguma. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                tts?.let { AudioRouter.configureTts(it) }
            }
        }
        val prefs = GlassesService.prefs(this)
        setContent {
            var geminiKey by remember {
                mutableStateOf(prefs.getString("gemini_key", "").orEmpty())
            }
            var braveKey by remember {
                mutableStateOf(prefs.getString("brave_key", "").orEmpty())
            }
            var glassesIp by remember {
                mutableStateOf(prefs.getString("glasses_ip", "").orEmpty())
            }
            var serviceOn by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("mhglass pronto.") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("mhglass", fontSize = 32.sp)
                Text("Diga tirar, ou aperte o botão do óculos.", fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "Tirando foto."
                        runTirar(
                            onStatus = { status = it },
                            onDone = { busy = false },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                ) { Text("TIRAR", fontSize = 28.sp) }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (serviceOn) {
                            GlassesService.stop(this@MainActivity)
                            serviceOn = false
                            status = "mhglass desligado."
                        } else {
                            GlassesService.start(this@MainActivity)
                            serviceOn = true
                            status = "mhglass ligado. Diga tirar."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text(
                        if (serviceOn) "Desligar mhglass" else "Ligar mhglass",
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val bt = AudioRouter.bluetoothOutputName(this@MainActivity)
                        val msg = if (bt != null) {
                            "Teste de som no óculos, saída $bt."
                        } else {
                            "Teste de som. Nenhum fone Bluetooth detectado, " +
                                "selecione o óculos como saída de áudio."
                        }
                        speak(msg)
                        status = msg
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Testar som no óculos") }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val (photos, videos, audios) =
                                MhGlassApp.transport.mediaCount()
                            status = "Fotos: $photos, vídeos: $videos, áudios: $audios"
                            speak(status)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Contar mídia no óculos") }

                Spacer(Modifier.height(24.dp))
                Text("Configurações (ficam só neste celular)", fontSize = 18.sp)
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        prefs.edit().putString("gemini_key", it).apply()
                    },
                    label = { Text("Chave Gemini") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = braveKey,
                    onValueChange = {
                        braveKey = it
                        prefs.edit().putString("brave_key", it).apply()
                    },
                    label = { Text("Chave Brave (opcional, para cruzar na internet)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = glassesIp,
                    onValueChange = {
                        glassesIp = it
                        prefs.edit().putString("glasses_ip", it).apply()
                    },
                    label = { Text("IP do óculos (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))
                Text(status, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text("Histórico da sessão", fontSize = 18.sp)
                history.forEach { item ->
                    Spacer(Modifier.height(8.dp))
                    Text(item, fontSize = 16.sp)
                }
            }
        }
    }

    private fun speak(text: String) {
        AudioRouter.speakChecked(this, tts, text)
    }

    private fun runTirar(onStatus: (String) -> Unit, onDone: () -> Unit) {
        scope.launch {
            val prefs = GlassesService.prefs(this@MainActivity)
            val result = MhGlassPipeline.run(
                context = this@MainActivity,
                transport = MhGlassApp.transport,
                takeNewPhoto = true,
                question = "",
                keys = MhGlassPipeline.Keys(
                    gemini = prefs.getString("gemini_key", "").orEmpty(),
                    brave = prefs.getString("brave_key", "").orEmpty(),
                    glassesIp = prefs.getString("glasses_ip", "").orEmpty(),
                ),
                announce = { text ->
                    onStatus(text)
                    speak(text)
                },
            )
            if (result.failure != null) {
                onStatus(result.failure)
                speak(result.failure)
            } else if (!result.answer.isNullOrBlank()) {
                if (result.fromCache) speak("Resposta já conhecida.")
                speak(result.answer)
                val entry = buildString {
                    append("Texto lido: ")
                    append(result.ocrText.ifBlank { "(nenhum texto)" })
                    append("\nResposta: ").append(result.answer)
                    if (result.sources.isNotEmpty()) {
                        append("\nFontes: ")
                        append(result.sources.joinToString("; ") { it.url })
                    }
                }
                history.add(0, entry)
                onStatus("Resposta falada no óculos.")
            } else {
                onStatus("Não consegui responder agora.")
                speak("Não consegui responder agora.")
            }
            onDone()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
