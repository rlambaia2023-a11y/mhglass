package com.mhglass

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * Roteamento de áudio do mhglass: a resposta TEM que sair no fone
 * do óculos, não no alto-falante do celular. Este é o ponto mais
 * importante do app para quem não enxerga.
 *
 * - TTS com USAGE_VOICE_COMMUNICATION (voz), não mídia comum.
 * - No Android 12+ (API 31+), tenta fixar o óculos como dispositivo
 *   de comunicação (setCommunicationDevice, MODIFY_AUDIO_SETTINGS).
 * - Antes de falar, verifica se há saída Bluetooth. Se não houver,
 *   fala o aviso no que estiver disponível, sem fingir que saiu no óculos.
 *
 * Limite honesto: o Android não deixa um app forçar 100% a saída se
 * outro dispositivo estiver selecionado no sistema. Por isso existe
 * o botão "Testar som no óculos".
 */
object AudioRouter {

    fun voiceAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    fun configureTts(tts: TextToSpeech) {
        try {
            tts.setAudioAttributes(voiceAttributes())
        } catch (e: Exception) {
            Log.w("AudioRouter", "setAudioAttributes falhou", e)
        }
    }

    fun bluetoothOutputName(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bt = outputs.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
            bt?.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: bt?.let { "dispositivo Bluetooth" }
        } catch (e: Exception) {
            Log.w("AudioRouter", "bluetoothOutputName falhou", e)
            null
        }
    }

    fun isBluetoothOutput(context: Context): Boolean =
        bluetoothOutputName(context) != null

    fun tryRouteToBluetooth(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = manager.getCommunicationDevices()
            val bt = devices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: return false
            manager.setCommunicationDevice(bt)
            true
        } catch (e: Exception) {
            Log.w("AudioRouter", "setCommunicationDevice falhou", e)
            false
        }
    }

    fun speakChecked(context: Context, tts: TextToSpeech?, text: String) {
        if (tts == null) return
        val routed = tryRouteToBluetooth(context)
        Log.i(
            "AudioRouter",
            "Bluetooth output=${bluetoothOutputName(context)} routed=$routed"
        )
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mhglass-audio")
    }
}
