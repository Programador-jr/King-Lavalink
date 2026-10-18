# Correção experimental do cliente TVHTML5 (youtube-source 1.18.2)

## Motivo

A versão 1.18.2 define o User-Agent do cliente `TVHTML5` como:

```java
.withUserAgent("Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version")
```

Uma investigação upstream reproduziu a mensagem `The page needs to be reloaded` com esse User-Agent e relatou sucesso na etapa de formatos usando a identidade de User-Agent de PlayStation 4. Consulte [youtube-source issue #226](https://github.com/lavalink-devs/youtube-source/issues/226).

A correção é um workaround experimental e pode deixar de funcionar se o YouTube mudar suas verificações. Não é uma correção oficial do mantenedor.

## Compilar no Windows

Na raiz do repositório, execute em PowerShell:

```powershell
.\scripts\build-youtube-plugin-patched.ps1
```

Requisitos: Git e JDK 17 ou superior. O script clona a tag oficial `1.18.2` para uma pasta temporária, verifica se o patch se aplica sem conflitos e executa `gradlew.bat :plugin:build` (inclui os testes da tarefa de build). Não altera o Lavalink ativo nem grava credenciais. O JAR final fica em `dist/youtube-plugin-1.18.2-king-tv-ua.jar`.

## Instalação manual e reversível

1. Pare o Lavalink.
2. Faça uma cópia de segurança do JAR oficial atual e do `application.yml`.
3. No `application.yml`, remova somente a entrada Maven `dev.lavalink.youtube:youtube-plugin:1.18.2` de `lavalink.plugins` para evitar carregar a versão publicada e a personalizada ao mesmo tempo. Se essa era a única entrada, a seção pode ficar assim:
   ```yaml
   lavalink:
     plugins: []
   ```
   Se houver outros plugins nessa lista, mantenha-os. Preserve o restante do bloco `lavalink:`.
4. Copie o JAR personalizado para a pasta `plugins` ao lado do `Lavalink.jar`. Se não existir, crie-a.
5. Inicie o Lavalink e confirme no log que apenas uma instância de `youtube-plugin` foi carregada.
6. Teste várias faixas públicas, incluindo vídeos que falhavam anteriormente. Confirme tanto `loadtracks` quanto o início real do áudio.

Para reverter, pare o Lavalink, remova o JAR personalizado, restaure a configuração original e volte a usar a versão oficial do plugin.

## Limites e segurança

- A alteração só muda o User-Agent do cliente `TVHTML5`; não corrige necessariamente falhas `This video requires login` de `WEB` e `ANDROID_VR`.
- Não adiciona cookies, tokens ou dados pessoais ao repositório.
- Um build bem-sucedido comprova compilação e testes automatizados, não reprodução real contra o YouTube. A validação final exige iniciar o Lavalink local e testar faixas.
