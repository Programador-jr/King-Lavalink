# Checklist de testes locais

## 1. Preparar o ambiente

- [ ] Confirmar Java 17 ou superior com `java -version`.
- [ ] Na raiz do projeto, executar `./scripts/setup.ps1` no PowerShell ou `./scripts/setup.sh` no Bash.
- [ ] Confirmar que o script baixou `Lavalink.jar` da release oficial 4.2.2.
- [ ] Confirmar que o script criou `application.yml` com senha aleatória.
- [ ] Confirmar que `Lavalink.jar` e `application.yml` são ignorados pelo Git.
- [ ] Mesclar as seções novas de `application.example.yml` no `application.yml` local existente, preservando a senha.

## 2. Configuração YouTube

- [ ] Confirmar que `lavalink.plugins` declara `dev.lavalink.youtube:youtube-plugin:1.18.2` com `snapshot: false`.
- [ ] Confirmar que `lavalink.server.sources.youtube` continua como `false`.
- [ ] Confirmar que `plugins.youtube.enabled` está como `true`.
- [ ] Confirmar que `plugins.youtube.remoteCipher.url` aponta para `https://cipher.kikkia.dev/` ou para uma instância própria de yt-cipher.
- [ ] Confirmar que `TV` está na lista de clientes e `plugins.youtube.oauth.enabled` está como `true`.
- [ ] Reiniciar Lavalink e confirmar nos logs que o plugin foi baixado/carregado sem erro.
- [ ] Se solicitado, concluir o fluxo OAuth com uma conta dedicada de teste (não a conta principal) e guardar o refresh token somente na configuração local/segredo de hospedagem.
- [ ] Confirmar nos logs que o OAuth foi autorizado e o token foi atualizado antes de testar a reprodução.
- [ ] Se uma instância própria de yt-cipher estiver configurada, confirmar nos logs que o resolvedor remoto foi ativado.
- [ ] Testar uma busca YouTube e reproduzir uma faixa pública.

O template usa a instância pública mantida pelo projeto yt-cipher (`https://cipher.kikkia.dev/`), sem garantia de disponibilidade contínua. Para produção, prefira hospedar uma instância própria. Não use o endpoint anterior `cipher.lavalink-harmonix.me`, que apresentou erro de implantação pausada.

O cipher não resolve erros `This video requires login`: para isso, o template habilita OAuth e o cliente `TV`. O plugin alerta que autenticação pode causar rate limits ou encerramento da conta. Use uma conta dedicada de teste, nunca a conta principal, e não compartilhe nem versione o refresh token.

## 3. Inicialização e autenticação

- [ ] Executar `./scripts/start.ps1` no PowerShell ou `./scripts/start.sh` no Bash.
- [ ] Confirmar nos logs que o servidor iniciou sem erro.
- [ ] Confirmar que a porta local 2333 está escutando.
- [ ] Em outro terminal, consultar `GET http://127.0.0.1:2333/version` com o cabeçalho `Authorization` contendo a senha de `application.yml`.

## 4. Segurança

- [ ] Confirmar que o serviço está vinculado a `127.0.0.1`.
- [ ] Confirmar que a senha não está no Git nem em logs compartilhados.
- [ ] Confirmar que `application.yml` aparece como ignorado pelo Git.
- [ ] Não expor a porta 2333 à internet sem planejar autenticação, firewall e transporte seguro.

## 5. Integração local com o King

- [ ] Rodar uma cópia local do King para que ela possa acessar `127.0.0.1:2333`.
- [ ] Na cópia local do bot, selecionar temporariamente o nó `localhost:2333` no `botconfig/lavalink.json` em vez dos nós públicos.
- [ ] Configurar a mesma senha usada em `application.yml`.
- [ ] Testar play, pause, resume, skip, stop e fila.
- [ ] Testar entrada/saída de canal de voz e recuperação após reinício do Lavalink.
- [ ] Não publicar a alteração temporária do nó local nem alterar produção antes dos testes.
