# Runbook: exclusão de conta, retenção e tombstone

**Escopo**: T147 / FR-103–FR-108. Uso em pedido de exclusão, auditoria de
retenção ou verificação de que uma conta foi encerrada sem afetar outras.

## 1. Antes de começar

- Confirme que o pedido veio do titular autenticado. Não execute exclusão por
  e-mail, chat ou pedido de terceiro.
- A exclusão é **síncrona** e irreversível para os dados ativos. Cópias de backup
  expiram em até 30 dias; não há restauração seletiva de uma conta após a
  operação, apenas a restauração integral do banco a partir de um backup.
- Nunca registre senha, valores, descrições financeiras, Client Secret ou API Key
  em ticket, chat ou log operacional.

## 2. Fluxo do usuário

1. O titular acessa **Configurações → Privacidade** (`/settings/privacy`).
2. Em **Exportar meus dados**, baixa o ZIP se quiser uma cópia antes de sair.
   O formato está documentado em `docs/security.md` (JSON Lines + CSV).
3. Em **Desconectar integração**, escolhe se deseja excluir também os dados
   importados. O padrão preserva contas, transações e histórico; contas manuais,
   lançamentos nessas contas e categorias são sempre preservados.
4. Em **Excluir minha conta**, informa a senha atual, digita `EXCLUIR` e marca a
   ciência da política de retenção.
5. O servidor valida a senha e a confirmação, revoga todas as sessões, cancela
   jobs, apaga credenciais e dados ativos, grava os eventos de auditoria e
   retorna `202`. A sessão atual também é encerrada.

## 3. O que é removido

- `financial_account` do titular e, por cascade, transações, cartões, faturas,
  itens, pagamentos, correções, histórico de identidade externa e movimentos
  internos.
- Tabelas dos módulos mantidos `goal_progress_snapshot`, `goal_allocation`,
  `financial_goal`, `provider_investment_transaction`, `provider_investment`,
  `provider_investment_sync`, `investment_income` e `manual_asset_liability` do
  titular, apagadas explicitamente porque o tombstone de `app_user` não dispara
  o cascade.
- Categorias, integração Pluggy, conexões, jobs, execuções de sincronização e
  chaves de idempotência.
- Tentativas de login indexadas pelo HMAC do nome normalizado.
- Credenciais da integração (Client ID/Secret cifrados) e cache de API Key.

## 4. O que permanece

- Linha de `app_user` como **tombstone**: `status=DELETED`, `deleted_at`
  preenchido e hash de senha substituído por um hash inutilizável. O nome de
  usuário continua reservado para impedir reutilização/impersonação.
- `audit_event` sanitizado (`ACCOUNT_DELETION_REQUESTED`, `ACCOUNT_DELETED`,
  `DISCONNECTED`, `EXPORT_REQUESTED` etc.) por 1 ano, sem conteúdo financeiro.
- Backups criptografados por até 30 dias, conforme a política de backup.

## 5. Verificação

Execute como operador, sem abrir dados de outro usuário:

```sql
-- Deve retornar status DELETED e deleted_at não nulo.
select status, deleted_at from app_user where id = '<user-id>';

-- Dados financeiros ativos do titular devem estar zerados.
select 'financial_account' as tabela, count(*) from financial_account where user_id = '<user-id>'
union all select 'transaction', count(*) from transaction where user_id = '<user-id>'
union all select 'provider_investment_transaction', count(*) from provider_investment_transaction where user_id = '<user-id>'
union all select 'provider_investment', count(*) from provider_investment where user_id = '<user-id>'
union all select 'provider_investment_sync', count(*) from provider_investment_sync where user_id = '<user-id>'
union all select 'investment_income', count(*) from investment_income where user_id = '<user-id>'
union all select 'manual_asset_liability', count(*) from manual_asset_liability where user_id = '<user-id>'
union all select 'financial_goal', count(*) from financial_goal where user_id = '<user-id>'
union all select 'goal_allocation', count(*) from goal_allocation where user_id = '<user-id>'
union all select 'goal_progress_snapshot', count(*) from goal_progress_snapshot where user_id = '<user-id>'
union all select 'pluggy_integration', count(*) from pluggy_integration where user_id = '<user-id>';

-- Auditoria deve conter os dois eventos, com metadata apenas sanitizada.
select event_type, metadata from audit_event
 where user_id = '<user-id>'
   and event_type in ('ACCOUNT_DELETION_REQUESTED', 'ACCOUNT_DELETED');

-- Nenhum job ativo remanescente.
select count(*) from sync_job
 where user_id = '<user-id>' and status in ('QUEUED', 'RUNNING', 'RETRY');
```

Confirme também:

- login com a senha antiga retorna erro genérico;
- a sessão anterior retorna `401`;
- outro usuário continua autenticado e vendo apenas os próprios dados;
- a exportação de outro usuário não contém o nome, apelido ou descrições do
  titular excluído.

## 6. Retenção operacional

O job diário `RetentionJob` (03:30 em `America/Sao_Paulo`, configurável por
`DELTA_PRIVACY_RETENTION_CRON`) remove:

| Dado | Retenção |
|---|---|
| `login_attempt` | 30 dias |
| `sync_run` | 90 dias |
| `audit_event` | 1 ano |
| `idempotency_key` | 24 h após `expires_at` |

As tabelas de webhook e snapshot patrimonial foram removidas pela baseline
`V001__baseline.sql`; só existem em backups anteriores a 2026-10-26.

Falha do job não apaga dados vigentes. Verifique o log sanitizado
`Retenção executada` e as contagens. Se o job falhar repetidamente, trate como
incidente operacional; não execute `DELETE` manual sem revisar os mesmos
critérios de data.

## 7. Rollback

Não há rollback de exclusão de conta. Se um backup integral for restaurado,
o tombstone e os dados voltam ao estado do backup; comunique o titular e registre
a operação. Nunca restaure um dump sem o key ring correspondente.
