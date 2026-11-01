# Compilar no PC e publicar o APK no GitHub Releases

Este é o caminho escolhido para distribuir o mhglass: compilar no PC
e publicar o APK no GitHub Releases. Não há deploy em Vercel nem em
Railway, porque o mhglass é um aplicativo Android, não um site nem
um backend. Também não há build automático no GitHub Actions para a
versão de verdade: ela precisa do AAR do SDK, que não está no
repositório (uso pessoal, pasta ignorada pelo git), então o build
automático falharia ou geraria só a versão de teste.

## Parte 1 — Compilar no PC (Android Studio)

1. Clone o repositório no PC (ou atualize, se já clonou):
   `git clone https://github.com/rlambaia2023-a11y/mhglass.git`
2. Abra a pasta `mhglass/app/` no Android Studio (não a pasta raiz,
   e sim a pasta que contém `settings.gradle.kts`).
3. Deixe o Gradle sincronizar. Se ele pedir outra versão do Kotlin
   ou do AGP, aceite a sugestão do próprio Android Studio.
4. Para foto de verdade no óculos:
   - Coloque o AAR do SDK do óculos em `app/app/libs/` (o mesmo já
     usado no projeto anterior). Sem ele, o build falha no arquivo
     `HeyCyanGlassesTransport.kt`.
   - Descomente a linha do AAR em `app/app/build.gradle.kts`.
   - Troque o transporte em `MhGlassApp.kt` para
     `HeyCyanGlassesTransport()`.
   - Sem o AAR, exclua `HeyCyanGlassesTransport.kt` do build: o app
     compila com o transporte de teste, que não tira foto de verdade.
5. Conecte o celular Android por USB, com depuração USB ligada.
6. Rode o app (Run) e instale no celular para o teste ao vivo
   (ver `docs/TESTE.md`).
7. Para gerar o APK de distribuição, sem instalar pelo Android Studio:
   - Versão de teste (debug, mais simples, sem assinar à mão):
     no Android Studio, Build > Build App Bundle(s) / APK(s) >
     Build APK(s), ou no terminal, dentro de `mhglass/app/`:
     `./gradlew assembleDebug`
   - O APK fica em:
     `app/app/build/outputs/apk/debug/app-debug.apk`
   - Versão final (release): precisa de um keystore criado no PC
     (Build > Generate Signed Bundle / APK). O keystore e as senhas
     ficam só no PC, nunca vão para o repositório nem para o chat.
     O APK fica em:
     `app/app/build/outputs/apk/release/app-release.apk`

Antes de publicar, teste o APK no celular com o óculos real, na
ordem de `docs/TESTE.md`: som no óculos, conectar, TIRAR, voz
"tirar", botão do óculos. Não publique um APK que não passou nesse
teste.

## Parte 2 — Publicar no GitHub Releases

1. No GitHub, abra https://github.com/rlambaia2023-a11y/mhglass
2. Clique em Releases > Draft a new release (ou Create a new release).
3. Choose a tag: `v0.1.0` (crie a tag na hora, na branch main).
4. Release title: `mhglass v0.1.0`
5. Descrição: copie o texto de `docs/RELEASE-v0.1.0.md` e ajuste o
   que mudou de verdade nessa versão.
6. Anexe o APK gerado na Parte 1 (debug para testadores, release
   para distribuição final). Não anexe o AAR, o keystore nem
   nenhuma chave.
7. Marque como pré-lançamento (pre-release) se for a primeira versão
   de teste com o óculos real. Quando o teste ao vivo passar, dá
   para tirar o pre-release.
8. Clique em Publish release.
9. O link para baixar fica em:
   https://github.com/rlambaia2023-a11y/mhglass/releases/tag/v0.1.0

## O que NÃO vai para o Releases

- AAR do SDK do fabricante.
- Keystore, senhas e chaves (Gemini, Brave).
- Fotos tiradas pelo óculos ou histórico de uso.
- Código-fonte a mais: o código já está no repositório; o Releases
  serve só para distribuir o APK pronto.

## Próximas versões

Para a v0.1.1 em diante, repita a Parte 1 e a Parte 2 com a tag nova
(`v0.1.1`, `v0.2.0`...), sempre a partir da branch main, e descreva
só o que mudou de verdade. Se o código mudar no repositório, faça
o push do código antes de gerar o APK, para o APK corresponder ao
código publicado.
