# King-Lavalink

Serviço Lavalink independente para desenvolvimento local e futura integração com o bot King.

## Objetivos

- Executar o Lavalink localmente antes de contratar hospedagem.
- Manter o serviço de áudio separado do bot Node.js.
- Baixar o binário oficial automaticamente com um script, sem versionar arquivos JAR.
- Validar conectividade, autenticação e reprodução antes de migrar para produção.

## Requisitos

- Java 17 ou superior.
- Acesso à internet para baixar o binário oficial do Lavalink.
- Windows PowerShell ou shell Bash.

## Instalação local

Os scripts baixam a versão do Lavalink definida neles (atualmente **4.2.2**) da página oficial de releases, validam o Java, criam uma configuração local com senha aleatória e mantêm os arquivos privados fora do Git.

### Windows (PowerShell)

Na raiz do repositório:

```powershell
.\scripts\setup.ps1
.\scripts\start.ps1
```

### Linux/macOS

Na raiz do repositório:

```bash
chmod +x scripts/setup.sh scripts/start.sh
./scripts/setup.sh
./scripts/start.sh
```

O primeiro comando baixa `Lavalink.jar` e cria `application.yml`. O segundo inicia o serviço. O processo fica ativo no terminal; use `Ctrl+C` para encerrá-lo.

## YouTube

A configuração de exemplo instala o plugin oficial `youtube-source` **1.18.2**, mantém a fonte nativa do YouTube desativada, ativa `MUSIC`, `TV`, `ANDROID_VR`, `WEB` e `WEBEMBEDDED`, habilita OAuth para o cliente `TV` e configura o resolvedor remoto `https://cipher.kikkia.dev/`.

### Erro "This video requires login" em todas as músicas

Esse erro pode indicar que o YouTube está bloqueando solicitações sem autenticação, por exemplo devido ao IP de saída do servidor. O endpoint `remoteCipher` decodifica assinaturas, mas não autentica a sessão no YouTube. O template habilita o OAuth oficial do `youtube-source` e o cliente `TV`, que requer autenticação.

1. Reinicie o Lavalink e acompanhe o terminal/logs. Na primeira inicialização sem token, o plugin deverá iniciar o fluxo oficial de autorização e mostrar instruções/código.
2. Conclua o fluxo com uma **conta Google/YouTube dedicada de teste (burner)**, não sua conta principal. O projeto `youtube-source` alerta que OAuth pode causar limitação de requisições ou até encerramento da conta; não é uma garantia de funcionamento.
3. Após a autorização, copie o refresh token mostrado no log para o arquivo local `application.yml` (não o commite nem o compartilhe). Opcionalmente, injete-o por variável de ambiente conforme o ambiente de hospedagem; não coloque tokens reais no repositório.
4. Reinicie o Lavalink e confirme nos logs que o token foi obtido/atualizado. Teste uma faixa pública.

Se o serviço roda em hospedagem sem terminal interativo, veja se os logs exibem o código de autorização e conclua o fluxo pelo método indicado. Se o plugin continuar falhando, envie os logs completos desde a inicialização do YouTube até a primeira tentativa de reprodução, removendo tokens e códigos privados. OAuth não corrige bugs de assinatura nem bloqueios de rede por si só.

A instância pública é mantida pelo projeto [yt-cipher](https://github.com/kikkia/yt-cipher), mas não tem garantia de disponibilidade contínua. Para produção, hospede sua própria instância e substitua a URL e, se configurada, a senha do serviço no `application.yml`. Não use o endpoint comunitário anterior `cipher.lavalink-harmonix.me`, que já apresentou erro de implantação pausada.

**Importante:** `setup.ps1` e `setup.sh` preservam um `application.yml` existente. Se você já executou o setup antes desta atualização, o arquivo local não será sobrescrito. Compare-o com `application.example.yml`, preserve a senha local e atualize a versão do plugin e a URL do cipher. Não crie duas chaves `lavalink:` ou `plugins:`; mescle as seções no mesmo bloco.
## Verificar se iniciou

Em outro terminal, consulte a API local. Substitua a senha pela definida em `application.yml`:

```bash
curl -H "Authorization: SUA_SENHA_LOCAL" http://127.0.0.1:2333/version
```

Uma resposta com a versão indica que a API está acessível. Isso ainda não comprova que as fontes musicais estejam funcionando.

Depois de reiniciar o Lavalink, confira os logs para confirmar que o plugin YouTube foi carregado e que a mensagem de inicialização menciona o resolvedor remoto. Teste uma busca e uma faixa pública do YouTube.

## Integração local com o King

O repositório do King contém uma lista de nós públicos em `botconfig/lavalink.json`. Para um teste local, a cópia local do bot precisa selecionar o Lavalink local em vez desses nós. Não use `127.0.0.1` como host se o bot estiver hospedado em outro computador/contêiner: esse endereço sempre aponta para o próprio ambiente do bot.

A configuração do bot principal não é alterada por este repositório. Valide primeiro a reprodução local antes de mudar a configuração de produção.

## Arquivos locais ignorados pelo Git

- `Lavalink.jar`: binário baixado da release oficial.
- `application.yml`: configuração com a senha local.
- Logs e arquivos de runtime.

## Segurança

- A configuração inicial escuta apenas em `127.0.0.1`.
- Não publique a senha nem o arquivo `application.yml`.
- Não exponha a porta 2333 à internet sem planejar autenticação, firewall e transporte seguro.
- A versão do Lavalink é fixada nos scripts para tornar o setup reproduzível; atualize-a conscientemente após revisar a release.

## Estrutura

```text
King-Lavalink/
├── .env.example
├── .gitignore
├── README.md
├── application.example.yml
├── docs/
│   └── local-testing.md
└── scripts/
    ├── setup.ps1
    ├── start.ps1
    ├── setup.sh
    └── start.sh
```
