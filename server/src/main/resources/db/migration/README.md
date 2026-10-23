As migrações Flyway versionadas ficam neste diretório (V001, V002, ...).

## Regras

- Nome: `V<versão>__<descrição>.sql`, versão numérica crescente e imutável.
- Uma migração aplicada **nunca** é editada; correções exigem nova versão.
- Toda migração deve ser compatível com a versão anterior da aplicação durante
  o deploy (expand/contract), porque server antigo e novo podem coexistir por
  alguns segundos.
- Nunca executar `DROP`/`TRUNCATE` sem backup verificado e sem etapa de
  contrato posterior.
- Dados cifrados exigem migração de chave separada; não misturar com migração de
  schema.
- Índices grandes usam `CREATE INDEX CONCURRENTLY` quando possível (fora de
  transação; a migração precisa ser marcada como não transacional).
- Toda migração é testada em banco vazio e em snapshot da versão anterior
  (`MigrationIT`). Falha de migração impede o start e, portanto, o readiness.

## Expand/contract

1. **Expand**: adicionar tabela/coluna/índice compatível; nada de renomear ou
   remover no mesmo release.
2. **Backfill**: preencher dados em lotes idempotentes, sem bloquear a tabela.
3. **Migrar leitura/escrita**: a aplicação passa a usar a nova estrutura.
4. **Contract**: em release posterior, remover a estrutura antiga após backup e
   confirmação de que nenhum cliente a utiliza.

Exemplo: para tornar uma coluna obrigatória, adicione-a anulável, faça backfill,
publique a aplicação que sempre a preenche e só depois aplique `NOT NULL`.

## Teste local

```bash
cd server
./mvnw verify                 # inclui MigrationIT (Testcontainers)
./mvnw verify -Dflyway.target=001   # limita a versão aplicada
```

O `PostgresTestBase` suporta `snapshot`/`restore` via `pg_dump`/`pg_restore`,
usado para simular um banco já existente em uma versão anterior.
