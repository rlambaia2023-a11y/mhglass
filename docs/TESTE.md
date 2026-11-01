# Teste do mhglass com o óculos real

Ordem obrigatória, sem pular passo. App HeyCyan original fechado
o tempo todo: o óculos aceita uma sessão por vez.

1. Instalar no celular, abrir o mhglass, colocar a chave Gemini em
   Configurações. Brave é opcional.
2. Tocar em "Testar som no óculos".
   - Ouviu no óculos: rota certa, pode seguir.
   - Ouviu no celular: selecione o óculos como saída de áudio do
     sistema e teste de novo. Não adianta seguir sem isso.
3. Buscar e Conectar (transporte HeyCyan, com o AAR local).
4. Tocar em TIRAR com uma foto com texto.
   - Ouvir, sem olhar a tela: "Tirando foto", "Lendo", "Buscando na
     internet", resposta.
   - Anotar: onde a voz trava ou some, e em qual passo.
5. Tocar em "Ligar mhglass" e dizer "tirar", só com o ouvido.
6. Apertar o botão do óculos e repetir.
   - Anotar o código que chegar no log. Se não for 0x02, incluir
     o código em `TRIGGER_CODES` em `GlassesService.kt`.
   - Se o botão simples não enviar aviso, a vigia por contagem vai
     perceber a foto nova em até 8 s. Anotar esse atraso.
7. Repetir o mesmo texto duas vezes: a segunda resposta deve vir
   do cache ("Resposta já conhecida"), sem nova chamada do Gemini.
8. Sem chave Brave: o app deve falar "Sem cruzamento na internet"
   e responder só pela foto, sem citar site nenhum.

Critério de pronto: passos 2 a 6 funcionam com o óculos real, só
com o ouvido, com a resposta saindo no fone do óculos.
