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

## Verificar se iniciou

Em outro terminal, consulte a API local. Substitua a senha pela definida em `application.yml`:

```bash
curl -H "Authorization: SUA_SENHA_LOCAL" http://127.0.0.1:2333/version
```

Uma resposta com a versão indica que a API está acessível. Isso ainda não comprova que as fontes musicais estejam funcionando.

## Fontes musicais

A disponibilidade das fontes depende de plugins, versões e mudanças dos provedores. A fonte nativa do YouTube está desativada na configuração inicial; o suporte ao YouTube será configurado e testado separadamente. Não presumimos que YouTube ou SoundCloud funcionarão apenas porque o servidor iniciou.

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
