# Delta Finance

Controle financeiro privado para uma família, operado em infraestrutura própria
(self-hosted). Cada usuário enxerga apenas as próprias contas, transações,
integrações e relatórios; a integração bancária é configurada individualmente no
Meu Pluggy, sem compartilhamento de credenciais.

> **Estado**: escopo funcional de `specs/001-finance` implementado até a fase
> posterior. O MVP bancário cobre identidade, contas e cartões manuais ou via Meu
> Pluggy, transações, categorias, faturas, reconciliação, dashboard, relatórios,
> privacidade e exportação. Investimentos manuais, patrimônio e metas estão
> disponíveis como módulos secundário e posteriores. Nenhum módulo movimenta
> dinheiro, cria ordens ou oferece recomendação de investimento; a validação de
> release segue [`docs/release-checklist.md`](docs/release-checklist.md).

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
  src/main/java/com/deltafinance/   módulos: auth, integrations, accounts, investments, wealth, goals, ...
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

## Funcionalidades

- **Identidade e privacidade**: cadastro/login, sessão com CSRF, isolamento por
  usuário, exportação dos próprios dados e exclusão de conta.
- **Contas e transações**: contas manuais, importação via Meu Pluggy, cartões,
  faturas, categorias, correções, itens removidos na origem e reconciliação.
- **Dashboard e relatórios**: resultado do período, saldo, categorias, série
  temporal, drill-down paginado e exportação dos próprios dados na área de
  privacidade.
- **Investimentos (fase secundária)**: posições manuais de ações e FIIs, com total
  calculado pela data de referência informada. Não há importação automática nem
  recomendação de compra, venda ou alocação.
- **Patrimônio (pós-MVP)**: ativos, passivos e snapshots datados, sem dupla
  contagem e sem inferir saldos passados quando falta histórico.
- **Metas (pós-MVP)**: progresso por alocações explícitas, patrimônio líquido ou
  redução de dívida, com histórico datado (um snapshot por meta/dia, gravado sob
  demanda). Metas não movimentam dinheiro, não criam ordens e não executam ações
  bancárias.

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
snapshot/restore para testar migrações. A baseline atual é
`V001__baseline.sql`; novas migrações começam em `V002`.

## Segurança

- Nenhum segredo real é versionado. `.env`, `secrets/`, dumps e backups estão no
  `.gitignore`.
- O key ring fica fora do banco e da imagem, montado somente leitura. Use
  permissão `640` e o grupo configurado em `DELTA_KEYRING_GID`. **Sem o key ring,
  os campos cifrados são irrecuperáveis** — faça backup separado.
- Client Secret e API Key da Pluggy nunca chegam ao navegador; a API Key é
  temporária e mantida apenas no servidor.
- Além dos dados bancários, nomes, descrições e valores de investimentos,
  patrimônio e metas são cifrados em repouso com o mesmo key ring.
- Connect Token não faz parte do MVP.
- Detalhes e limites da proteção: [`docs/security.md`](docs/security.md).

## Documentação

- [`docs/funcionalidades.md`](docs/funcionalidades.md): funcionalidades
  implementadas, regras de negócio e limitações atuais.
- [`docs/operations.md`](docs/operations.md): operação, health checks, logs,
  sincronização, investimentos, patrimônio, metas, migrações e backups.
- [`docs/security.md`](docs/security.md): modelo de segurança, criptografia,
  sessão, retenção e exportação.
- [`docs/accessibility.md`](docs/accessibility.md): rotas cobertas e critérios de
  acessibilidade.
- [`docs/release-checklist.md`](docs/release-checklist.md): versões fixadas e
  gates de release.
- [`docs/runbooks/README.md`](docs/runbooks/README.md): índice de runbooks.
- [`docs/analise-integracao-pluggy-multiusuario.md`](docs/analise-integracao-pluggy-multiusuario.md):
  análise técnica da integração multiusuário com o Meu Pluggy.
- [`deploy/README.md`](deploy/README.md): TLS, key ring e scripts operacionais.
- [`specs/001-finance/spec.md`](specs/001-finance/spec.md): requisitos
  funcionais.
- [`specs/001-finance/data-model.md`](specs/001-finance/data-model.md): modelo de
  dados e classificação dos campos.
- [`specs/001-finance/contracts/openapi.yaml`](specs/001-finance/contracts/openapi.yaml):
  contrato REST versionado.
