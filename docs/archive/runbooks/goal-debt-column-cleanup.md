# Runbook: limpeza da dívida inicial legada das metas

**Escopo:** remover `financial_goal.initial_value_enc`, os checks de
`DEBT_REDUCTION` e a fonte `MANUAL_LIABILITY` de `goal_allocation` após o corte
S-03. Execute apenas quando a release sem redução de dívida já estiver estável.

## 1. Pré-requisitos

- A release com S-03 está em produção há pelo menos um ciclo de deploy.
- Existe backup recente e restaurável do banco **e** do key ring.
- As consultas abaixo retornam zero:

```sql
select count(*) from financial_goal
 where type = 'DEBT_REDUCTION'
    or progress_rule = 'DEBT_REDUCTION'
    or initial_value_enc is not null;

select count(*) from goal_allocation
 where source_type = 'MANUAL_LIABILITY';
```

O banco local foi conferido em 2026-10-26 e não tinha metas; confirme novamente no
ambiente de produção.

## 2. Exportar antes de alterar

```bash
export PGPASSWORD='<senha>'
pg_dump -h "$DELTA_DB_HOST" -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" \
  --data-only --table=public.financial_goal --table=public.goal_allocation \
  | age -r '<chave-pública-age>' > goals-legacy-$(date +%F).sql.age
```

## 3. Aplicar a migração de limpeza

Em uma release seguinte, criar migração Flyway (próxima versão livre) com:

```sql
alter table financial_goal drop constraint financial_goal_type_ck;
alter table financial_goal add constraint financial_goal_type_ck check (
  type in ('EMERGENCY_FUND', 'TRAVEL', 'PLANNED_PURCHASE', 'NET_WORTH')
);

alter table financial_goal drop constraint financial_goal_rule_ck;
alter table financial_goal add constraint financial_goal_rule_ck check (
  progress_rule in ('ALLOCATIONS', 'NET_WORTH')
);

alter table financial_goal drop constraint financial_goal_debt_initial_ck;
alter table financial_goal drop column initial_value_enc;

alter table goal_allocation drop constraint goal_allocation_source_ck;
alter table goal_allocation add constraint goal_allocation_source_ck check (
  source_type in ('MANUAL', 'ACCOUNT', 'INVESTMENT', 'MANUAL_ASSET')
);
```

No mesmo corte, remover a menção à coluna legada em
`specs/001-finance/data-model.md`, `docs/funcionalidades.md` e
`docs/operations.md`.

## 4. Verificação posterior

```sql
select column_name from information_schema.columns
 where table_name = 'financial_goal' and column_name = 'initial_value_enc';
-- Esperado: 0 linhas.

select pg_get_constraintdef(oid)
  from pg_constraint
 where conrelid = 'financial_goal'::regclass
   and conname in ('financial_goal_type_ck', 'financial_goal_rule_ck');
-- Esperado: checks sem DEBT_REDUCTION.
```

Rode `GoalsIT`, `MigrationIT`, `BackupRestoreIT` e o smoke de metas antes de
liberar a release.

## 5. Rollback

O rollback de aplicação não recria a coluna. Se precisar voltar à versão com
redução de dívida, restaure o backup completo ou aplique uma migração de reversão.
Como o código S-03 não grava `initial_value_enc`, manter a coluna por uma release
é a opção segura.
