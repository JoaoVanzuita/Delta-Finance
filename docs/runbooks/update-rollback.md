# Runbook: atualização e rollback

**Escopo**: atualização controlada do stack self-hosted por imagens versionadas
no GHCR e retorno seguro à versão anterior, respeitando migrações
expand/contract.

> Execute este runbook no diretório do kit de release. O projeto Compose é
> `delta-finance` e o banco usa o volume `delta-finance-prod_pgdata`, separado
> do desenvolvimento (`delta-finance-dev_pgdata`).

## 1. Antes de atualizar

1. Leia o `docs/release-checklist.md` e confirme versão, tag e digests.
2. Faça backup do banco e copie `secrets/crypto-keys.json` e `.env` para local
   seguro. O procedimento está em `deploy/release/README.md`.
3. Registre a versão atual:

```bash
docker compose images
docker compose exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select max(version) from flyway_schema_history"'
```

4. Garanta que não há sincronização/job em andamento:
   `GET /api/v1/sync/status` deve mostrar fila vazia ou apenas jobs concluídos.
5. Valide o Compose sem subir:

```bash
docker compose config --quiet
```

6. Confirme que `DELTA_VERSION` no `.env` corresponde à versão que está rodando.

## 2. Atualizar

1. Altere `DELTA_VERSION` no `.env` para a nova versão, sem o prefixo `v`.
2. Baixe e recrie os serviços:

```bash
docker compose pull
docker compose up -d --remove-orphans
docker compose ps
```

Não use `docker compose down -v`; a opção `-v` remove o volume do PostgreSQL.

Verifique:

- `server` saudável em `/actuator/health/readiness`;
- migrações aplicadas no log (`Flyway`), sem falha;
- `client` saudável em `/healthz`;
- login, dashboard, uma sincronização e uma exportação.

Smoke test mínimo no host:

```bash
curl -fsS http://localhost:8080/healthz
curl -fsS http://localhost:8080/api/v1/auth/csrf -o /dev/null -w '%{http_code}\n'
docker compose exec server curl -fsS http://127.0.0.1:8080/actuator/health/readiness
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
# Edite DELTA_VERSION no .env para a versão anterior.
docker compose pull
docker compose up -d --remove-orphans
docker compose ps
```

Se a imagem anterior ainda estiver local, o `pull` pode ser omitido. Valide
health, login e dashboard. Se o schema recebeu apenas colunas/tabelas novas
(expand), a versão anterior continua funcionando.

### 4.2 Com migração incompatível

1. Pare `server` e `client`:

```bash
docker compose stop server client
```

2. Restaure o backup anterior. Use o procedimento do `deploy/release/README.md`
   com o mesmo key ring do momento do backup.
3. Ajuste `DELTA_VERSION` para a versão anterior e suba o stack:

```bash
docker compose up -d --remove-orphans
```

4. Valide contagens, dashboard, login e uma sincronização.
5. Comunique o incidente e registre a causa.

Nunca faça downgrade de schema por edição manual de tabela; use o backup e o
código da versão correspondente.

## 5. Pós-atualização

- Confirme retenção de backup e rotação de chaves vigentes.
- Verifique métricas de sincronização e fila.
- Atualize o registro de versão e digests no `docs/release-checklist.md`.
- Mantenha o backup anterior por pelo menos 30 dias.
- Mantenha as imagens/tags anteriores no GHCR para permitir novo rollback.
