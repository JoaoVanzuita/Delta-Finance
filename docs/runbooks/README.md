# Runbooks

Procedimentos operacionais. Cada runbook é escrito e testado na tarefa indicada;
os arquivos abaixo são criados quando o procedimento correspondente é validado.

| Runbook | Quando usar | Status |
|---|---|---|
| `connection.md` | configurar uma conexão Meu Pluggy e validar credenciais | disponível |
| `reconnect.md` | autorização anual expirada, itemId alterado ou conexão removida | disponível |
| `key-rotation.md` | rotacionar chaves de criptografia/índice com reencriptação | disponível |
| `backup.md` | rotina diária de backup, verificação e retenção de 30 dias | disponível |
| `restore.md` | restaurar em banco limpo após incidente ou perda | disponível |
| `update-rollback.md` | atualizar o stack e reverter com segurança | disponível |
| `incident.md` | indisponibilidade, vazamento suspeito ou falha de sync | disponível |
| `deletion.md` | exclusão de conta, retenção e tombstone | disponível |
| `password-reset.md` | recuperação assistida de senha (sem e-mail transacional) | disponível |
| `pluggy-acceptance.md` | gate real com duas aplicações Meu Pluggy independentes | disponível; execução real pendente |

## Princípios

- Nenhum runbook pede para colar segredos em canais, issues ou logs. Use o
  `.env`/`secrets/` no host e nunca exponha Client Secret, API Key ou conteúdo
  financeiro.
- Toda ação destrutiva (restore, exclusão de conta, rotação de chave) exige
  confirmação explícita e registro do que foi feito e quando.
- Backup do banco sem o key ring não permite recuperar dados cifrados. O backup
  do key ring é um procedimento separado e obrigatório.
- Validação de procedimento inclui execução real em ambiente limpo, não apenas
  revisão do texto.
