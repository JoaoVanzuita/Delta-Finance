# Runbooks

Procedimentos operacionais. Cada runbook é escrito e testado na tarefa indicada;
os arquivos abaixo são criados quando o procedimento correspondente é validado.

| Runbook | Quando usar | Status |
|---|---|---|
| `connection.md` | configurar uma conexão Meu Pluggy e validar credenciais | T180 |
| `reconnect.md` | autorização anual expirada, itemId alterado ou conexão removida | T180 |
| `key-rotation.md` | rotacionar chaves de criptografia/índice com reencriptação | T176 |
| `backup.md` | rotina diária de backup, verificação e retenção de 30 dias | T175 |
| `restore.md` | restaurar em banco limpo após incidente ou perda | T175 |
| `update-rollback.md` | atualizar o stack e reverter com segurança | T187 |
| `incident.md` | indisponibilidade, vazamento suspeito ou falha de sync | T180 |
| `deletion.md` | exclusão de conta, retenção e tombstone | T147 |
| `password-reset.md` | recuperação assistida de senha (sem e-mail transacional) | T193 |
| `pluggy-acceptance.md` | gate real com duas aplicações Meu Pluggy independentes | T182 |

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
