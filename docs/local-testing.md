# Checklist de testes locais

## 1. Ambiente

- [ ] Confirmar Java instalado com `java -version`.
- [ ] Baixar uma release estável do Lavalink v4.
- [ ] Confirmar que `Lavalink.jar` não será commitado.
- [ ] Copiar `application.example.yml` para `application.yml`.
- [ ] Definir `LAVALINK_SERVER_PASSWORD`.

## 2. Inicialização

- [ ] Executar `java -jar Lavalink.jar`.
- [ ] Confirmar nos logs que o servidor iniciou sem erro.
- [ ] Confirmar que a porta local 2333 está escutando.
- [ ] Consultar `GET /version` usando o cabeçalho `Authorization`.

## 3. Segurança

- [ ] Confirmar que o serviço está vinculado a `127.0.0.1`.
- [ ] Confirmar que a senha não está no Git nem nos logs.
- [ ] Confirmar que `application.yml` aparece como ignorado pelo Git.

## 4. Próxima etapa: integração com o King

- [ ] Identificar a biblioteca de áudio atual e os pontos de entrada dos comandos.
- [ ] Escolher um cliente Node.js compatível com Lavalink v4.
- [ ] Implementar conexão e reconexão.
- [ ] Testar play, pause, resume, skip, stop e fila.
- [ ] Testar entrada/saída de canal de voz e recuperação após reinício do Lavalink.
- [ ] Testar cada fonte musical separadamente; não presumir suporte por padrão.
