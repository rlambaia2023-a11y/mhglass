package com.mhglass

/**
 * Ponto único de escolha do transporte do óculos.
 *
 * Padrão: TestTransport (sem foto de verdade, só para testar voz e
 * fluxo). Para foto de verdade no PC, com o AAR em app/libs/:
 * troque a linha abaixo por HeyCyanGlassesTransport() e recompile.
 * Nada mais no app precisa mudar, porque o pipeline só conhece a
 * interface GlassesTransport.
 */
object MhGlassApp {
    var transport: GlassesTransport = TestTransport()
}
