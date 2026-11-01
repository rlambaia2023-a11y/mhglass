package com.mhglass

/**
 * Camada do óculos do mhglass, separada do resto do app de propósito.
 *
 * O pipeline só conhece esta interface. Quem tira foto de verdade é
 * a implementação HeyCyan (HeyCyanGlassesTransport), que precisa do
 * AAR local do SDK e por isso não vai para o repositório. Sem o AAR,
 * o app usa o TestTransport abaixo: ele não tira foto, só deixa
 * testar a voz, o OCR e o fluxo com uma foto já no celular.
 *
 * Separar assim evita embutir binário de fabricante no projeto e
 * deixa claro, no build, qual transporte está ativo.
 */
interface GlassesTransport {
    val isConnected: Boolean
    fun connectSavedDevice()
    fun disconnect()
    suspend fun takePhoto(): Boolean
    suspend fun mediaCount(): Triple<Int, Int, Int>
    fun addButtonListener(onCode: (Int) -> Unit)
}

/** Transporte de teste: sem óculos, sem foto nova. Só para o fluxo. */
class TestTransport : GlassesTransport {
    override val isConnected: Boolean = false
    override fun connectSavedDevice() {}
    override fun disconnect() {}
    override suspend fun takePhoto(): Boolean = false
    override suspend fun mediaCount(): Triple<Int, Int, Int> = Triple(-1, -1, -1)
    override fun addButtonListener(onCode: (Int) -> Unit) {}
}
