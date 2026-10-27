# Runbook: limpeza das tabelas legadas de webhook

**Escopo:** drop de `webhook_subscription` e `webhook_event` após o corte S-04,
com exportação/backup prévio. Execute apenas quando a versão sem webhooks já
estiver estável em produção.

## 1. Pré-requisitos

- A release com S-04 (código/UI/contrato sem webhooks) está em produção há pelo
  menos um ciclo de deploy.
- Existe backup recente e restaurável do banco **e** do key ring.
- O responsável confirmou que não há consumidor externo dos endpoints removidos.
- As contagens abaixo foram registradas e conferidas:

```sql
select 'webhook_subscription' as tabela, count(*) from webhook_subscription
union all
select 'webhook_event', count(*) from webhook_event
union all
select 'sync_job_process_webhook', count(*) from sync_job
 where job_type = 'PROCESS_WEBHOOK';
```

## 2. Exportar antes de apagar

Faça backup lógico das duas tabelas. O conteúdo pode conter HMAC, ciphertext e
metadados; trate o arquivo como sensível e mantenha-o criptografado.

```bash
export PGPASSWORD='<senha>'
pg_dump -h "$DELTA_DB_HOST" -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" \
  --data-only --table=public.webhook_subscription --table=public.webhook_event \
  | age -r '<chave-pública-age>' > webhook-legacy-$(date +%F).sql.age
```

Alternativa em CSV, se o operador precisar de inspeção:

```sql
\copy (select * from webhook_subscription order by created_at, id) to 'webhook_subscription.csv' csv header
\copy (select * from webhook_event order by received_at, id) to 'webhook_event.csv' csv header
```

Valide que o backup abre e contém as linhas esperadas antes de prosseguir.

## 3. Aplicar a migração de limpeza

Em uma release seguinte, criar migração Flyway (próxima versão livre) com:

```sql
-- Jobs de webhook não têm mais processador. Preserve o backup antes do delete.
delete from sync_job where job_type = 'PROCESS_WEBHOOK';

alter table sync_job drop constraint sync_job_type_ck;
alter table sync_job add constraint sync_job_type_ck check (
  job_type in ('SYNC_INTEGRATION', 'SYNC_CONNECTION', 'RECALCULATE_INVOICE', 'RECONCILE')
);

drop table webhook_event;
drop table webhook_subscription;
```

No mesmo corte, remover as limpezas legadas de `webhook_event` em
`AccountDeletionService` e `ConnectionService`, os valores `WEBHOOK_*` de
`AuditEventType` e as seções legadas de `specs/001-finance/data-model.md`.

## 4. Verificação posterior

```sql
select to_regclass('public.webhook_subscription') as webhook_subscription,
       to_regclass('public.webhook_event') as webhook_event;
-- Esperado: NULL em ambas.

select count(*) from sync_job where job_type = 'PROCESS_WEBHOOK';
-- Esperado: 0.

select conname, pg_get_constraintdef(oid)
  from pg_constraint
 where conrelid = 'sync_job'::regclass
   and conname = 'sync_job_type_ck';
-- Esperado: CHECK sem PROCESS_WEBHOOK.
```

Rode `MigrationIT`, `BackupRestoreIT`, `RetentionJobIT` e o smoke de sincronização
antes de liberar a release.

## 5. Rollback

O rollback de aplicação não recria as tabelas. Se precisar voltar à versão com
webhooks, restaure o backup completo ou aplique uma migração de reversão que
recrie o schema do zero. Como o código S-04 não grava webhooks, manter as tabelas
por uma release é a opção segura.
