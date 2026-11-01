package com.mhglass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Serviço em primeiro plano do mhglass: é ele que impede o Android
 * de matar o app quando o celular está no bolso.
 *
 * O que ele ouve, enquanto estiver ligado:
 * 1) o botão do óculos, via SDK (códigos em TRIGGER_CODES; 0x02 é o
 *    esperado, mas todo código que chega vai para o log, para calibrar)
 * 2) a voz "tirar", via reconhecimento de fala do celular em pt-BR
 * 3) a vigia por contagem (reserva): se a contagem de fotos subir,
 *    começa o mesmo fluxo sem tirar outra foto (até 8 s de atraso)
 *
 * Cada gatilho chama o mesmo MhGlassPipeline e fala cada passo no
 * óculos. O serviço só escuta enquanto estiver ligado; desligar
 * para tudo. As chaves ficam nas preferências do app, só no celular.
 */
class GlassesService : Service() {

    companion object {
        const val ACTION_START = "com.mhglass.START"
        const val ACTION_STOP = "com.mhglass.STOP"

        /** Códigos de botão aceitos. Ajuste após o primeiro teste real. */
        val TRIGGER_CODES = setOf(0x02)

        fun prefs(context: Context) =
            context.getSharedPreferences("mhglass", Context.MODE_PRIVATE)

        fun start(context: Context) {
            val intent = Intent(context, GlassesService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GlassesService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var pipelineJob: Job? = null
    private var lastPhotoCount = -1

    private val transport: GlassesTransport
        get() = MhGlassApp.transport

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                tts?.let { AudioRouter.configureTts(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                running = false
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        1,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(1, buildNotification())
                }
                if (!running) {
                    running = true
                    speak("mhglass ligado. Diga tirar, ou aperte o botão do óculos.")
                    listenForButton()
                    listenForVoice()
                    watchMediaCount()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        stopListening()
        scope.cancel()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "mhglass", "mhglass",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "mhglass")
            .setContentTitle("mhglass ligado")
            .setContentText("Diga tirar, ou aperte o botão do óculos.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

    private fun speak(text: String) {
        AudioRouter.speakChecked(this, tts, text)
    }

    private fun keys(): MhGlassPipeline.Keys {
        val p = prefs(this)
        return MhGlassPipeline.Keys(
            gemini = p.getString("gemini_key", "").orEmpty(),
            brave = p.getString("brave_key", "").orEmpty(),
            glassesIp = p.getString("glasses_ip", "").orEmpty(),
        )
    }

    private fun runTirar(trigger: TirarCommand.Trigger, takeNewPhoto: Boolean) {
        if (pipelineJob?.isActive == true) {
            speak("Já estou tirando. Espere a resposta.")
            return
        }
        pipelineJob = scope.launch {
            val result = MhGlassPipeline.run(
                context = this@GlassesService,
                transport = transport,
                takeNewPhoto = takeNewPhoto,
                question = "",
                keys = keys(),
                announce = { speak(it) },
            )
            if (result.failure != null) {
                speak(result.failure)
            } else if (!result.answer.isNullOrBlank()) {
                if (result.fromCache) speak("Resposta já conhecida.")
                speak(result.answer)
            } else {
                speak("Não consegui responder agora.")
            }
            // Volta a ouvir "tirar" depois da resposta.
            if (running) listenForVoice()
        }
    }

    private fun listenForButton() {
        transport.addButtonListener { code ->
            if (!running) return@addButtonListener
            if (code in TRIGGER_CODES) {
                runTirar(TirarCommand.Trigger.GLASSES_BUTTON, takeNewPhoto = false)
            }
        }
    }

    private fun listenForVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        stopListening()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Erro de escuta não é erro do usuário: tenta de novo
                    // enquanto o serviço estiver ligado.
                    if (running) scope.launch { delay(500); listenForVoice() }
                }

                override fun onResults(results: Bundle?) {
                    val heard = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (TirarCommand.isTirarWord(heard)) {
                        runTirar(TirarCommand.Trigger.VOICE, takeNewPhoto = true)
                    } else if (running) {
                        listenForVoice()
                    }
                }

                override fun onPartialResults(partial: Bundle?) {
                    val heard = partial
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (TirarCommand.isTirarWord(heard)) {
                        runTirar(TirarCommand.Trigger.VOICE, takeNewPhoto = true)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            try {
                startListening(intent)
            } catch (e: Exception) {
                Log.w("GlassesService", "startListening falhou", e)
            }
        }
    }

    private fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    /** Reserva: foto nova percebida pela contagem, em até 8 s. */
    private fun watchMediaCount() {
        scope.launch {
            while (isActive && running) {
                try {
                    val (photos, _, _) = transport.mediaCount()
                    if (photos >= 0 && lastPhotoCount >= 0 && photos > lastPhotoCount) {
                        runTirar(TirarCommand.Trigger.MEDIA_WATCH, takeNewPhoto = false)
                    }
                    if (photos >= 0) lastPhotoCount = photos
                } catch (_: Exception) {
                }
                delay(8_000)
            }
        }
    }
}
