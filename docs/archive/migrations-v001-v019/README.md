# Migrações históricas V001–V019

Cópia das migrações Flyway anteriores à baseline consolidada de 2026-10-26.

- **Não** são executadas pelo Flyway; o diretório ativo é
  `server/src/main/resources/db/migration/`.
- Servem para auditoria, reprodução do schema antigo e referência durante a
  limpeza.
- O dump completo anterior à baseline está em
  `backups/pre-baseline-20261026/` (diretório ignorado pelo Git).
- A baseline ativa é `server/src/main/resources/db/migration/V001__baseline.sql`.

Para reproduzir o schema antigo em um banco vazio, aplique os arquivos em ordem
numérica com `psql -v ON_ERROR_STOP=1 -f`. Não os recoloque no diretório ativo
sem uma estratégia de versionamento compatível com o histórico existente.
