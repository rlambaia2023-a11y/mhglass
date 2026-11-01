# mhglass

Sistema personalizado para o óculos Harlafou (HeyCyan), em pt-BR, feito para
ser usado sem olhar para a tela.

Nome: **mhglass**. Um único comando, um único fluxo.

## O comando: Tirar

Diga **"Tirar"**, aperte o botão do óculos ou toque no botão grande
**TIRAR** na tela. Os três jeitos chamam o mesmo fluxo, nesta ordem,
com um aviso falado em cada passo:

1. **Tirar a foto** — o óculos tira a foto.
2. **Ler** — a foto vem para o celular pela Wi-Fi do óculos e o OCR
   (ML Kit, no próprio celular, sem nuvem) lê o que está escrito nela.
3. **Cruzar na internet** — o texto lido vai para uma busca na internet
   (Brave Search, se a chave estiver configurada no celular) e o Gemini
   cruza o texto do OCR, a foto e os resultados da busca.
4. **Falar no óculos** — a resposta sai falada no fone do óculos
   (saída de áudio Bluetooth, modo voz), não no alto-falante do celular.

Se um passo falhar, o app fala isso e para, sem inventar resposta.
Se a busca na internet falhar, ele avisa "sem cruzamento na internet"
e responde só com a foto e o texto lido, deixando isso claro na fala.

Detalhe do comando, gatilhos e mensagens faladas: ver
[COMANDO-TIRAR.md](COMANDO-TIRAR.md).

## Regras fixas

- Só Gemini, nunca OpenAI, em qualquer adaptação. Modelo Lite primeiro:
  `gemini-2.5-flash-lite`, o mesmo já usado no projeto anterior do óculos.
  Em erro 429 (cota), o app fala que a cota acabou e para. Não tenta
  outro modelo para contornar a cota.
- Resposta curta e falada, em pt-BR natural, sem cara de texto de IA.
- A foto fica no celular. Só vão para o Gemini a foto e a pergunta que
  o comando Tirar gerou, e para a busca só vai o texto a pesquisar.
- Cache: o mesmo texto lido (OCR igual) em 24 horas reaproveita a
  resposta, sem gastar outra chamada do Gemini.
- Chaves (Gemini e Brave) ficam só no celular, em Configurações.
  Nunca vão para o chat nem para o repositório.

## Base técnica, com honestidade sobre licença

- Referência de protocolo HeyCyan: repositório público
  [FerSaiyan/Alternative-HeyCyan-App-and-SDK](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK)
  (projeto CyanBridge). O código original dele é Apache-2.0, mas o
  repositório é de licença mista: SDKs de fabricante, binários e
  material de terceiros mantêm as licenças próprias deles. Ver
  `LICENSING.md` e `THIRD_PARTY_NOTICES.md` lá.
- Por isso o mhglass **não embute nenhum AAR de fabricante neste
  repositório**. A camada do óculos é uma interface (`GlassesTransport`).
  A implementação HeyCyan (`HeyCyanGlassesTransport`) usa as chamadas
  do SDK já validadas no projeto anterior e só compila no PC se o AAR
  local estiver em `app/app/libs/` (pasta ignorada pelo git, uso
  pessoal). Sem o AAR, exclua esse arquivo do build antes de compilar
  e o app usa o transporte de teste, que não tira foto de verdade —
  serve só para testar a voz e o fluxo.
- Código próprio do mhglass (fluxo Tirar, OCR, busca, Gemini, voz):
  escrito para este projeto, sem copiar o app HeyCyan original.
- Este projeto **não foi publicado**. Publicar é decisão do usuário,
  depois de checar a licença do componente de fabricante que for usar.

## O que já está pronto aqui (código, ainda sem APK)

- `app/` — projeto Android (Kotlin + Compose), pacote `com.mhglass`.
- Fluxo Tirar completo em `MhGlassPipeline.kt`, com anúncios falados.
- Três gatilhos para o mesmo fluxo: botão do óculos, voz "tirar" com o
  serviço ligado e botão grande na tela.
- OCR no celular, busca Brave opcional, Gemini Lite com cache e parada
  em 429, TTS com roteamento para o fone Bluetooth do óculos.
- Tela acessível: um botão TIRAR grande, um botão "Ligar mhglass"
  (serviço em primeiro plano, para o celular no bolso), teste de som
  no óculos e histórico da sessão.

## O que NÃO está pronto

- **APK ainda não compilado nesta VM**: aqui não há Java nem Android
  SDK, como já acontecia no projeto anterior. O próximo passo é abrir
  `app/` no Android Studio do PC, sincronizar o Gradle e compilar.
- Teste com o óculos real: conectar, tirar foto, importar, ler,
  cruzar e ouvir no óculos, nesta ordem, com o app HeyCyan original
  fechado (o óculos aceita uma sessão por vez).
- Calibração do botão do óculos: no primeiro teste real, veja o log.
  Se o código que chegar não for 0x02, ajuste em
  `GlassesService.kt`. O botão simples de foto pode não enviar aviso
  nenhum — por isso existe a reserva por contagem de fotos (até 8 s).

## Como compilar (PC, Android Studio)

1. Abra a pasta `app/` no Android Studio.
2. Deixe o Gradle sincronizar. Se ele pedir outra versão do Kotlin
   ou do AGP, aceite a sugestão do próprio Android Studio.
3. Para foto de verdade: coloque o AAR do SDK do óculos em
   `app/app/libs/` (o mesmo já usado no projeto anterior), descomente
   a linha do AAR em `app/app/build.gradle.kts` e troque o transporte
   em `MhGlassApp.kt` para `HeyCyanGlassesTransport()`. Sem o AAR,
   exclua `HeyCyanGlassesTransport.kt` do build e o app usa o
   transporte de teste.
4. Conecte o celular Android por USB, com depuração USB ligada.
5. Rode o app (Run) e instale no celular.
6. No celular: Bluetooth e localização ligados, óculos ligado, app
   HeyCyan original fechado, chave Gemini em Configurações (fica só
   no celular). Brave é opcional, para o cruzamento na internet.

## Teste ao vivo, em ordem

1. Tocar em "Testar som no óculos". Se ouvir no óculos, a rota está
   certa. Se ouvir no celular, selecione o óculos como saída de áudio
   do sistema e teste de novo.
2. Buscar e Conectar.
3. Tocar em TIRAR com uma foto com texto (ex.: uma questão, um rótulo).
4. Ouvir, sem olhar a tela: "Tirando foto", "Lendo", "Buscando na
   internet", resposta.
5. Dizer "Tirar" com o serviço ligado e repetir o teste só com o ouvido.
6. Apertar o botão do óculos e repetir. Anotar o código que chegar no
   log, se não for 0x02.

## Estrutura

- `app/` — projeto Android (abra esta pasta no Android Studio)
- `app/app/src/main/java/com/mhglass/` — código do mhglass
  - `MainActivity.kt` — tela acessível + configurações locais
  - `GlassesService.kt` — serviço em primeiro plano, botão do óculos
    e voz "tirar"
  - `TirarCommand.kt` — o comando Tirar e seus três gatilhos
  - `MhGlassPipeline.kt` — foto, importação, OCR, busca, Gemini, voz
  - `GlassesTransport.kt` — interface do óculos + transporte de teste
  - `HeyCyanGlassesTransport.kt` — implementação HeyCyan (precisa do
    AAR local; não vai para o repositório)
  - `AlbumDownloader.kt` — importação por Wi-Fi do óculos
  - `OcrReader.kt` — OCR no celular (ML Kit)
  - `WebSearchClient.kt` — busca Brave (opcional)
  - `GeminiClient.kt` — só Gemini Lite, cache, parada em 429
  - `AnswerCache.kt` — reaproveita resposta com o mesmo OCR em 24 h
  - `AudioRouter.kt` — voz no fone do óculos, não no celular
- `docs/` — notas de teste e de licença
- `mhglass-upstream/` NÃO está aqui: a cópia de referência do
  CyanBridge ficou em `~/workspace/mhglass-upstream/`, fora deste
  projeto, só para consulta.
