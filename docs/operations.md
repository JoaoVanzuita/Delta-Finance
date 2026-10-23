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
liberado. O runbook de atualização/rollback entra em T187.

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
  de erro, entra em T029. Valores financeiros, descrições, Client Secret e API
  Key nunca são registrados.
- `sync_run` é a fonte de verdade operacional de cada tentativa de sincronização
  (T076+).
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
- **Retries**: até `MAX_ATTEMPTS=5` por job, com backoff exponencial (base 30 s,
  teto 1 h) ou `Retry-After` do provedor. Falha de uma conexão não bloqueia as
  demais.
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
- `MigrationIT` (T039) valida banco vazio, snapshot da versão anterior e
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
- RPO de 24 h e RTO de 2 h para a operação de referência. Validação completa em
  T175.

## Retenção de dados

Prazos previstos (implementados em T191): `login_attempt` 30 dias, `sync_run` e
`webhook_event` 90 dias, `audit_event` 1 ano, `idempotency_key` 24 h.

## Observabilidade e alertas

- Métricas Micrometer via Actuator (T030), com foco em latência de requisições,
  fila de sincronização, falhas por código e duração de relatórios.
- Alertas recomendados (documentar em T180): falhas repetidas de sincronização,
  fila acumulada, expiração de autorização anual da Pluggy e falha de backup.
- Não há cache de dados financeiros; no máximo metadados curtos de status.

## Recuperação assistida de senha

O MVP não usa e-mail transacional. O procedimento assistido, com verificação de
identidade pelo operador, invalidação de sessões e auditoria, é implementado em
T197 e documentado em `runbooks/password-reset.md` (T193).

## Segurança operacional

- Segredos apenas em `.env` e `secrets/`, ambos fora do Git.
- Acesso ao host restrito ao operador; TLS obrigatório na rede.
- Dependências, imagens e segredos são escaneados no CI a partir de T172.
