# Runbook: recuperação assistida de senha

**Escopo**: T193 / FR-139 e T197. Não há e-mail transacional no MVP; a
recuperação é assistida pelo operador, com verificação de identidade fora do
sistema.

## 1. Princípios

- O titular não prova identidade por e-mail ou pergunta secreta.
- O operador verifica a identidade por canal independente (presencial, vídeo,
  telefone com retorno em número conhecido).
- Nenhum dado financeiro é exibido ao operador; o mecanismo atua apenas em
  `app_user`.
- A senha temporária é exibida uma única vez no terminal do operador, nunca em
  log, auditoria, issue ou chat.
- Todas as sessões do titular são revogadas, inclusive a atual. Bloqueios de
  login por tentativas são limpos.

## 2. Preparação

1. Configure `DELTA_OPERATOR_RESET_TOKEN` no `.env` do host com um valor
   aleatório forte (`openssl rand -base64 32`). Sem ele, o mecanismo fica
   desabilitado.
2. Garanta que o stack está de pé (`docker compose ps`).
3. Abra um canal seguro com o titular.

## 3. Walkthrough com conta de teste

Em ambiente de laboratório:

```bash
# 1. Criar uma conta de teste pela interface.
# 2. Autenticar em dois navegadores/dispositivos (duas sessões).
# 3. Executar:
./deploy/scripts/reset-password.sh \
  --username alice.teste \
  --operator "Operador Delta"

# O script pede o nome de usuário novamente para confirmar.
# 4. Guardar a senha temporária exibida e entregá-la ao titular.
# 5. Confirmar que as duas sessões antigas retornam 401.
# 6. Entrar com a senha temporária e trocar a senha.
# 7. Confirmar que outro usuário continua autenticado e com a senha original.
```

O teste automatizado equivalente é `PasswordResetIT`:

```bash
cd server
./mvnw -Dit.test=PasswordResetIT -DfailIfNoTests=false verify
```

Ele cobre token inválido, usuário inexistente, revogação de sessões, troca
efetiva, isolamento entre dois usuários e ausência da senha temporária na
auditoria.

## 4. Auditoria

Consulte:

```sql
select event_type, target_type, target_id, metadata, created_at
  from audit_event
 where event_type = 'PASSWORD_RESET_ASSISTED'
 order by created_at desc
 limit 10;
```

`metadata` contém apenas `operator`, `sessionsRevoked` e `performedAt`. Não
contém senha, hash, token ou dados financeiros. O evento é retido por 1 ano.

## 5. Verificação de isolamento

- A conta de outro usuário não muda de senha.
- A sessão de outro usuário permanece válida.
- A exportação de outro usuário não contém dados do titular.
- O operador não recebe lista de usuários nem dados financeiros.

## 6. Falhas e limites

| Sintoma | Ação |
|---|---|
| `DELTA_OPERATOR_RESET_TOKEN não definido` | configurar o token no `.env` e repetir |
| `Token operacional inválido` | conferir o valor; não registrar o token em ticket |
| `Usuário inexistente` (`NotFoundException`) | confirmar o nome com o titular; não revelar se a conta existe a terceiros |
| Senha temporária não exibida | executar novamente; a anterior é invalidada |
| Sessões antigas ainda ativas | verificar se o processo concluiu e o health do servidor |

## 7. Encerramento

- Entregue a senha por canal seguro e oriente a troca imediata.
- Não reutilize a senha temporária.
- Registre no ticket apenas: data, operador, usuário, confirmação de identidade
  e resultado; nunca a senha.
