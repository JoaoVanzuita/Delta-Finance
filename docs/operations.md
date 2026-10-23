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
| `server` | `/actuator/health/readiness` | banco (e, a partir de T019/T030, key ring) |
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
