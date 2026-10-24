# Runbook: atualização e rollback

**Escopo**: T187. Atualização controlada do stack self-hosted e retorno seguro à
versão anterior, respeitando migrações expand/contract.

## 1. Antes de atualizar

1. Leia o `docs/release-checklist.md` e confirme versões/digests.
2. Faça backup verificado: `./deploy/scripts/backup.sh`.
3. Copie o key ring para local seguro (não vai no dump).
4. Registre a versão atual:

```bash
docker compose images
docker compose exec -T db psql -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" -tAc \
  "select max(version) from flyway_schema_history"
```

5. Garanta que não há sincronização/job em andamento:
   `GET /api/v1/sync/status` deve mostrar fila vazia ou apenas jobs concluídos.
6. Valide o Compose sem subir: `docker compose config --quiet`.

## 2. Atualizar

```bash
git fetch --tags
git checkout <tag-ou-commit>
docker compose pull
docker compose build
docker compose up -d
docker compose ps
```

Verifique:

- `server` saudável em `/actuator/health/readiness`;
- migrações aplicadas no log (`Flyway`), sem falha;
- `client` e `proxy` saudáveis;
- login, dashboard, uma sincronização e uma exportação.

Smoke test mínimo:

```bash
curl -fsS https://SEU_DOMINIO/healthz
curl -fsS https://SEU_DOMINIO/api/v1/auth/csrf -o /dev/null -w '%{http_code}\n'
```

## 3. Migrações expand/contract

- Migração nova deve ser compatível com a versão anterior (expand).
- Nunca edite migração já aplicada; crie a próxima versão.
- A remoção de coluna/índice (contract) só ocorre em release posterior, após
  todas as instâncias rodarem a versão que não usa mais o objeto.
- `MigrationIT` valida banco vazio, banco existente e idempotência.

Se a migração falhar:

1. mantenha o `server` parado;
2. leia o log sanitizado;
3. restaure o backup e a versão anterior;
4. abra incidente com horário, versão e código de erro (sem dados financeiros).

## 4. Rollback

### 4.1 Somente aplicação (sem migração destrutiva)

```bash
docker compose stop server
git checkout <tag-anterior>
docker compose build server
docker compose up -d server
```

Valide health, login e dashboard. Se o schema já recebeu apenas colunas/tabelas
novas (expand), a versão anterior continua funcionando.

### 4.2 Com migração incompatível

1. Pare `server` e `client`.
2. Restaure o backup anterior:

```bash
export AGE_IDENTITY_FILE="$HOME/age-key.txt"
./deploy/scripts/restore.sh --file backups/<backup>.dump.age --yes
```

3. Faça checkout da versão anterior e suba o stack.
4. Valide contagens, dashboard, login e uma sincronização.
5. Comunique o incidente e registre a causa.

Nunca faça downgrade de schema por edição manual de tabela; use o backup e o
código da versão correspondente.

## 5. Pós-atualização

- Confirme retenção de backup e rotação de chaves vigentes.
- Verifique métricas de sincronização e fila.
- Atualize o registro de versão no `docs/release-checklist.md`.
- Mantenha o backup anterior por pelo menos 30 dias.


## 6. Ensaio executado em 2026-10-24

Drill sintético em ambiente isolado, sem dados reais:

- projeto Compose separado `delta-drill`, PostgreSQL 18.6, key ring descartável;
- versão N-1 com migrações V001–V006;
- versão N com migração expand-only `V007__release_drill.sql` (tabela
  `release_drill_marker`);
- usuário, conta e transação sintéticos; backup `pg_dump -Fc` antes do upgrade.

Resultado:

1. N-1 subiu, health `UP`, dashboard com saldo `1000.00` e despesa `100.00`.
2. N aplicou V007, health `UP`, tabela marcadora presente; contagens
   `app_user=1`, `financial_account=1`, `transaction=1`.
3. Rollback para N-1: health `UP`, mesmas contagens e mesmos totais. O Flyway
   registrou o aviso esperado de schema 007 mais novo que a última migração
   local (006), mas a aplicação subiu e operou normalmente — a migração era
   compatível (expand-only).
4. Banco, volume, imagens e diretório temporários foram removidos; nenhuma
   alteração no repositório ou no ambiente real.

Limite do ensaio: não foi executada uma migração destrutiva (contract). Nesse
caso, o rollback exige `restore.sh` com o backup, conforme seções 3 e 4.
