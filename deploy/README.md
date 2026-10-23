# Deploy e operação do stack

Este diretório contém a configuração de borda e os scripts operacionais. O
`compose.yaml` da raiz orquestra `db`, `server`, `client` e `proxy`.

## Topologia

```text
Internet ──▶ proxy (Caddy :80/:443, TLS) ──▶ client (Caddy :80, SPA + /api) ──▶ server (:8080) ──▶ db (:5432)
```

- Apenas `proxy` publica portas no host.
- `server` e `db` ficam na rede interna `backend`.
- `/api/*` é encaminhado pelo `client` para `server:8080` (mesma origem, sem CORS
  aberto em produção).

## TLS

O `Caddyfile` emite e renova certificados automaticamente para
`${DELTA_DOMAIN}`. Para uso apenas em rede local, use `DELTA_DOMAIN=localhost`
(CA interna do Caddy) e distribua o certificado raiz aos dispositivos, ou
configure um domínio público válido apontando para o servidor.

Configuração mínima no `.env`:

```dotenv
DELTA_DOMAIN=financas.exemplo.com
DELTA_ACME_EMAIL=voce@exemplo.com
DELTA_PUBLIC_BASE_URL=https://financas.exemplo.com
DELTA_COOKIE_SECURE=true
```

Webhooks da Pluggy exigem HTTPS público. Sem webhooks, a sincronização
programada continua funcionando (é o caminho obrigatório do MVP).

## Key ring (chaves de criptografia)

O key ring é externo ao banco e à imagem, montado em `/run/secrets` como
somente leitura. Formato esperado (a ser carregado por
`shared/crypto/KeyRing.java`, T019):

```json
{
  "data": [{ "version": 1, "key": "<32 bytes em base64>" }],
  "index": [{ "version": 1, "key": "<32 bytes em base64>" }]
}
```

Criação:

```bash
mkdir -p secrets && chmod 700 secrets
umask 077
cat > secrets/crypto-keys.json <<EOF
{
  "data":  [{ "version": ${DELTA_CRYPTO_ACTIVE_VERSION:-1}, "key": "$(openssl rand -base64 32)" }],
  "index": [{ "version": ${DELTA_INDEX_KEY_VERSION:-1}, "key": "$(openssl rand -base64 32)" }]
}
EOF
chmod 640 secrets/crypto-keys.json
```

O container roda como usuário `app` (uid 999) e recebe o grupo do arquivo via
`DELTA_KEYRING_GID` no `.env` (padrão 1000). Descubra o grupo com
`stat -c %g secrets/crypto-keys.json` e ajuste o `.env` se necessário. O arquivo
não deve ser legível por outros usuários do host.

Regras:

- Nunca versione `secrets/`, nunca inclua o key ring em imagens ou logs.
- Faça backup **separado** do banco. Sem o key ring, os campos cifrados são
  irrecuperáveis (não há backdoor).
- A rotação é online e versionada; o runbook entra em T176.

## Scripts

| Script | Uso |
|---|---|
| `scripts/backup.sh` | `pg_dump` comprimido, cifrado com `age`, retenção de 30 dias |
| `scripts/restore.sh` | restauração em banco limpo, com confirmação explícita |

Exemplos:

```bash
AGE_RECIPIENT=age1... ./deploy/scripts/backup.sh
./deploy/scripts/restore.sh --file backups/delta-20261023.dump.age --yes
```

Os scripts usam as variáveis `DELTA_DB_*` do `.env` e executam dentro do
container `db` via `docker compose exec`. Backup/restauração completos, com
teste em banco limpo, são validados em T175.
