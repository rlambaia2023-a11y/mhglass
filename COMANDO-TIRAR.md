# Comando Tirar — especificação do mhglass

## O comando

Uma palavra: **"Tirar"**.

Não é preciso frase completa, nem olhar a tela, nem tocar em menu.
"Tirar" quer dizer: tira a foto agora, lê o que está nela, cruza na
internet e me fala a resposta no óculos.

## Os três gatilhos (mesmo fluxo, mesma voz)

| Gatilho | Quando usar | Como ligar |
| --- | --- | --- |
| Botão do óculos | Uso no dia a dia, celular no bolso | Tocar em "Ligar mhglass". O serviço em primeiro plano ouve o SDK. Código esperado: 0x02 (calibrável, ver abaixo). |
| Voz "tirar" | Mãos ocupadas, sem apertar nada | Com o serviço ligado, o celular escuta "tirar" em pt-BR. Ao ouvir, confirma com "Tirando foto" e começa. |
| Botão TIRAR na tela | Teste, calibração e quem enxerga a tela | Botão grande na tela principal. |

O serviço só escuta voz enquanto estiver ligado, e o usuário desliga
quando quiser tocando em "Desligar mhglass". Nada fica escutando sem
o serviço ligado.

## Passo a passo falado (sempre nesta ordem)

1. "Tirando foto."
2. "Foto tirada. Importando do óculos."
   - Falha aqui: "Não consegui tirar a foto. Verifique se o óculos
     está conectado." ou "Foto tirada, mas não consegui importar.
     Confira o IP do óculos." E para. Sem inventar resposta.
3. "Foto importada. Lendo o que está na foto."
   - Sem texto: "Não encontrei texto na foto. Descrevendo a imagem."
4. "Texto lido. Buscando na internet para cruzar."
   - Sem chave Brave ou sem internet: "Sem cruzamento na internet.
     Resposta só pela foto e pelo texto lido." E segue, avisando.
5. Resposta falada no fone do óculos, curta, em pt-BR.
   - Se houve cruzamento, a fala termina com a fonte de forma curta,
     ex.: "Segundo o site X". O histórico na tela guarda o link
     completo e o texto lido.
   - Se o OCR e a imagem discordarem, a fala diz isso, sem esconder.
6. Erro de cota: "Cota do Gemini acabou. Não tentei outro modelo.
   Tente de novo mais tarde." E para.

## O que o Tirar NÃO faz

- Não tira foto sozinho sem um gatilho. Tirar só acontece por botão
  do óculos, voz "tirar" com o serviço ligado ou botão na tela.
- Não publica nada, não envia a foto para rede social, não grava
  vídeo nem áudio por conta própria.
- Não inventa fonte: sem resultado de busca, não cita site nenhum.
- Não usa OpenAI em nenhum passo. Só Gemini Lite, com cache e
  parada em 429.

## Calibração do botão do óculos (primeiro teste real)

1. Ligue o mhglass e aperte o botão de foto do óculos.
2. Veja o log do app: todo código que chegar é registrado.
3. Se o código não for 0x02, anote-o e inclua-o na condição em
   `GlassesService.kt` (lista `TRIGGER_CODES`).
4. Se o botão simples de foto não enviar aviso nenhum, use a reserva:
   a vigia por contagem percebe a foto nova em até 8 segundos e
   começa o mesmo fluxo, sem tirar outra foto.

## Som: tem que sair no fone do óculos

- O TTS usa voz de comunicação (`USAGE_VOICE_COMMUNICATION`), não
  mídia comum, porque o óculos Bluetooth funciona como dispositivo
  de comunicação.
- No Android 12+, o app tenta fixar o óculos como dispositivo de
  comunicação (`setCommunicationDevice`).
- Antes de falar, verifica se há saída Bluetooth. Se não houver,
  fala o aviso no que estiver disponível, em vez de fingir que a
  resposta saiu no óculos.
- Limite honesto do Android: o app não consegue forçar 100% a saída
  se outro dispositivo estiver selecionado no sistema. Por isso
  existe "Testar som no óculos" antes do primeiro uso real.

Nota de vocabulário: a resposta sai pelo alto-falante/fone do óculos
(para o usuário ouvir). O microfone do óculos é a entrada de som;
ele não é o destino da resposta falada.
