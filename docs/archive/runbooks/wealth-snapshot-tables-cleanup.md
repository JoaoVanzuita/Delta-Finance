# Runbook: limpeza das tabelas legadas de snapshot patrimonial

**Escopo:** drop de `wealth_snapshot` e `wealth_snapshot_item` após o corte S-02,
com exportação/backup prévio. Execute apenas quando a release sem snapshots já
estiver estável em produção.

## 1. Pré-requisitos

- A release com S-02 está em produção há pelo menos um ciclo de deploy.
- Existe backup recente e restaurável do banco **e** do key ring.
- O responsável confirmou que não há consumidor dos endpoints removidos.
- As contagens foram registradas:

```sql
select 'wealth_snapshot' as tabela, count(*) from wealth_snapshot
union all
select 'wealth_snapshot_item', count(*) from wealth_snapshot_item;
```

## 2. Exportar antes de apagar

```bash
export PGPASSWORD='<senha>'
pg_dump -h "$DELTA_DB_HOST" -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" \
  --data-only --table=public.wealth_snapshot --table=public.wealth_snapshot_item \
  | age -r '<chave-pública-age>' > wealth-snapshot-legacy-$(date +%F).sql.age
```

Alternativa em CSV para inspeção:

```sql
\copy (select * from wealth_snapshot order by reference_date, id) to 'wealth_snapshot.csv' csv header
\copy (select * from wealth_snapshot_item order by snapshot_id, id) to 'wealth_snapshot_item.csv' csv header
```

Valide que o backup abre e contém as linhas esperadas antes de prosseguir.

## 3. Aplicar a migração de limpeza

Em uma release seguinte, criar migração Flyway (próxima versão livre) com:

```sql
drop table wealth_snapshot_item;
drop table wealth_snapshot;
```

No mesmo corte, remover:

- as limpezas legadas de `wealth_snapshot*` em `AccountDeletionService`;
- as limpezas em `PostgresTestBase`;
- as seções 7.3 e 7.4 de `specs/001-finance/data-model.md`;
- a menção a tabelas legadas em `funcionalidades.md` e `operations.md`.

## 4. Verificação posterior

```sql
select to_regclass('public.wealth_snapshot') as wealth_snapshot,
       to_regclass('public.wealth_snapshot_item') as wealth_snapshot_item;
-- Esperado: NULL em ambas.
```

Rode `MigrationIT`, `BackupRestoreIT`, `WealthIT`, `ExportIT` e o smoke de
patrimônio antes de liberar a release.

## 5. Rollback

O rollback de aplicação não recria as tabelas. Se precisar voltar à versão com
snapshots, restaure o backup completo ou aplique uma migração de reversão que
recrie o schema do zero. Como o código S-02 não grava snapshots, manter as
tabelas por uma release é a opção segura.
