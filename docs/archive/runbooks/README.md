# Runbooks arquivados

Procedimentos executados pela baseline consolidada `V001__baseline.sql` em
2026-10-26.

| Runbook | Procedimento original |
|---|---|
| `webhook-tables-cleanup.md` | exportar e remover tabelas legadas de webhook |
| `wealth-snapshot-tables-cleanup.md` | exportar e remover snapshots patrimoniais |
| `goal-debt-column-cleanup.md` | remover coluna/checks da dívida inicial |

Eles não devem ser executados novamente: as tabelas, colunas e checks já não
existem no schema ativo. O backup anterior à baseline está em
`backups/pre-baseline-20261026/` (ignorado pelo Git) e as migrações históricas em
[`docs/archive/migrations-v001-v019/`](../migrations-v001-v019/).
