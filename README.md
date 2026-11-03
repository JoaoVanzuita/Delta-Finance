# Delta Finance

Controle financeiro privado para uma família, operado em infraestrutura própria
(self-hosted). Cada usuário enxerga apenas as próprias contas, transações,
integrações e relatórios. A integração bancária é configurada individualmente no
Meu Pluggy, sem compartilhamento de credenciais.

> **Estado**: MVP bancário e módulos secundários implementados. O escopo cobre
> identidade, contas e cartões manuais ou via Meu Pluggy, transações, categorias,
> faturas, reconciliação, dashboard, relatórios, investimentos, patrimônio,
> metas, privacidade e exportação. A estabilização inclui guardrails de
> arquitetura, análise estática, orçamento de consultas e contrato OpenAPI.
>
> Nenhum módulo movimenta dinheiro, cria ordens ou oferece recomendação de
> investimento. O produto é um painel de acompanhamento, não um intermediário
> financeiro.

## Stack

| Camada   | Tecnologia                                                                      |
| -------- | ------------------------------------------------------------------------------- |
| Backend  | Java 25 (Temurin), Spring Boot 4.1.2, Spring Security, Spring Data JPA          |
| Banco    | PostgreSQL 18.6, migrações Flyway                                               |
| Frontend | Node.js 24, React 19.3, TypeScript 6.0, Vite 8.3, Tailwind CSS 4.3              |
| Testes   | JUnit 5, Testcontainers 2.0, ArchUnit, Vitest, Testing Library, Playwright, axe |
| Operação | Docker Compose, Caddy 2.11 (TLS no dev; HTTP na release LAN)                    |

## Arquitetura

O backend é um monólito modular. Cada domínio tem suas camadas de API,
aplicação, domínio e infraestrutura, e conversa com os demais por serviços ou
eventos internos. Não há chamada direta de controller para repositório nem
consulta financeira sem escopo de usuário.

```text
Internet ──▶ proxy (Caddy :80/:443) ──▶ client (Caddy :8080, SPA + /api)
                                              └──▶ server (:8080) ──▶ db (:5432)
```

- O diagrama acima é da stack de desenvolvimento.
- Na release, o `proxy` não existe e o `client` publica a porta `8080` na LAN.
- Apenas o `proxy` (dev) ou o `client` (release) publica portas no host.
- `server` e `db` ficam na rede interna `backend`.
- `/api/*` é encaminhado pelo `client` para `server:8080`, na mesma origem.
- Em desenvolvimento, `compose.dev.yaml` publica o banco apenas em
  `127.0.0.1:${DELTA_DB_PORT}` para o backend rodar fora do Compose.
- O projeto Compose de desenvolvimento é `delta-finance-dev`; o de release é
  `delta-finance`.
- O volume de desenvolvimento é `delta-finance-dev_pgdata`; o de produção é
  `delta-finance-prod_pgdata`.

Estrutura principal:

```text
server/                 Backend Spring Boot (monólito modular)
  src/main/java/com/deltafinance/   auth, integrations, accounts, transactions,
                                    invoices, categories, reconciliation,
                                    reporting, investments, wealth, goals,
                                    privacy, security, shared
  src/main/resources/db/migration/  migrações Flyway
  src/test/java/com/deltafinance/   unit, integration, api, architecture, support
client/                 SPA React + TypeScript + Vite
  src/app/              composição, providers e rotas
  src/features/         telas por domínio
  src/lib/              cliente HTTP, contratos, formatação e chaves de consulta
  e2e/                  testes Playwright
deploy/                 Caddyfiles e scripts de backup, restauração e rotação
docs/                   documentação interna de operação, produto e release
specs/001-finance/      especificação, plano e contrato OpenAPI
compose.yaml            stack self-hosted de desenvolvimento (build local)
compose.dev.yaml        override local do banco
compose.release.yaml    stack de release com imagens versionadas do GHCR
VERSION                 versão usada pela tag e pelo workflow de release
```

## Funcionalidades

- **Identidade e privacidade**: cadastro/login, sessão com CSRF, isolamento por
  usuário, reautenticação para operações sensíveis, exportação dos próprios
  dados e exclusão de conta.
- **Contas e transações**: contas manuais, importação via Meu Pluggy, painel de
  cartões separado das contas, faturas, categorias, correções, itens removidos
  na origem e reconciliação.
- **Dashboard e relatórios**: resultado do período, saldo, categorias, série
  temporal, drill-down paginado e exportação.
- **Investimentos**: importação de posições e proventos do Meu Pluggy, dashboard
  de alocação, posições encerradas, custo informado, resultado estimado e
  evolução mensal por snapshots. Não há recomendação de compra, venda ou
  alocação.
- **Patrimônio**: ativos, passivos, patrimônio líquido e evolução mensal por
  snapshots imutáveis. A série começa na ativação e não é reconstruída para
  meses anteriores.
- **Metas**: progresso por alocações explícitas, patrimônio líquido ou redução
  de dívida, com histórico diário paginado capturado sob demanda.

## Pré-requisitos

- Docker Engine e Docker Compose v2.
- Java 25 (Temurin) e Node.js 24 apenas para desenvolvimento local.
- `openssl` para gerar o key ring.
- `age` para cifrar e restaurar backups.

O Maven não precisa estar instalado: use `./server/mvnw`. O backend exige Java 25
porque o Spotless usa google-java-format, que depende de APIs internas do
compilador. O arquivo `server/.mvn/jvm.config` já habilita os `--add-exports`
necessários.

## Início rápido

### 1. Configure o ambiente

```bash
cp .env.example .env
```

Edite o `.env` e troque, no mínimo:

| Variável                   | Uso                                                                |
| -------------------------- | ------------------------------------------------------------------ |
| `DELTA_DOMAIN`             | domínio público servido pelo Caddy                                 |
| `DELTA_PUBLIC_BASE_URL`    | URL pública, com `https://`                                        |
| `DELTA_ACME_EMAIL`         | e-mail para emissão do certificado TLS                             |
| `DELTA_DB_PASSWORD`        | senha do PostgreSQL                                                |
| `DELTA_COOKIE_SECURE`      | mantenha `true` com HTTPS                                          |
| `DELTA_KEYRING_GID`        | grupo do arquivo do key ring no host                               |
| `DELTA_CRYPTO_KEY_FILE`    | caminho do key ring; no container, `/run/secrets/crypto-keys.json` |
| `DELTA_TRUSTED_PROXY_HOPS` | quantidade de proxies confiáveis antes da aplicação                |

Os valores de `.env.example` são placeholders. Nunca versione `.env`, dumps,
backups ou o key ring.

### 2. Gere o key ring

O key ring fica fora do banco e da imagem, montado somente leitura.

```bash
mkdir -p secrets && chmod 700 secrets
umask 077
cat > secrets/crypto-keys.json <<EOF
{
  "data":  [{ "version": 1, "key": "$(openssl rand -base64 32)" }],
  "index": [{ "version": 1, "key": "$(openssl rand -base64 32)" }]
}
EOF
chmod 640 secrets/crypto-keys.json
```

Descubra o grupo do arquivo e ajuste `DELTA_KEYRING_GID` no `.env`:

```bash
stat -c %g secrets/crypto-keys.json
```

**Sem o key ring, os campos cifrados são irrecuperáveis.** Faça backup dele por
um canal separado do backup do banco.

### 3. Suba a stack

```bash
docker compose up -d --build
docker compose ps
```

Todos os serviços devem ficar `healthy`. Verifique a aplicação:

```bash
docker compose exec server curl -fsS http://127.0.0.1:8080/actuator/health/liveness
docker compose logs -f server
```

Para uso apenas em rede local, `DELTA_DOMAIN=localhost` usa a CA interna do
Caddy. Distribua o certificado raiz aos dispositivos ou configure um domínio
público válido. Webhooks da Pluggy exigem HTTPS público; sem webhooks, a
sincronização programada continua funcionando.

## Desenvolvimento local

### Banco

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d db
```

Se `DELTA_DB_PORT` estiver ocupada, ajuste a variável no `.env`. O override do
Compose e o backend local usam a mesma porta.

### Backend

O backend **não lê o `.env` sozinho**. Exporte as variáveis antes de subir:

```bash
cd server
set -a; . ../.env; set +a
DELTA_CRYPTO_KEY_FILE=../secrets/crypto-keys.json \
  ./mvnw spring-boot:run
```

Sem exportar as variáveis, o backend cai nos valores padrão (`delta`/`delta`) e
o PostgreSQL responde `password authentication failed for user "delta"`. O
`DELTA_CRYPTO_KEY_FILE` é sobrescrito porque o `.env` aponta para
`/run/secrets`, caminho existente apenas dentro do container.

O `POSTGRES_PASSWORD` só é aplicado quando o volume `pgdata` é criado. Se a
senha mudou depois do primeiro `up`, realinhe sem apagar dados:

```bash
set -a; . ./.env; set +a
docker compose exec db psql -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" \
  -c "ALTER ROLE $DELTA_DB_USER PASSWORD '$DELTA_DB_PASSWORD'"
```

Alternativa destrutiva, que apaga somente o banco de desenvolvimento
(`delta-finance-dev_pgdata`):

```bash
docker compose down -v
docker compose -f compose.yaml -f compose.dev.yaml up -d db
```

### Frontend

O servidor de desenvolvimento faz proxy de `/api` para `http://localhost:8080`.

```bash
cd client
npm ci
npm run dev
```

Acesse `http://localhost:5173`.

## Banco e migrações

As migrações ficam em `server/src/main/resources/db/migration/`.

- `V001__baseline.sql` é a baseline.
- Migrações novas começam em `V002`; a série atual vai até `V018`.
- Nunca edite uma migração já aplicada em ambiente existente.
- `MigrationIT` valida banco vazio e banco já migrado.
- Os testes de integração usam Testcontainers com PostgreSQL real e são
  ignorados automaticamente quando o Docker não está disponível.

## Qualidade

Backend:

```bash
cd server
./mvnw verify
```

Esse comando compila, roda testes unitários e de integração e aplica Spotless,
Checkstyle, SpotBugs e JaCoCo. A cobertura mínima do domínio é 80%; `Money` e
`AuthenticatedUser` exigem 100%. `ArchUnit` bloqueia dependências indevidas,
consulta sem escopo de usuário e `@Transactional` na borda HTTP. `QueryBudgetIT`
limita a quantidade de consultas do dashboard e das faturas.

Frontend:

```bash
cd client
npm run verify
npm run format:check
```

`npm run verify` encadeia typecheck, lint, testes, build e `openapi:check`.
O contrato fica em `specs/001-finance/contracts/openapi.yaml`; o arquivo
`client/src/lib/api/schema.d.ts` é gerado a partir dele e precisa ser commitado
junto com qualquer alteração de contrato.

Testes ponta a ponta:

```bash
cd client
npx playwright install
npm run test:e2e
```

## Segurança

- Nenhum segredo real é versionado. `.env`, `secrets/`, dumps e backups estão
  no `.gitignore`.
- O key ring fica fora do banco e da imagem, montado somente leitura. Use
  permissão `640` e o grupo configurado em `DELTA_KEYRING_GID`.
- Nomes, descrições e valores financeiros são cifrados em repouso com o mesmo
  key ring, incluindo investimentos, patrimônio e metas.
- Client Secret e API Key da Pluggy nunca chegam ao navegador. A API Key é
  temporária e mantida apenas no servidor.
- O IP usado em rate limit é lido da direita para a esquerda em
  `X-Forwarded-For`; o Caddy de borda sobrescreve o header. Ajuste
  `DELTA_TRUSTED_PROXY_HOPS` se a topologia de proxies mudar.
- Sessão usa cookie `HttpOnly`, CSRF e limite absoluto configurável.
- A exportação não inclui credenciais, tokens ou identificadores externos
  sensíveis.
- Connect Token não faz parte do produto.

## Operação

### Saúde e logs

```bash
docker compose ps
docker compose exec server curl -fsS http://127.0.0.1:8080/actuator/health/liveness
docker compose logs -f server
docker compose logs -f client
# Na stack de desenvolvimento com TLS, inclua também `proxy`.
```

### Backup

O backup é um `pg_dump` comprimido e cifrado com `age`, com retenção
configurável por `DELTA_BACKUP_RETENTION_DAYS`.

```bash
AGE_RECIPIENT=age1... ./deploy/scripts/backup.sh
```

O key ring **não** entra nesse arquivo. Copie `secrets/crypto-keys.json` por um
canal separado e valide o checksum gerado pelo script.

### Restauração

A restauração substitui o conteúdo do banco e exige confirmação explícita.

```bash
AGE_IDENTITY_FILE=~/age-key.txt ./deploy/scripts/restore.sh --latest --yes
```

Valide a restauração em um banco limpo antes de considerar o backup confiável.

### Rotação de chaves

1. Gere a nova chave e adicione-a ao key ring mantendo a antiga.
2. Aponte `DELTA_CRYPTO_ACTIVE_VERSION` para a nova versão.
3. Verifique o backup.
4. Execute `./deploy/scripts/rotate-keys.sh --yes`.
5. Só remova a chave antiga depois de validar as leituras.

A rotação da chave de índice HMAC não é automática: ela exige reindexação
planejada dos campos pesquisáveis.

### Sincronização e retenção

- `DELTA_SYNC_SCHEDULER_ENABLED` liga a sincronização programada.
- A carga inicial usa 12 meses; a janela diária usa 7 dias com sobreposição.
- A reconciliação mensal cobre 12 meses.
- `DELTA_PRIVACY_RETENTION_ENABLED` e `DELTA_PRIVACY_RETENTION_CRON` controlam o
  expurgo de tentativas de login, execuções de sincronização, auditoria e
  chaves de idempotência.

## Release

O release é disparado por uma tag Git `vX.Y.Z`. O workflow
`.github/workflows/release.yml` valida a versão, roda os gates, publica as
imagens `linux/amd64` no GHCR e anexa o kit ao GitHub Releases.

Antes de criar a tag:

```bash
./deploy/scripts/check-version.sh vX.Y.Z
docker compose config --quiet
docker compose --env-file deploy/release/.env.example -f compose.release.yaml config --quiet
cd server && ./mvnw verify
cd ../client && npm run verify && npm run format:check && npm run test:e2e
```

O kit distribuído contém `compose.yaml`, `.env.example`, `README.md`, `VERSION`,
`release.json` e `SHA256SUMS`. O banco de produção usa o volume
`delta-finance-prod_pgdata`, separado do desenvolvimento
(`delta-finance-dev_pgdata`). O procedimento de instalação, atualização,
rollback e backup está em `deploy/release/README.md`. O checklist completo está
em `docs/release-checklist.md`.

Após o primeiro push, torne os pacotes do GHCR públicos (ou documente o login
com PAT `read:packages`) para que a instalação não exija autenticação.

## Limitações conhecidas

- **Histórico patrimonial**: snapshots mensais, sem reconstrução retroativa.
  A captura manual registra apenas a posição do dia; se o job mensal não rodar,
  o mês correspondente fica sem snapshot.
- **Aportes e resgates**: o tratamento confiável cobre as categorias mapeadas do
  provedor e a conta marcada como `investment_account`. Transferências manuais
  para corretora dependem da categoria correta e não existe vínculo explícito
  entre investimento e conta.
- **Custos de investimento**: a conciliação é feita no nível do perímetro, não
  por posição individual.
- **Multi-moeda**: apenas BRL compõe os totais consolidados; outras moedas
  aparecem separadas, sem conversão automática.
- **Provedor bancário**: respostas vazias ou incompletas são tratadas como
  inconclusivas. Casos específicos de conciliação podem exigir correção manual
  ou suporte do provedor.
