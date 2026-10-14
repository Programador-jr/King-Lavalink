# King-Lavalink

Serviço Lavalink independente para desenvolvimento local e futura integração com o bot King.

## Objetivos

- Executar o Lavalink localmente antes de contratar hospedagem.
- Manter o serviço de áudio separado do bot Node.js.
- Não versionar o binário `Lavalink.jar` nem segredos.
- Validar conectividade, autenticação e reprodução antes de migrar para produção.

## Requisitos

- Java 17 ou superior; confira os requisitos da release escolhida.
- Acesso ao terminal.
- O repositório do bot King para configurar o cliente Lavalink posteriormente.

## Preparação local

1. Baixe uma release estável do Lavalink v4: https://github.com/lavalink-devs/Lavalink/releases
2. Coloque o arquivo baixado como `Lavalink.jar` na raiz deste projeto.
3. Copie `application.example.yml` para `application.yml`.
4. Defina a variável de ambiente `LAVALINK_SERVER_PASSWORD`.
5. Inicie o serviço.

### Windows PowerShell

```powershell
$env:LAVALINK_SERVER_PASSWORD = "substitua-por-uma-senha-local"
java -jar Lavalink.jar
```

### Linux/macOS

```bash
export LAVALINK_SERVER_PASSWORD='substitua-por-uma-senha-local'
java -jar Lavalink.jar
```

O Lavalink lê `application.yml` da pasta de trabalho atual. Não publique a senha nem compartilhe o arquivo `application.yml`.

## Verificação inicial

Depois que o servidor iniciar, consulte a API local (substitua a senha):

```bash
curl -H "Authorization: substitua-por-uma-senha-local" http://127.0.0.1:2333/version
```

Uma resposta com a versão indica que a API está acessível localmente. Isso não comprova, por si só, que uma fonte musical esteja configurada.

## Fontes musicais

A disponibilidade das fontes varia conforme versões, plugins e mudanças dos provedores. Não presumimos que YouTube ou SoundCloud funcionarão apenas porque o servidor iniciou. A escolha e os testes de plugins serão uma etapa separada.

## Estrutura inicial

```text
King-Lavalink/
├── .env.example
├── .gitignore
├── README.md
├── application.example.yml
└── docs/
    └── local-testing.md
```

## Segurança

- `application.yml` está ignorado pelo Git.
- Use uma senha forte e exclusiva.
- Mantenha o serviço vinculado apenas à interface local durante os testes.
- Não exponha a porta 2333 à internet sem planejar autenticação, firewall e transporte seguro.
