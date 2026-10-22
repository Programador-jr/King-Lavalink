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

## Painel web de logs e conexões

Com o Lavalink iniciado, abra outro terminal e execute:

```powershell
.\scripts\panel.ps1
```

No Linux/macOS, execute `./scripts/panel.sh`. Abra [http://127.0.0.1:8080](http://127.0.0.1:8080) e navegue pelas páginas Visão geral, Conexões e Logs. O painel mostra players conectados, reprodução, CPU, memória, logs recentes com rolagem e histórico de conexões. Ele usa apenas Python 3 e a biblioteca padrão e registra um snapshot a cada atualização enquanto estiver aberto em `logs/connections.jsonl`.

O painel lê a senha de `application.yml` ou das variáveis `LAVALINK_SERVER_PASSWORD` / `LAVALINK_PASSWORD`. O serviço web fica vinculado a `127.0.0.1`; mantenha-o local. A configuração de exemplo grava os logs do Lavalink em `logs/lavalink.log`. Se seu `application.yml` foi criado antes dessa opção, adicione o bloco `logging.file.name: logs/lavalink.log` (sob `logging`, antes de `level`) e reinicie o Lavalink. Para alterar a porta do painel, defina `PANEL_PORT` antes de iniciá-lo.

## YouTube

A configuração de exemplo instala o plugin oficial `youtube-source` **1.15.0**, mantém a fonte nativa do YouTube desativada e configura um resolvedor remoto de cipher para lidar com mudanças no script do player. A versão 1.15.0 adiciona suporte ao endpoint `/resolve_url` do resolvedor remoto.

O endereço público configurado é comunitário e fornecido sem garantia de disponibilidade. Ele serve para diagnóstico local; para uso contínuo, implante uma instância privada do [yt-cipher](https://github.com/Sagexdd/yt-cipher) e substitua `plugins.youtube.remoteCipher.url` pela URL da sua instância.

**Importante:** `setup.ps1` preserva um `application.yml` existente. Se você já executou o setup antes desta atualização, o arquivo local não será sobrescrito. Copie as seções `lavalink.plugins`, `plugins.youtube` e o nível de log `dev.lavalink.youtube` de `application.example.yml` para o seu `application.yml`, mantendo a senha atual e as configurações locais. Não crie duas chaves `lavalink:` ou `plugins:`; mescle as seções no mesmo bloco.

## Deploy Java na ShardCloud

O Lavalink é um serviço Java executado pelo JAR oficial. A configuração `.shardcloud` seleciona o runtime Java, inicia `Lavalink.jar`, reserva 1 GB de memória e define `VERSION=recommended`. A configuração de produção em `application.shardcloud.yml` escuta na porta 80 e baixa o plugin oficial `youtube-source` na inicialização.

Para gerar o ZIP pronto para upload (o JAR não é versionado pelo Git):

```powershell
.\scripts\package-shardcloud.ps1
```

No painel ShardCloud, crie uma aplicação com runtime Java e envie `dist/king-lavalink-shardcloud.zip`. Defina a variável secreta `LAVALINK_SERVER_PASSWORD` antes do deploy. O endereço público fornecido pela plataforma usa a porta 443 com TLS/WebSocket seguro (`wss`); informe esse host, a senha e conexão segura no bot.

O pacote contém apenas o JAR oficial, a configuração de produção e `.shardcloud`; não inclui `.env`, `application.yml` local nem credenciais. Para OAuth do YouTube, configure o token como segredo separado e referencie-o apenas se concluir o fluxo.
## Verificar se iniciou

Em outro terminal, consulte a API local. Substitua a senha pela definida em `application.yml`:

```bash
curl -H "Authorization: SUA_SENHA_LOCAL" http://127.0.0.1:2333/version
```

Uma resposta com a versão indica que a API está acessível. Isso ainda não comprova que as fontes musicais estejam funcionando.

Depois de reiniciar o Lavalink, confira os logs para confirmar que o plugin YouTube foi carregado e que a mensagem de inicialização menciona o resolvedor remoto. Teste uma busca e uma faixa pública do YouTube.

## Integração local com o King

A branch `dev` do repositório King aceita selecionar o nó local explicitamente sem modificar a lista de nós públicos:

1. Inicie o Lavalink e confirme que `GET /version` funciona com a senha local.
2. Na cópia local do King, adicione estas variáveis ao `.env` usando exatamente a senha presente em `application.yml`:

   ```env
   LAVALINK_LOCAL=true
   LAVALINK_HOST=127.0.0.1
   LAVALINK_PORT=2333
   LAVALINK_SECURE=false
   LAVALINK_PASSWORD=SENHA_GERADA_PELO_SETUP
   ```

3. Reinicie o bot. Ao definir `LAVALINK_HOST` e `LAVALINK_PASSWORD` no `.env`, o King prioriza automaticamente o nó configurado por ambiente e ignora `botconfig/lavalink.json`. `LAVALINK_LOCAL=true` também força esse modo e valida que as credenciais locais estejam completas. Se nenhuma configuração explícita de nó estiver presente, a lista antiga de `botconfig/lavalink.json` continua sendo usada.
4. Confirme nos logs que o node local conectou; em seguida teste busca/reprodução do YouTube e controles básicos.

Se o bot e o Lavalink estiverem em contêineres diferentes, `127.0.0.1` não é o endereço correto para comunicação entre eles. Use o nome DNS do serviço na rede privada e ajuste o endereço de bind do Lavalink com controles de firewall apropriados.

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
