# Delta Finance

Controle financeiro privado para uma família, operado em infraestrutura própria
(self-hosted). Cada usuário enxerga apenas as próprias contas, transações,
integrações e relatórios; a integração bancária é configurada individualmente no
Meu Pluggy, sem compartilhamento de credenciais.

> **Estado**: fase 4 (integração Meu Pluggy). Cadastro/login, configuração
> pessoal da integração, conexões por itemId, importação de contas, cartões e
> transações estão implementados. Sincronização programada, faturas e dashboard
> ainda não; use apenas dados de teste.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25 (Temurin), Spring Boot 4.1.1, Spring Security, Spring Data JPA |
| Banco | PostgreSQL 18.6, migrações Flyway |
| Frontend | Node.js 24, React 19.3, TypeScript 6.0, Vite 8.3, Tailwind CSS 4.3 |
| Testes | JUnit 5, Testcontainers 2.0, ArchUnit, Vitest, Testing Library, Playwright, axe |
| Operação | Docker Compose, Caddy 2.11 (TLS) |

Versões, justificativas e decisões estão em
[`specs/001-finance/research.md`](specs/001-finance/research.md). O plano técnico
completo está em [`specs/001-finance/plan.md`](specs/001-finance/plan.md).

## Estrutura do repositório

```text
server/                 Backend Spring Boot (monólito modular)
  src/main/java/com/deltafinance/   módulos: auth, integrations, accounts, ...
  src/main/resources/db/migration/  migrações Flyway
  src/test/java/com/deltafinance/   unit, integration, api, architecture, support
client/                 SPA React + TypeScript + Vite
  src/app/              composição, providers e rotas
  src/features/         telas por domínio
  e2e/                  testes Playwright
deploy/                 Caddyfiles, scripts de backup/restauração
docs/                   operação, segurança e runbooks
specs/001-finance/      especificação, plano, contrato OpenAPI e tarefas
compose.yaml            stack self-hosted completa
```

## Pré-requisitos

- Docker Engine 29+ e Docker Compose 2.40+
- Java 25 (Temurin 25.0.4.1+1) e Node.js 24.22.0 apenas para desenvolvimento local
- `openssl` para gerar as chaves do key ring

O Maven não precisa estar instalado: use `./server/mvnw`.

## Início rápido (stack completa)

```bash
cp .env.example .env          # ajuste domínio e senhas locais
mkdir -p secrets              # key ring externo (ver deploy/README.md)
docker compose up -d --build
docker compose ps             # todos os serviços devem ficar healthy
```

O roteiro de validação ponta a ponta (incluindo o gate real da Pluggy) está em
[`specs/001-finance/quickstart.md`](specs/001-finance/quickstart.md).

## Desenvolvimento local

> **Java 25 é obrigatório.** O Spotless usa google-java-format, que depende de
> APIs internas do compilador; o build foi validado com Temurin 25.0.4.1+1 e não
> com JDKs mais novos. O arquivo `server/.mvn/jvm.config` já habilita os
> `--add-exports` necessários.

Backend (PostgreSQL via Compose, publicado apenas em `127.0.0.1`):

```bash
# compose.dev.yaml publica o banco na porta DELTA_DB_PORT (padrão 5432)
docker compose -f compose.yaml -f compose.dev.yaml up -d db

cd server
set -a; . ../.env; set +a    # credenciais do .env
DELTA_CRYPTO_KEY_FILE=../secrets/crypto-keys.json \
  ./mvnw spring-boot:run     # profile local, porta 8080
```

O backend **não lê o `.env` sozinho**: sem exportar as variáveis, ele cai nos
valores padrão (`delta`/`delta`) e o Postgres responde
`password authentication failed for user "delta"`. O `DELTA_CRYPTO_KEY_FILE`
é sobrescrito porque o `.env` aponta para `/run/secrets`, caminho existente
apenas dentro do container.

Se `DELTA_DB_PORT` já estiver ocupada por outro serviço local, ajuste-a no
`.env`: o override do Compose e o backend usam a mesma variável.

O `POSTGRES_PASSWORD` só é aplicado quando o volume `pgdata` é criado. Se você
trocou `DELTA_DB_PASSWORD` depois do primeiro `up`, a senha do volume continua a
antiga e a autenticação falha. Sem perder dados, realinhe a senha:

```bash
set -a; . ./.env; set +a
docker compose exec db psql -U "$DELTA_DB_USER" -d "$DELTA_DB_NAME" \
  -c "ALTER ROLE $DELTA_DB_USER PASSWORD '$DELTA_DB_PASSWORD'"
```

Alternativa destrutiva (apaga o banco de desenvolvimento):
`docker compose down -v && docker compose -f compose.yaml -f compose.dev.yaml up -d db`.

Frontend (proxy de `/api` para `http://localhost:8080`):

```bash
cd client
npm ci
npm run dev                  # http://localhost:5173
```

## Qualidade

```bash
# Backend: compila, formata (Spotless), analisa (Checkstyle), testa (unit + IT)
cd server && ./mvnw verify

# Frontend
cd client
npm run lint
npm run format:check
npm run typecheck
npm run test
npm run build

# E2E (Playwright, stub de API)
npm run test:e2e
```

Os testes de integração do backend usam Testcontainers e são ignorados
automaticamente quando o Docker não está disponível. A base está em
`server/src/test/java/com/deltafinance/support/PostgresTestBase.java` e suporta
`-Dflyway.target=V002`, além de snapshot/restore para testar migrações.

## Segurança

- Nenhum segredo real é versionado. `.env`, `secrets/`, dumps e backups estão no
  `.gitignore`.
- O key ring fica fora do banco e da imagem, montado somente leitura. Use
  permissão `640` e o grupo configurado em `DELTA_KEYRING_GID`. **Sem o key ring,
  os campos cifrados são irrecuperáveis** — faça backup separado.
- Client Secret e API Key da Pluggy nunca chegam ao navegador; a API Key é
  temporária e mantida apenas no servidor.
- Connect Token não faz parte do MVP.
- Detalhes e limites da proteção: [`docs/security.md`](docs/security.md).

## Documentação

- [`docs/operations.md`](docs/operations.md): operação, health checks, logs,
  migrações e backups.
- [`docs/runbooks/README.md`](docs/runbooks/README.md): índice de runbooks.
- [`deploy/README.md`](deploy/README.md): TLS, key ring e scripts operacionais.
- [`specs/001-finance/contracts/openapi.yaml`](specs/001-finance/contracts/openapi.yaml):
  contrato REST versionado.
