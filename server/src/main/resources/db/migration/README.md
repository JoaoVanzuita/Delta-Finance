As migrações Flyway versionadas ficam neste diretório (V001, V002, ...).

Regras (ver specs/001-finance/data-model.md e tarefa T039):

- O nome segue `V<versão>__<descrição>.sql`, com versão numérica crescente.
- Migrações já aplicadas são imutáveis; correções exigem nova versão.
- Aplicar expand/contract: adicionar colunas/tabelas compatíveis, migrar dados,
  só depois remover o que ficou obsoleto.
- Toda migração é testada em banco vazio e em snapshot da versão anterior
  (`MigrationIT`).
