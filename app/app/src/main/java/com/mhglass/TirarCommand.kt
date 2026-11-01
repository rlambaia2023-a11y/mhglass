package com.mhglass

/**
 * mhglass — o comando Tirar.
 *
 * Uma palavra dispara o mesmo fluxo, venha de onde vier:
 * - TRIGGER_GLASSES_BUTTON: botão do óculos (código SDK 0x02, calibrável)
 * - TRIGGER_VOICE: a palavra "tirar", ouvida com o serviço ligado
 * - TRIGGER_SCREEN: o botão grande TIRAR na tela
 *
 * O comando em si não decide nada sobre a resposta. Ele só diz
 * "começa agora" e entrega o trabalho ao MhGlassPipeline, que fala
 * cada passo no óculos. Nada aqui toca em rede ou em IA.
 */
object TirarCommand {

    const val WORD = "tirar"

    enum class Trigger { GLASSES_BUTTON, VOICE, SCREEN, MEDIA_WATCH }

    /** A palavra "tirar" aparece na fala reconhecida? Aceita variação simples. */
    fun isTirarWord(recognized: String): Boolean {
        val clean = recognized.lowercase().trim()
            .replace(Regex("[^a-zà-ú ]"), " ")
            .replace(Regex("\\s+"), " ")
        return clean.split(" ").any { it == WORD }
    }

    fun triggerName(trigger: Trigger): String = when (trigger) {
        Trigger.GLASSES_BUTTON -> "botão do óculos"
        Trigger.VOICE -> "voz"
        Trigger.SCREEN -> "botão da tela"
        Trigger.MEDIA_WATCH -> "foto nova no óculos"
    }
}
