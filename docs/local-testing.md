# Checklist de testes locais

## 1. Preparar o ambiente

- [ ] Confirmar Java 17 ou superior com `java -version`.
- [ ] Na raiz do projeto, executar `./scripts/setup.ps1` no PowerShell ou `./scripts/setup.sh` no Bash.
- [ ] Confirmar que o script baixou `Lavalink.jar` da release oficial 4.2.2.
- [ ] Confirmar que o script criou `application.yml` com senha aleatória.
- [ ] Confirmar que `Lavalink.jar` e `application.yml` são ignorados pelo Git.

## 2. Inicialização

- [ ] Executar `./scripts/start.ps1` no PowerShell ou `./scripts/start.sh` no Bash.
- [ ] Confirmar nos logs que o servidor iniciou sem erro.
- [ ] Confirmar que a porta local 2333 está escutando.
- [ ] Em outro terminal, consultar `GET http://127.0.0.1:2333/version` com o cabeçalho `Authorization` contendo a senha de `application.yml`.

## 3. Segurança

- [ ] Confirmar que o serviço está vinculado a `127.0.0.1`.
- [ ] Confirmar que a senha não está no Git nem em logs compartilhados.
- [ ] Confirmar que `application.yml` aparece como ignorado pelo Git.

## 4. Próxima etapa: fontes e integração com o King

- [ ] Configurar o plugin de origem do YouTube compatível com a versão escolhida, se necessário.
- [ ] Testar cada fonte musical separadamente; não presumir suporte por padrão.
- [ ] Identificar a biblioteca de áudio atual e os pontos de entrada dos comandos do King.
- [ ] Escolher um cliente Node.js compatível com Lavalink v4 e com o suporte DAVE necessário.
- [ ] Implementar conexão e reconexão.
- [ ] Testar play, pause, resume, skip, stop e fila.
- [ ] Testar entrada/saída de canal de voz e recuperação após reinício do Lavalink.
