# Operação

Guia operacional do Delta Finance (self-hosted). Complementa
[`../README.md`](../README.md), [`../deploy/README.md`](../deploy/README.md) e
[`security.md`](security.md).

## Subir e atualizar

```bash
cp .env.example .env            # apenas na primeira vez
mkdir -p secrets                # key ring, ver deploy/README.md
docker compose pull
docker compose up -d --build
docker compose ps
```

Atualizações seguem expand/contract: migrações compatíveis são aplicadas no
start do backend; uma migração com falha impede o readiness e o tráfego não é
liberado. Procedimento completo em `runbooks/update-rollback.md`.

## Health checks

| Serviço | Endpoint | Observação |
|---|---|---|
| `server` | `/actuator/health/liveness` | processo vivo |
| `server` | `/actuator/health/readiness` | banco + key ring (versões ativas, sem material de chave) |
| `client` | `/healthz` | SPA servida pelo Caddy interno |
| `proxy` | `127.0.0.1:2019/config/` | liveness do Caddy (admin local) |
| `db` | `pg_isready` | pronto para conexões |

O Actuator não deve ser exposto publicamente; apenas a rede interna do Compose
tem acesso.

## Logs

- Logs vão para stdout/stderr e são coletados pelo Docker
  (`docker compose logs -f server`).
- Formato JSON estruturado, com `requestId`, evento, resultado, duração e código
  de erro. Valores financeiros, descrições, Client Secret e API
  Key nunca são registrados.
- `sync_run` é a fonte de verdade operacional de cada tentativa de sincronização
  .
- Logs de sincronização incluem apenas `connectionId`/`integrationId`,
  `correlationId`, estado e código sanitizado; nunca descrição, valor, itemId ou
  corpo de resposta do provedor.

## Sincronização

- **Fila durável** `sync_job`: claim atômico com `FOR UPDATE SKIP LOCKED`; um
  único job ativo por conexão/tipo. `POST /api/v1/connections/{id}/sync`
  enfileira a consulta dos dados já disponíveis e responde `202`, sem forçar a
  instituição de origem a atualizar.
- **Janelas configuráveis** (`DELTA_SYNC_*`, padrão entre parênteses):
  carga inicial de 12 meses (`INITIAL_WINDOW_MONTHS=12`); reprocessamento diário
  de 7 dias com sobreposição (`DAILY_WINDOW_DAYS=7`); reconciliação mensal de 12
  meses (`RECONCILIATION_WINDOW_MONTHS=12`) no dia
  `RECONCILIATION_DAY=1`. O scheduler roda a cada `SCHEDULER_INTERVAL=1m` com
  jitter de até `JITTER=60s`.
- **Contrato da Pluggy**: em `GET /v2/transactions` use `dateFrom`/`dateTo` e o
  cursor `next` como query string (o `after` deve ser extraído e decodificado).
  **Não envie `pageSize`**: a API responde `400 property pageSize should not
  exist`; o tamanho padrão é 500. Um `400` de parâmetro é registrado no log como
  `Falha do provedor: endpoint=... status=400` e não significa,
  necessariamente, indisponibilidade do provedor.
- **Retries**: até `MAX_ATTEMPTS=5` por job, com backoff exponencial (base 30 s,
  teto 1 h) ou `Retry-After` do provedor. Falha de uma conexão não bloqueia as
  demais. Credencial recusada e itemId inválido/inexistente exigem ação do
  usuário e não entram em retry automático.
- **Estados**: `INCOMPLETE`, `ACTIVE`, `SYNCING`, `SYNCED`,
  `CREDENTIAL_FAILURE`, `ACTION_REQUIRED`, `AUTHORIZATION_EXPIRED`,
  `ITEM_UNAVAILABLE`. A expiração anual da aplicação
  (`USER_AUTHORIZATION_EXPIRED`) é distinta do consentimento bancário de cada
  item (`CONSENT_EXPIRED`); ambos exigem reautorização e atualização do itemId,
  com histórico preservado.
- **Lista vazia** produz `EMPTY` e não marca remoções. `SOURCE_REMOVED` só é
  atribuído quando uma leitura conclusiva e não vazia deixa de trazer a
  transação; `POST /api/v1/transactions/{id}/source-removal` permite
  `RESTORE` (validado na origem) ou `CONFIRM_REMOVAL`, sempre preservando a
  linha e a auditoria.
- **Webhooks** são opcionais (`DELTA_PLUGGY_WEBHOOKS_ENABLED=false`), exigem
  HTTPS público, segredo por inscrição e `eventId` idempotente. São apenas sinal
  para consultar os dados completos; a sincronização programada continua sendo a
  reconciliação.
- **Painel** `/settings/integration` mostra estado, última tentativa, último
  sucesso, fila, histórico (`GET /api/v1/sync/status` e `/sync/runs`) e ações de
  consultar agora, atualizar itemId e remover conexão.
- **Métricas**: `delta.sync.runs`, `delta.sync.duration`, `delta.sync.failures`,
  `delta.sync.jobs` e `delta.sync.records` via Actuator. Alertas recomendados:
  falhas repetidas, fila acumulada, `AUTHORIZATION_EXPIRED`/`CONSENT_EXPIRED` e
  ausência de sincronização bem-sucedida no período esperado.

## Banco e migrações

- PostgreSQL 18.6, volume `pgdata`.
- Migrações Flyway em `server/src/main/resources/db/migration/`, aplicadas no
  start. Nunca edite uma migração já aplicada; crie a próxima versão.
- `MigrationIT` valida banco vazio, snapshot da versão anterior e
  idempotência.
- Acesso administrativo local:

```bash
docker compose exec db psql -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME"
```

## Backup e restauração

- `deploy/scripts/backup.sh`: `pg_dump -Fc`, cifrado com `age`, retenção padrão
  de 30 dias.
- `deploy/scripts/restore.sh`: restauração em banco limpo, com confirmação.
- O key ring é copiado separadamente; backup do banco sem chaves não permite
  leitura dos campos cifrados.
- RPO de 24 h e RTO de 2 h para a operação de referência. Ensaio automatizado
  em `BackupRestoreIT`; procedimento em `runbooks/backup.md` e `runbooks/restore.md`.

## Retenção de dados

Prazos implementados pelo `RetentionJob`: `login_attempt` 30 dias, `sync_run` e
`webhook_event` 90 dias, `audit_event` 1 ano, `idempotency_key` 24 h.

## Observabilidade e alertas

- Métricas Micrometer via Actuator, com foco em latência de requisições,
  fila de sincronização, falhas por código e duração de relatórios.
- Alertas recomendados: falhas repetidas de sincronização,
  fila acumulada, expiração de autorização anual da Pluggy e falha de backup.
- Não há cache de dados financeiros; no máximo metadados curtos de status.

## Recuperação assistida de senha

O MVP não usa e-mail transacional. O procedimento assistido, com verificação de
identidade pelo operador, invalidação de sessões e auditoria, é implementado em
pelo `AssistedPasswordResetService` e por `deploy/scripts/reset-password.sh`;
procedimento em `runbooks/password-reset.md`.

## Segurança operacional

- Segredos apenas em `.env` e `secrets/`, ambos fora do Git.
- Acesso ao host restrito ao operador; TLS obrigatório na rede.
- Dependências, imagens e segredos são escaneados por `.github/workflows/security.yml`
  (gitleaks, Trivy filesystem e Trivy image).

## Performance

Cenário de referência: 5 usuários, 5 conexões por usuário e 10.000 transações por
usuário. As metas são dashboard/filtros p95 ≤ 2 s, CRUD/listas p95 ≤ 500 ms,
login/logout p95 ≤ 1 s e sincronização de uma conexão ≤ 60 s.

1. Suba um banco de desenvolvimento migrado e a API.
2. Semeie dados sintéticos:

```bash
DELTA_DB_URL=jdbc:postgresql://localhost:5432/delta_finance \
DELTA_DB_USER=delta DELTA_DB_PASSWORD=delta \
DELTA_CRYPTO_KEY_FILE=./secrets/crypto-keys.json \
./deploy/scripts/perf-seed.sh
```

3. Execute os cenários k6:

```bash
docker run --rm --network host -i grafana/k6:2.3.0 run - < server/perf/dashboard.js
CONNECTION_ID=<uuid> docker run --rm --network host -i grafana/k6:2.3.0 run - \
  < server/perf/auth-crud-sync.js
```

O semeador recria `perf.dashboard`; nunca use em produção. Registre apenas
latências e contagens, sem dados financeiros. Se o dashboard exceder 2 s,
verifique volume do período, quantidade de candidatos descriptografados e
índices por `user_id`/data; não reduza a janela de reconciliação para mascarar o
problema.

**Resultado de 2026-10-24** (stack local, 5 usuários sintéticos com 10.000
transações no usuário de performance, 3 VUs por 30 s para dashboard e 1–4 VUs por
20 s para login/CRUD/sync): dashboard/filtros/drill-down p95 ≈ 365 ms; login p95
≈ 39 ms; logout p95 ≈ 7 ms; CRUD/listas p95 ≈ 9–16 ms; sincronização p95 ≈
58 ms (stub local com 1.000 transações); 0% de falhas HTTP. A sincronização usa
um stub local apenas para medir a fila e o processamento; a latência real do
provedor deve ser medida no gate `pluggy-acceptance.md`.

## Rotação de chave

`KeyRotationJob` regrava os campos cifrados com a versão ativa, em lotes e de
forma idempotente. A execução é única, via `deploy/scripts/rotate-keys.sh --yes`,
e exige a chave antiga no key ring até o fim. A rotação do índice HMAC é manual e
exige reindexação. Passo a passo em `runbooks/key-rotation.md`.

## Segurança e dependências

- `.github/workflows/security.yml` executa gitleaks, Trivy `fs` e Trivy `image`
  em push, pull request e semanalmente.
- ZAP baseline: `deploy/scripts/zap-baseline.sh [url...]`; relatórios em
  `docs/security/zap/`.
- Cobertura: JaCoCo exige 80% de linhas no domínio e 100% em `Money`,
  `OwnershipGuard` e `AuthenticatedUser`; o build falha abaixo disso.
- Release: `docs/release-checklist.md` fixa versões/digests e gates manuais.
