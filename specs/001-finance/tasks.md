---
description: "Task list for the Delta Finance MVP implementation"
---

# Tasks: MVP de Finanças Pessoais Familiares

> **Nota de 2026-10-25/26:** registros de webhooks (T074/T085 e testes
> relacionados), de Bills experimentais (T123/T133), da revisão manual
> `RESTORE`/`CONFIRM_REMOVAL` (T192), de snapshots patrimoniais (T157) e da
> redução de dívida nas metas (T165/T167) são históricos. Esses recursos foram
> removidos pelos cortes S-04, S-05, S-08, S-02 e S-03. O comportamento atual
> está no contrato OpenAPI.

**Input**: Design documents from `/specs/001-finance/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/openapi.yaml`, `quickstart.md`

**Tests**: Incluídos porque a especificação e o plano exigem cobertura
automatizada de autenticação, isolamento, importação, deduplicação,
reconciliação, faturas, segurança, ciclo de vida de credenciais, acessibilidade,
migrações, política de senha e semântica de transações pendentes. Testes de cada
história devem ser escritos primeiro e falhar antes da implementação.

**Organization**: Tarefas agrupadas por história de usuário para permitir
implementação e teste independentes. O MVP bancário é composto por US1–US7;
US5 e US6 antecedem US4 para que o dashboard consuma faturas e transferências
já implementadas; US8 é a fase secundária de investimentos; US9–US10 são a fase
posterior de patrimônio e metas. O gate real do MVP (Phase 10) bloqueia
US8–US10.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência de tarefa
  incompleta).
- **[Story]**: história de usuário correspondente (US1–US10).
- Toda tarefa inclui caminho de arquivo explícito.
- A ordem de execução é definida pelas fases; os IDs `T###` são estáveis e podem
  não ser sequenciais após reordenações.

## Path Conventions

- Backend: `server/src/main/java/com/deltafinance/`
- Backend tests: `server/src/test/java/com/deltafinance/`
- Migrações: `server/src/main/resources/db/migration/`
- Frontend: `client/src/`
- E2E: `client/e2e/`
- Deploy: `deploy/`
- Contrato: `specs/001-finance/contracts/openapi.yaml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Criar o monorepo, o stack executável e a base de qualidade.

- [X] T001 Criar o esqueleto do monorepo e o README inicial em `README.md`, com `server/`, `client/`, `deploy/` e `docs/`.
- [X] T002 Inicializar o backend Spring Boot com Java 25 e dependências fixadas em `server/pom.xml`.
- [X] T003 [P] Inicializar o cliente React 19.3, TypeScript 6.0.3 e Vite 8.3.5 em `client/package.json` e `client/vite.config.ts`.
- [X] T004 Configurar formatação, Checkstyle/Spotless e análise estática do backend em `server/pom.xml`.
- [X] T005 [P] Configurar ESLint, Prettier e checagem de tipos no frontend em `client/eslint.config.js`, `client/.prettierrc` e `client/tsconfig.json`.
- [X] T006 [P] Criar `.gitignore`, `.editorconfig` e `.env.example` com placeholders em `.gitignore`, `.editorconfig` e `.env.example`.
- [X] T007 [P] Criar o esqueleto do Compose com `db`, `server`, `client` e `proxy` em `compose.yaml`.
- [X] T008 [P] Criar as configurações Caddy de proxy e do cliente em `deploy/Caddyfile` e `deploy/client.Caddyfile`.
- [X] T009 [P] Criar Dockerfiles multi-stage do backend e do frontend em `server/Dockerfile` e `client/Dockerfile`.
- [X] T010 [P] Criar o pipeline de CI com build, testes e lint em `.github/workflows/ci.yml`.
- [X] T011 [P] Criar o entrypoint Spring Boot e os profiles `local`/`prod` em `server/src/main/java/com/deltafinance/DeltaFinanceApplication.java` e `server/src/main/resources/application.yml`.
- [X] T012 [P] Criar router e providers do frontend em `client/src/app/App.tsx`, `client/src/app/router.tsx` e `client/src/app/providers.tsx`; o layout base fica em T034.
- [X] T013 [P] Criar a base de testes PostgreSQL com Testcontainers, suporte a `flyway.target` e restauração de snapshot em `server/src/test/java/com/deltafinance/support/PostgresTestBase.java`.
- [X] T014 [P] Criar as configurações Vitest, Playwright e axe em `client/vitest.config.ts`, `client/playwright.config.ts` e `client/vitest.setup.ts`.
- [X] T015 [P] Criar o esqueleto de documentação operacional em `docs/operations.md`, `docs/security.md` e `docs/runbooks/README.md`.
- [X] T016 [P] Criar os esqueletos de backup e restauração em `deploy/scripts/backup.sh` e `deploy/scripts/restore.sh`.

**Checkpoint**: o repositório compila, o Compose sobe os serviços vazios e o CI
executa lint/build/testes básicos.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Base de identidade, criptografia, segurança, erros, observabilidade
e contratos que bloqueia todas as histórias.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase.

- [X] T017 Criar a migração de identidade `V001__identity.sql` em `server/src/main/resources/db/migration/V001__identity.sql` com `app_user` (`username_normalized` único, `password_hash` Argon2id, `status` `ACTIVE|DELETION_PENDING|DELETED`), `login_attempt`, `audit_event`, `idempotency_key` e tabelas do Spring Session.
- [X] T018 [P] Implementar `Money`, `Clock`, `CursorPage` e `PageRequest` em `server/src/main/java/com/deltafinance/shared/money/`, `shared/time/` e `shared/pagination/`.
- [X] T019 [P] Implementar o carregador do key ring e as propriedades de criptografia em `server/src/main/java/com/deltafinance/shared/crypto/KeyRing.java` e `CryptoProperties.java`.
- [X] T020 [P] Implementar AES-256-GCM com nonce, tag, AAD e versão de chave em `server/src/main/java/com/deltafinance/shared/crypto/CryptoService.java`; cobrir tamper e rotação em `server/src/test/java/com/deltafinance/unit/crypto/CryptoServiceTest.java`.
- [X] T021 [P] Implementar HMAC-SHA-256 para IDs externos, dedup e fingerprints em `server/src/main/java/com/deltafinance/shared/crypto/IndexHmacService.java` com testes em `server/src/test/java/com/deltafinance/unit/crypto/IndexHmacServiceTest.java`.
- [X] T022 [P] Implementar serialização de `BigDecimal` como string decimal em `server/src/main/java/com/deltafinance/shared/money/MoneyJsonModule.java`.
- [X] T023 Implementar a entidade base com `created_at`, `updated_at`, `version` e auditoria em `server/src/main/java/com/deltafinance/shared/persistence/BaseEntity.java`.
- [X] T024 Implementar sessão, cookies, CSRF, headers e logout no Spring Security em `server/src/main/java/com/deltafinance/security/SecurityConfig.java`.
- [X] T025 Implementar `AppUser`, repositório, `PasswordEncoder` Argon2id calibrado (m=19 MiB, t=2, p=1, salt 16 B, hash 32 B) e `PasswordPolicy` com lista local versionada de senhas comuns (mínimo 10.000 entradas) e rejeição de senha que contenha o nome de usuário normalizado em `server/src/main/java/com/deltafinance/auth/domain/AppUser.java`, `AppUserRepository.java`, `server/src/main/java/com/deltafinance/auth/domain/PasswordPolicy.java` e `server/src/main/java/com/deltafinance/auth/config/PasswordConfig.java`.
- [X] T026 Implementar `AuthService` e `AuthController` para registro, login, logout, sessão atual e troca de senha, validando a política de senha (12–128, lista comum mínima, nome de usuário normalizado) em `server/src/main/java/com/deltafinance/auth/application/AuthService.java` e `auth/api/AuthController.java`.
- [X] T027 Implementar limitação de tentativas (5 falhas/15 min por usuário+IP, bloqueio de 15 min, reset em sucesso) e auditoria de autenticação em `server/src/main/java/com/deltafinance/auth/application/LoginAttemptService.java` e `server/src/main/java/com/deltafinance/shared/audit/AuditService.java`.
- [X] T028 [P] Implementar `ProblemDetail`, códigos de erro e `RequestIdFilter` em `server/src/main/java/com/deltafinance/shared/errors/GlobalExceptionHandler.java` e `RequestIdFilter.java`.
- [X] T029 [P] Configurar logs JSON e mascaramento de dados sensíveis em `server/src/main/resources/logback-spring.xml` e `server/src/main/java/com/deltafinance/shared/logging/SensitiveDataMasker.java`.
- [X] T030 [P] Configurar Actuator, health liveness/readiness e métricas em `server/src/main/resources/application.yml` e `server/src/main/java/com/deltafinance/shared/config/ObservabilityConfig.java`.
- [X] T031 [P] Implementar `AuthenticatedUser` e `OwnershipGuard` para derivação de `user_id` da sessão e verificação de propriedade nas consultas de repositório em `server/src/main/java/com/deltafinance/security/AuthenticatedUser.java` e `OwnershipGuard.java`.
- [X] T032 [P] Implementar o cliente HTTP do frontend, CSRF, `ProblemDetail` e tratamento de 401/403 em `client/src/lib/api/client.ts` e `client/src/lib/api/problem.ts`.
- [X] T033 [P] Executar as skills `frontend-design` e `ui-ux-pro-max` e registrar direção visual, tokens e princípios em `client/docs/design-direction.md`.
- [X] T034 [P] Implementar layout base, navegação e tokens visuais em `client/src/components/layout/AppShell.tsx` e `client/src/styles/tokens.css`.
- [X] T035 [P] Adicionar regras ArchUnit de fronteiras, ausência de ciclos e consultas de repositório que não atravessem `user_id` em `server/src/test/java/com/deltafinance/architecture/ModuleBoundaryTest.java`.
- [X] T036 [P] Adicionar helpers de teste RTL e axe em `client/src/test/render.tsx` e `client/src/test/axe.ts`.
- [X] T037 Adicionar lint do OpenAPI e geração de tipos do cliente em `client/package.json` e `client/src/lib/api/schema.d.ts`.
- [X] T038 [P] Criar fixture de dois usuários e fábrica de dados em `server/src/test/java/com/deltafinance/support/TwoUserFixture.java` e `TestDataFactory.java`.
- [X] T039 Implementar `MigrationIT` e documentar a política expand/contract em `server/src/test/java/com/deltafinance/integration/MigrationIT.java` e `server/src/main/resources/db/migration/README.md`: aplicar todas as migrações em banco vazio; aplicar em snapshot da versão anterior (a partir de V002); validar idempotência, expand/contract e falha de migração impedindo readiness.

**Checkpoint**: fundação pronta; autenticação, CSRF, criptografia, erros e
observabilidade funcionam, e as histórias podem começar.

---

## Phase 3: User Story 1 - Conta pessoal e isolamento entre familiares (Priority: P1)

**Goal**: cadastro, login, logout, troca de senha, sessão segura e isolamento
total entre usuários.

**Independent Test**: criar dois usuários, registrar dados distintos em cada um
e tentar acessar, alterar, excluir, exportar e relatar dados do outro por ID,
URL, filtro ou conteúdo; todas as tentativas devem ser negadas.

### Tests for User Story 1

- [X] T040 [P] [US1] Contract test dos endpoints de registro, login, logout, `me` e senha, incluindo 12/128 caracteres, frase-senha válida, senha comum rejeitada pela lista local versionada de 10.000 entradas, senha contendo o nome de usuário e bloqueio após 5 falhas/15 min, em `server/src/test/java/com/deltafinance/api/AuthApiTest.java`.
- [X] T041 [P] [US1] Integration test de sessão, expiração de 30 minutos de inatividade e 12 horas absolutas, logout, troca de senha e limite de tentativas de login em `server/src/test/java/com/deltafinance/integration/AuthSessionIT.java`.
- [X] T042 [P] [US1] Integration test de acesso cruzado de leitura, escrita e exclusão em recursos de identidade e configurações de conta, com dois usuários e identificadores manipulados, em `server/src/test/java/com/deltafinance/integration/IsolationIT.java`.
- [X] T043 [P] [US1] Testes de formulários, validação da política de senha, erros genéricos e route guards em `client/src/features/auth/__tests__/AuthFlow.test.tsx`.

### Implementation for User Story 1

- [X] T044 [US1] Implementar a página e formulário de registro em `client/src/features/auth/pages/RegisterPage.tsx`.
- [X] T045 [US1] Implementar login, logout, expiração de sessão e mensagens genéricas em `client/src/features/auth/pages/LoginPage.tsx` e `client/src/features/auth/hooks/useSession.ts`.
- [X] T046 [US1] Implementar alteração de senha, aplicando a mesma política, e configurações da conta em `client/src/features/auth/pages/AccountSettingsPage.tsx`.
- [X] T047 [US1] Implementar route guards, tabela de rotas e tratamento de 401/403/404 em `client/src/app/ProtectedRoute.tsx`, `client/src/app/router.tsx` e `client/src/features/auth/components/SessionExpiredBanner.tsx`.
- [X] T048 [US1] Completar auditoria de eventos de autenticação e senha em `server/src/main/java/com/deltafinance/auth/application/AuthAuditService.java`.
- [X] T049 [US1] Adicionar E2E de dois usuários e isolamento em `client/e2e/isolation.spec.ts`.

**Checkpoint**: US1 funcional e testável isoladamente; nenhum dado cruza
usuários.

---

## Phase 4: User Story 2 - Configurar o Meu Pluggy pessoal e importar dados bancários (Priority: P1)

**Goal**: configuração pessoal, credenciais write-only, múltiplos itemIds,
importação de contas, cartões e transações.

**Independent Test**: configurar duas integrações independentes, importar dados
e verificar que cada registro pertence ao usuário e à conexão corretos;
credenciais inválidas não ativam a integração nem revelam segredos.

### Tests for User Story 2

- [X] T050 [P] [US2] Contract test de integração, conexões, contas e transações em `server/src/test/java/com/deltafinance/api/IntegrationApiTest.java`.
- [X] T051 [P] [US2] Integration test com dois stubs Pluggy e múltiplos itemIds em `server/src/test/java/com/deltafinance/integration/PluggyIntegrationSetupIT.java`.
- [X] T052 [P] [US2] Integration test de Client Secret write-only, API Key não exposta, ausência de Connect Token, isolamento do cache de API Key entre dois usuários/integrações e atualização inválida que mantém a configuração anterior em `server/src/test/java/com/deltafinance/integration/SecretHandlingIT.java`.
- [X] T053 [P] [US2] E2E de configuração da integração e validação de credenciais em `client/e2e/integration-setup.spec.ts`.

### Implementation for User Story 2

- [X] T054 [US2] Criar a migração `V002__pluggy_integration.sql` para `pluggy_integration` (único `(user_id, provider)`, ciphertexts, status enum) e `pluggy_connection` (ID local estável, único `(integration_id, external_item_id_hash)`) em `server/src/main/resources/db/migration/V002__pluggy_integration.sql`.
- [X] T055 [P] [US2] Implementar `PluggyIntegration` e repositório em `server/src/main/java/com/deltafinance/integrations/domain/PluggyIntegration.java` e `PluggyIntegrationRepository.java`.
- [X] T056 [P] [US2] Implementar `PluggyConnection` e repositório em `server/src/main/java/com/deltafinance/connections/domain/PluggyConnection.java` e `PluggyConnectionRepository.java`.
- [X] T057 [US2] Criar a migração `V003__accounts_transactions.sql` para `financial_account` (origem `IMPORTED|MANUAL`), `card_profile` (`credit_balance_enc` para crédito acumulado), `transaction` (`dedup_key` único parcial, FKs `(user_id, account_id)`), `external_identity_history` e `category` em `server/src/main/resources/db/migration/V003__accounts_transactions.sql`.
- [X] T058 [P] [US2] Implementar `FinancialAccount` e `CardProfile` com repositórios em `server/src/main/java/com/deltafinance/accounts/domain/FinancialAccount.java`, `CardProfile.java` e repositórios correspondentes.
- [X] T059 [P] [US2] Implementar `Transaction` e `ExternalIdentityHistory` com repositórios em `server/src/main/java/com/deltafinance/transactions/domain/Transaction.java`, `ExternalIdentityHistory.java` e repositórios correspondentes.
- [X] T060 [P] [US2] Implementar `Category` e repositório em `server/src/main/java/com/deltafinance/categories/domain/Category.java` e `CategoryRepository.java`.
- [X] T061 [US2] Implementar `PluggyAuthClient` e cache Caffeine da API Key por `integrationId` (TTL 110 min, máximo 100, valor com `userId`+`integrationId` validado na leitura, invalidação em 401) em `server/src/main/java/com/deltafinance/integrations/infrastructure/PluggyAuthClient.java` e `ApiKeyCache.java`.
- [X] T062 [US2] Implementar `PluggyDataClient` para `GET /items/{id}`, `GET /accounts?itemId` e `GET /v2/transactions` com cursor em `server/src/main/java/com/deltafinance/integrations/infrastructure/PluggyDataClient.java`.
- [X] T063 [US2] Implementar `IntegrationService` para salvar/validar credenciais e estados em `server/src/main/java/com/deltafinance/integrations/application/IntegrationService.java`.
- [X] T064 [US2] Implementar `ConnectionService` para adicionar, validar, atualizar e remover itemIds, preservando histórico ao remover, em `server/src/main/java/com/deltafinance/connections/application/ConnectionService.java`.
- [X] T065 [US2] Implementar a importação de contas e cartões, incluindo `creditData` quando fornecido, em `server/src/main/java/com/deltafinance/accounts/application/AccountImportService.java`.
- [X] T066 [US2] Implementar importação de transações com dedup, fingerprint, histórico de IDs, normalização do sinal assinado, vínculo de reembolso/chargeback e reconciliação `PENDING`→`POSTED` no mesmo registro, sem agregar pendentes, em `server/src/main/java/com/deltafinance/transactions/application/TransactionImportService.java`.
- [X] T067 [US2] Implementar controllers e DTOs de integração, conexões, contas e transações, sem o endpoint de revisão de `SOURCE_REMOVED` (T192), em `server/src/main/java/com/deltafinance/integrations/api/IntegrationController.java`, `server/src/main/java/com/deltafinance/connections/api/ConnectionController.java`, `server/src/main/java/com/deltafinance/accounts/api/AccountController.java` e `server/src/main/java/com/deltafinance/transactions/api/TransactionController.java`.
- [X] T068 [US2] Implementar a configuração da integração no frontend em `client/src/features/integration/pages/IntegrationSetupPage.tsx` e `client/src/features/integration/components/ConnectionForm.tsx`.
- [X] T069 [US2] Implementar as listas de contas, cartões e transações com sinalização de campos ausentes, estimativas e moeda diferente de BRL, nas rotas `/accounts`, `/accounts/:id` e `/transactions`, em `client/src/features/accounts/pages/AccountsPage.tsx` e `client/src/features/transactions/pages/TransactionsPage.tsx`.
- [X] T070 [US2] Implementar comportamento write-only do Client Secret, itemId mascarado, aviso de limite de conexões configurável e datado (atualmente 5) e ausência de campo/instrução de Connect Token em `client/src/features/integration/components/SecretField.tsx` e `ConnectionLimitNotice.tsx`.

**Checkpoint**: US2 funcional; duas integrações importam dados corretamente e
segredos nunca retornam à interface.

---

## Phase 5: User Story 3 - Sincronizar com segurança e recuperar conexões (Priority: P1)

**Goal**: sincronização programada idempotente, estados, recuperação de
autorização expirada, mudança de itemId e webhooks opcionais.

**Independent Test**: simular sucesso, resposta vazia, credencial inválida,
API Key expirada/renovada, autorização expirada, consentimento bancário expirado,
indisponibilidade, rate limit e mudança de itemId; verificar estados,
preservação do histórico e ausência de duplicação.

### Tests for User Story 3

- [X] T071 [P] [US3] Contract + integration test de idempotência e concorrência de jobs em `server/src/test/java/com/deltafinance/integration/SyncJobIT.java`.
- [X] T072 [P] [US3] Integration test de falhas, lista vazia, preservação de histórico, `SOURCE_REMOVED`, `CONFIRM_REMOVAL`, `RESTORE` via `POST /transactions/{id}/source-removal`, remoção de item e `PENDING`→`POSTED` sem duplicação em `server/src/test/java/com/deltafinance/integration/SyncRecoveryIT.java`.
- [X] T073 [P] [US3] Integration test de mudança de itemId e reconciliação em `server/src/test/java/com/deltafinance/integration/ItemIdChangeIT.java`.
- [X] T074 [P] [US3] Teste de webhook duplicado/replay e mapeamento por itemId em `server/src/test/java/com/deltafinance/api/PluggyWebhookTest.java`.
- [X] T194 [P] [US3] Teste de ciclo de vida de credenciais: API Key expirada no meio da sincronização, renovação via `POST /auth` no servidor e atualização do cache por `integrationId` sem expor a chave; expiração anual da autorização da aplicação (`USER_AUTHORIZATION_EXPIRED`) distinta da expiração do consentimento bancário individual (`consentExpiresAt`), com histórico preservado e ação exigida, em `server/src/test/java/com/deltafinance/integration/CredentialLifecycleIT.java`.
- [X] T075 [P] [US3] Teste de estados de sincronização no frontend em `client/src/features/integration/__tests__/SyncStatus.test.tsx`.

### Implementation for User Story 3

- [X] T076 [US3] Criar a migração `V004__sync_webhooks.sql` para `sync_job`, `sync_run`, `webhook_subscription` e `webhook_event` em `server/src/main/resources/db/migration/V004__sync_webhooks.sql`.
- [X] T077 [P] [US3] Implementar `SyncJob`/`SyncRun` e repositórios em `server/src/main/java/com/deltafinance/connections/domain/SyncJob.java`, `SyncRun.java` e repositórios.
- [X] T078 [P] [US3] Implementar `WebhookSubscription`/`WebhookEvent` e repositórios em `server/src/main/java/com/deltafinance/integrations/domain/WebhookSubscription.java`, `WebhookEvent.java` e repositórios.
- [X] T079 [US3] Implementar `SyncJobService` com `FOR UPDATE SKIP LOCKED`, retries, backoff e locks por integração em `server/src/main/java/com/deltafinance/connections/application/SyncJobService.java`.
- [X] T080 [US3] Implementar `PluggySyncService` com janela diária de 7 dias, importação inicial e reconciliação mensal de 12 meses, com janelas configuráveis e documentadas, em `server/src/main/java/com/deltafinance/connections/application/PluggySyncService.java`.
- [X] T081 [US3] Implementar a máquina de estados e o mapeamento de erros (`USER_AUTHORIZATION_EXPIRED`, `LOGIN_ERROR`, `429`, timeout e vazio), distinguindo autorização da aplicação de consentimento bancário independente, em `server/src/main/java/com/deltafinance/connections/application/SyncStateService.java`.
- [X] T082 [US3] Implementar reconciliação de itemId sem perda histórica em `server/src/main/java/com/deltafinance/connections/application/ItemReconciliationService.java`.
- [X] T083 [US3] Implementar `SyncController` para status, histórico e consulta de dados disponíveis em `server/src/main/java/com/deltafinance/connections/api/SyncController.java`.
- [X] T192 [US3] Implementar revisão explícita de transações `SOURCE_REMOVED` via `POST /transactions/{id}/source-removal`, com ações de restaurar/confirmar remoção e histórico preservado, em `server/src/main/java/com/deltafinance/transactions/application/SourceRemovalReviewService.java` e `client/src/features/transactions/components/SourceRemovedReview.tsx`.
- [X] T084 [US3] Implementar o endpoint opcional de webhook com segredo, `eventId` idempotente e resposta 202 em `server/src/main/java/com/deltafinance/integrations/api/PluggyWebhookController.java`.
- [X] T085 [US3] Implementar registro de webhook por integração atrás de feature flag em `server/src/main/java/com/deltafinance/integrations/application/WebhookService.java`.
- [X] T086 [US3] Implementar rate limiter e backoff por endpoint Pluggy em `server/src/main/java/com/deltafinance/integrations/infrastructure/PluggyRateLimiter.java`.
- [X] T087 [US3] Implementar scheduler com jitter e graceful shutdown em `server/src/main/java/com/deltafinance/connections/application/SyncScheduler.java`.
- [X] T088 [US3] Implementar painel de status, lista de conexões e ações no frontend em `client/src/features/integration/components/SyncStatusPanel.tsx` e `ConnectionsList.tsx`, dentro da página única `/settings/integration`.
- [X] T089 [US3] Adicionar métricas, auditoria e logs sanitizados de sincronização em `server/src/main/java/com/deltafinance/connections/application/SyncMetrics.java` e `server/src/main/java/com/deltafinance/shared/audit/AuditService.java`.

**Checkpoint**: US3 funcional; sincronizações repetidas não duplicam, falhas não
apagam histórico e a reconexão preserva dados.

---

## Phase 6: User Story 5 - Gerenciar contas, lançamentos manuais e correções (Priority: P2)

**Goal**: contas manuais, lançamentos, categorias, correções de importadas e
reconciliação assistida de movimentos internos.

**Independent Test**: criar conta manual, lançamentos de todos os tipos,
categorizar e corrigir uma transação importada, confirmar/rejeitar movimentos
internos e verificar persistência, filtros e relatórios.

### Tests for User Story 5

- [X] T104 [P] [US5] Contract + integration test de criação, edição e desativação de contas manuais (tipo imutável, bloqueio de novos lançamentos e histórico preservado), lançamentos, categorias, transferência manual em duas pernas, estorno e reembolso/chargeback vinculado (inclusive parcial) sem impacto em receitas/despesas em `server/src/test/java/com/deltafinance/integration/ManualEntriesIT.java`.
- [X] T105 [P] [US5] Integration test de precedência de correções e auditoria em `server/src/test/java/com/deltafinance/integration/TransactionCorrectionIT.java`.
- [X] T106 [P] [US5] Integration test de sugestão, confirmação e rejeição de movimentos internos, incluindo limiar, tolerância, empate e falso positivo, em `server/src/test/java/com/deltafinance/integration/InternalMatchIT.java`.
- [X] T107 [P] [US5] Testes de formulários de lançamento manual em `client/src/features/transactions/__tests__/ManualEntryForm.test.tsx`.

### Implementation for User Story 5

- [X] T108 [US5] Criar a migração `V005__corrections_matches.sql` para `transaction_correction` e `internal_match` (único `(user_id, kind, debit_transaction_id, credit_transaction_id)`, status `PENDING|CONFIRMED|REJECTED`) em `server/src/main/resources/db/migration/V005__corrections_matches.sql`.
- [X] T109 [P] [US5] Implementar `TransactionCorrection` e repositório em `server/src/main/java/com/deltafinance/transactions/domain/TransactionCorrection.java` e repositório.
- [X] T110 [P] [US5] Implementar `InternalMatch` e repositório em `server/src/main/java/com/deltafinance/reconciliation/domain/InternalMatch.java` e repositório.
- [X] T111 [US5] Implementar `ManualTransactionService` e controller para receitas, despesas, transferências em duas pernas, estornos, reembolsos/chargebacks vinculados e ajustes com motivo, normalizando o sinal assinado, em `server/src/main/java/com/deltafinance/transactions/application/ManualTransactionService.java` e `transactions/api/ManualTransactionController.java`.
- [X] T112 [US5] Implementar `CategoryService` e controller com categorias predefinidas/personalizadas, reatribuição e desativação em `server/src/main/java/com/deltafinance/categories/application/CategoryService.java` e `categories/api/CategoryController.java`.
- [X] T113 [US5] Implementar `ManualAccountService` e controller para criação, edição e desativação de contas manuais, com saldo/data de referência, tipo imutável após a criação, `POST /accounts` e `PATCH /accounts/{id}`, em `server/src/main/java/com/deltafinance/accounts/application/ManualAccountService.java` e `accounts/api/ManualAccountController.java`.
- [X] T114 [US5] Implementar `TransactionCorrectionService` com `edited_fields`, precedência do usuário e valores originais em `server/src/main/java/com/deltafinance/transactions/application/TransactionCorrectionService.java`.
- [X] T115 [US5] Implementar `InternalMatchService` com sugestões determinísticas por valor, data e contas (tolerância `max(R$ 0,01, 0,5% do maior valor absoluto entre os dois movimentos)`, janela ±3/±7 dias, score com pesos 50 para valor, 30 para data e 20 para contas, limiar 80, desempate por menor diferença de data, depois de valor e depois por identificador local, e `criteria_version`) em `server/src/main/java/com/deltafinance/reconciliation/application/InternalMatchService.java`.
- [X] T116 [US5] Implementar confirmação/rejeição, exclusão de movimentos internos dos totais e `internal_match` confirmado para transferência manual em `server/src/main/java/com/deltafinance/reconciliation/application/InternalMatchDecisionService.java`.
- [X] T117 [US5] Implementar `TransactionQueryService` com filtros e paginação por cursor em `server/src/main/java/com/deltafinance/transactions/application/TransactionQueryService.java`.
- [X] T118 [US5] Implementar formulários de conta manual e lançamento manual, incluindo ação de desativar conta com confirmação, em `client/src/features/transactions/pages/ManualEntryPage.tsx` e `client/src/features/accounts/pages/ManualAccountPage.tsx`.
- [X] T119 [US5] Implementar gerenciador de categorias na rota `/categories` e diálogo de correção em `client/src/features/categories/pages/CategoriesPage.tsx` e `client/src/features/transactions/components/CorrectionDialog.tsx`.
- [X] T120 [US5] Implementar lista de reconciliação pendente com confirmar/rejeitar na rota `/reconciliation` em `client/src/features/reconciliation/pages/ReconciliationPage.tsx`.
- [X] T121 [US5] Adicionar E2E de lançamentos manuais e reconciliação em `client/e2e/manual-reconciliation.spec.ts`.

**Checkpoint**: US5 funcional; contas manuais, correções e movimentos internos
funcionam sem duplicar despesas.

---

## Phase 7: User Story 6 - Gerenciar faturas de cartão (Priority: P2)

**Goal**: ciclos, faturas, parcelamento, pagamentos parciais, juros, encargos,
itens manuais e integração opcional com Bills do provedor.

**Independent Test**: configurar cartão, gerar faturas com compras à vista e
parceladas, registrar pagamento parcial e encargos, editar item manual e conferir
totais, status, saldo devedor e ausência de duplicação.

### Tests for User Story 6

- [X] T122 [P] [US6] Contract + integration test de ciclos, configuração de fechamento/vencimento/limite, parcelas, pagamentos, encargos, compra no cartão e pagamento de fatura contabilizados uma única vez, item espelho compondo o total da fatura uma única vez sem duplicar `expenses`, `reporting_effect` nas duas direções, rejeição de item manual sem `reportingEffect`, item espelho com `EXPENSE_DECREASE` que não reduz `expenses` novamente, itens sem transação, limite de `EXPENSE_DECREASE` como o menor entre valor do item, total da fatura antes do item e despesas do cartão no período do relatório (inclusive com crédito em período distinto), rejeição de compra/parcela sem `transactionId` e conversão explícita em transação manual, proibição de cálculo automático de juros/encargos (FR-085), crédito excedente/carry-forward com aplicação à primeira fatura não paga e residual, `creditBalance`/`appliedCredit`, histórico de correções de item, mudança de ciclo sem reescrever faturas fechadas/pagas e transação pós-fechamento no ciclo seguinte em `server/src/test/java/com/deltafinance/integration/InvoiceIT.java`.
- [X] T123 [P] [US6] Integration test de Bills opcionais, conciliação de itens de compra/parcela com transações existentes por provider IDs/fingerprint, rejeição ou conversão explícita de itens órfãos, ausência de dupla contagem, `ReportEntry.origin=IMPORTED` para itens PROVIDER_BILL e não reescrita de fatura paga por Bill do provedor em `server/src/test/java/com/deltafinance/integration/InvoiceProviderIT.java`.
- [X] T124 [P] [US6] Testes de detalhe, item manual e pagamento no frontend em `client/src/features/invoices/__tests__/InvoiceDetail.test.tsx`.
- [X] T196 [P] [US6] Testes de configuração de ciclo, fechamento, vencimento e limite do cartão, incluindo validação de dia 1–31, limite ≥ 0 e estado de cartão sem ciclo, em `client/src/features/invoices/__tests__/CardCycleSettings.test.tsx`.

### Implementation for User Story 6

- [X] T125 [US6] Criar a migração `V006__invoices.sql` para `invoice` (único `(card_account_id, cycle_start)`, status `OPEN|CLOSED|PARTIALLY_PAID|PAID|OVERDUE`, `has_adjustments`/`adjustment_count`, `applied_credit_enc`), `invoice_item` (kind `PURCHASE|INSTALLMENT|INTEREST|FEE|TAX|CREDIT|REFUND|MANUAL_ADJUSTMENT`, `reporting_effect` `EXPENSE_INCREASE|EXPENSE_DECREASE|NONE` obrigatório no request, valor sempre positivo, `PURCHASE`/`INSTALLMENT` exigem `transactionId` no request e `transaction_id` persistido, único parcial `(user_id, transaction_id)` quando ativo), `invoice_payment` (status `PENDING|CONFIRMED|REVERSED`) e `invoice_item_correction` em `server/src/main/resources/db/migration/V006__invoices.sql`.
- [X] T126 [P] [US6] Implementar `Invoice` e repositório em `server/src/main/java/com/deltafinance/invoices/domain/Invoice.java` e repositório.
- [X] T127 [P] [US6] Implementar `InvoiceItem` e repositório em `server/src/main/java/com/deltafinance/invoices/domain/InvoiceItem.java` e repositório.
- [X] T128 [P] [US6] Implementar `InvoicePayment` e repositório em `server/src/main/java/com/deltafinance/invoices/domain/InvoicePayment.java` e repositório.
- [X] T129 [US6] Implementar cálculo de ciclo, fechamento e vencimento por cartão e estado de cartão sem ciclo com transações não alocadas em `server/src/main/java/com/deltafinance/invoices/application/CardCycleService.java`.
- [X] T130 [US6] Implementar geração/associação de faturas por `billId` ou ciclo local, somente com transações `POSTED`, em `server/src/main/java/com/deltafinance/invoices/application/InvoiceGenerationService.java`.
- [X] T131 [US6] Implementar agrupamento de parcelas e correção manual de planos em `server/src/main/java/com/deltafinance/invoices/application/InstallmentService.java`.
- [X] T132 [US6] Implementar pagamentos totais/parciais, juros, encargos e recálculo de saldo, ignorando itens pendentes no total, aplicando `reporting_effect` e a regra de crédito excedente/carry-forward: itens espelho compõem o total uma única vez, `EXPENSE_DECREASE` usa o menor entre valor do item, total da fatura antes do item e despesas do cartão no período do relatório, saldo de crédito por cartão, crédito acumulado de faturas anteriores aplicado à primeira fatura não paga em ordem de vencimento, residual para a fatura seguinte, `appliedCredit` na fatura e `creditBalance` no cartão, sem reabrir faturas pagas, em `server/src/main/java/com/deltafinance/invoices/application/InvoiceBalanceService.java`.
- [X] T133 [US6] Implementar importação opcional de Bills do provedor, reconciliando itens de compra/parcela com transações existentes por provider IDs/fingerprint e definindo `reporting_effect` coerente com o tipo; itens sem correspondência DEVEM ser rejeitados ou convertidos em transação por ação explícita, nunca persistidos como itens órfãos, em `server/src/main/java/com/deltafinance/invoices/application/ProviderBillImportService.java`.
- [X] T134 [US6] Implementar controllers e DTOs de faturas, itens e pagamentos, incluindo `creditBalance`/`appliedCredit` e `GET /invoice-items/{id}/corrections`, em `server/src/main/java/com/deltafinance/invoices/api/InvoiceController.java`.
- [X] T135 [US6] Implementar lista e detalhe de faturas nas rotas `/cards/:accountId/invoices` e `/invoices/:id`, incluindo transações não alocadas de cartão sem ciclo, em `client/src/features/invoices/pages/InvoicesPage.tsx` e `InvoiceDetailPage.tsx`.
- [X] T195 [US6] Implementar configuração de fechamento, vencimento e limite do cartão em `client/src/features/invoices/components/CardCycleSettings.tsx`, consumindo `PUT /cards/{accountId}/profile`, com validação de dia 1–31, limite ≥ 0 e estado para cartão sem ciclo.
- [X] T136 [US6] Implementar editor de item manual com escolha obrigatória de `reportingEffect` e vínculo de transação para compra/parcela, histórico em `invoice_item_correction` consumindo `/invoice-items/{id}/corrections` e diálogo de pagamento em `client/src/features/invoices/components/InvoiceItemDialog.tsx` e `InvoicePaymentDialog.tsx`.
- [X] T137 [US6] Implementar status, atraso, progresso de pagamento parcial, indicador de ajustes (`hasAdjustments`/`adjustmentCount`) e exibição de `creditBalance`/`appliedCredit` em `client/src/features/invoices/components/InvoiceStatus.tsx`.
- [X] T138 [US6] Adicionar E2E do fluxo de faturas em `client/e2e/invoices.spec.ts`.

**Checkpoint**: US6 funcional; faturas locais funcionam mesmo sem Bills e não
duplicam compras.

---

## Phase 8: User Story 4 - Visualizar dashboard e relatórios consolidados (Priority: P2)

**Goal**: dashboard com resultado do período, saldo atual consolidado, filtros,
distribuição por categoria, série temporal e drill-down.

**Independent Test**: montar um conjunto conhecido de transações, aplicar
filtros e conferir totais, gráficos e listas; os totais devem respeitar as regras
financeiras e corresponder exatamente às transações filtradas.

### Tests for User Story 4

- [X] T090 [P] [US4] Contract + integration test do dashboard e `/reports/entries`: schema, paginação, filtros, 422 para intervalo amplo, `pendingCount`/`pendingIncome`/`pendingExpenses` sem total líquido, `lastSuccessfulSyncAt` conforme o filtro, `cardOutstandingBalance`, `ReportEntry.origin`, drill-down e isolamento de relatórios entre dois usuários, em `server/src/test/java/com/deltafinance/integration/DashboardIT.java`.
- [X] T091 [P] [US4] Integration test de composição financeira do dashboard: resultado do período, saldo atual apenas `BANK`/`CASH`/`WALLET` com status `ACTIVE`, exclusão de `INACTIVE`/`CLOSED`/`UNAVAILABLE` do saldo atual com preservação do histórico, cartões em passivo separado, compra no cartão e pagamento de fatura sem dupla contagem, soma das categorias igual a `expenses`, soma de `/reports/entries` igual ao total, total da fatura incluindo itens espelho uma única vez sem duplicá-los em `expenses`, exclusão de `PENDING` e ausência de rótulo/total de fluxo de caixa sobre `currentBalance`/`periodResult`, em `server/src/test/java/com/deltafinance/integration/FinancialRulesIT.java`.
- [X] T092 [P] [US4] Testes de componentes, filtros e gráficos em `client/src/features/dashboard/__tests__/Dashboard.test.tsx`.

### Implementation for User Story 4

- [X] T093 [US4] Implementar `ReportingService` com filtros por período, conta, cartão, categoria, tipo e origem, períodos calendário em `America/Sao_Paulo`, `currentBalance` só de `BANK`/`CASH`/`WALLET` com status `ACTIVE` (excluindo `INACTIVE`/`CLOSED`/`UNAVAILABLE` do saldo atual e preservando histórico), um único total de `expenses` como gasto efetivo filtrando apenas `POSTED`/`RECONCILED` (nunca `PENDING`), sem total separado de fluxo de caixa e sem rotular `currentBalance`/`periodResult` como fluxo de caixa, itens de fatura sem transação conforme FR-077, projeção `ReportEntry` com `origin` (`IMPORTED`/`MANUAL`, normalizando `PROVIDER_BILL` para `IMPORTED`) e exclusão de moeda diferente de BRL dos totais consolidados, em `server/src/main/java/com/deltafinance/reporting/application/ReportingService.java`.
- [X] T094 [US4] Implementar `MoneyAggregator` para descriptografar, filtrar faixa de valor e agregar com `BigDecimal` em `server/src/main/java/com/deltafinance/reporting/application/MoneyAggregator.java`.
- [X] T095 [US4] Implementar `DashboardController` para resumo com `pendingCount`/`pendingIncome`/`pendingExpenses`, `pendingInternalMatches`, categorias, série temporal, `currentBalance`, `cardOutstandingBalance`, `lastSuccessfulSyncAt` conforme as conexões do filtro e `GET /reports/entries` em `server/src/main/java/com/deltafinance/reporting/api/DashboardController.java`.
- [X] T096 [US4] Implementar `DrillDownService` para retornar as entradas `TRANSACTION` e `INVOICE_ITEM` sem transação que compõem cada total, com `origin` (`IMPORTED`/`MANUAL`), usando a mesma projeção do `ReportingService`, em `server/src/main/java/com/deltafinance/reporting/application/DrillDownService.java`.
- [X] T097 [US4] Implementar a página e o layout do dashboard em `client/src/features/dashboard/pages/DashboardPage.tsx`.
- [X] T098 [US4] Implementar filtros do dashboard e da distribuição de despesas em `client/src/features/dashboard/components/DashboardFilters.tsx`.
- [X] T099 [US4] Implementar gráficos de despesas por categoria e evolução temporal, com alternativa textual e drill-down, em `client/src/features/dashboard/components/CategoryChart.tsx` e `TimeSeriesChart.tsx`.
- [X] T100 [US4] Implementar tabelas/cards responsivos em `client/src/features/dashboard/components/DashboardTable.tsx`.
- [X] T101 [US4] Implementar estados de carregamento, vazio, erro, saldo desatualizado, dados ausentes/estimados, “sem sincronização no período”, bloco de transações pendentes com `pendingCount`/`pendingIncome`/`pendingExpenses` separados e sem total líquido, e pendências de movimentos internos em `client/src/features/dashboard/components/DashboardStates.tsx`.
- [X] T102 [US4] Adicionar rótulos, teclado, foco e `aria-live` ao dashboard em `client/src/features/dashboard/components/DashboardAccessibility.tsx`.
- [X] T103 [US4] Adicionar E2E do dashboard, do drill-down e do fluxo de transação pendente que entra nos totais uma única vez após efetivação em `client/e2e/dashboard.spec.ts`.

**Checkpoint**: US4 funcional; resultado do período e saldo atual são distintos e
os totais batem com o drill-down.

---

## Phase 9: User Story 7 - Controlar conta, exportar dados e encerrar vínculos (Priority: P2)

**Goal**: exportação, desconexão, exclusão de conta, retenção e auditoria.

**Independent Test**: executar cada ação com dois usuários; exportação contém
apenas dados do solicitante, desconexão interrompe sync e exclusão remove acesso
sem afetar o outro usuário.

### Tests for User Story 7

- [X] T139 [P] [US7] Contract + integration test de exportação sem segredos e sem dados de outro usuário, incluindo evento de auditoria sanitizado, em `server/src/test/java/com/deltafinance/integration/ExportIT.java`.
- [X] T140 [P] [US7] Integration test de desconexão, exclusão, revogação de sessões, remoção de dados ativos em até 24 horas e eventos de auditoria sanitizados, em `server/src/test/java/com/deltafinance/integration/AccountLifecycleIT.java`.
- [X] T141 [P] [US7] Teste de configurações de privacidade e confirmações no frontend em `client/src/features/privacy/__tests__/PrivacySettings.test.tsx`.

### Implementation for User Story 7

- [X] T142 [US7] Implementar exportação em streaming ZIP/JSON Lines + CSV e auditoria sanitizada de exportação em `server/src/main/java/com/deltafinance/privacy/application/ExportService.java` e `privacy/api/PrivacyController.java`.
- [X] T143 [US7] Implementar desconexão de integração com preservação ou exclusão explícita e auditoria sanitizada de desconexão em `server/src/main/java/com/deltafinance/privacy/application/DisconnectService.java`.
- [X] T144 [US7] Implementar exclusão de conta, revogação de sessões, cancelamento de jobs, tombstone e auditoria sanitizada de exclusão em `server/src/main/java/com/deltafinance/privacy/application/AccountDeletionService.java`.
- [X] T145 [US7] Implementar configurações de privacidade, exportação, desconexão e exclusão na rota `/settings/privacy` em `client/src/features/privacy/pages/PrivacySettingsPage.tsx`.
- [X] T146 [US7] Adicionar E2E de exportação, desconexão e exclusão em `client/e2e/privacy.spec.ts`.
- [X] T191 [US7] Implementar job diário de retenção para `login_attempt` (30 dias), `sync_run` e `webhook_event` (90 dias), `audit_event` (1 ano) e `idempotency_key` (24 h), com testes que comprovem expurgo dos expirados, preservação dos não expirados e contagens esperadas, em `server/src/main/java/com/deltafinance/privacy/application/RetentionJob.java`.
- [X] T147 [US7] Documentar retenção, exportação e exclusão em `docs/security.md` e `docs/runbooks/deletion.md`.

**Checkpoint**: US7 funcional; privacidade, retenção e ciclo de vida testados com
dois usuários.

---

## Phase 10: MVP Hardening, Gate Real e Release

**Purpose**: hardening, segurança, performance, acessibilidade, operação e
aceitação real do MVP bancário.

**⚠️ GATE**: nenhuma tarefa de US8, US9 ou US10 pode iniciar antes de T182 ser
aprovado e de T171–T189, T193 e T197–T199 estarem concluídas.

**Status do gate em 2026-10-24**: cumprido — T182 validado e T171–T189, T193 e
T197–T199 concluídas. US9 e US10 estão liberadas, mas não foram iniciadas.

- [X] T171 [P] Revisar headers, cookies, CORS, CSRF e rate limits em `server/src/main/java/com/deltafinance/security/SecurityConfig.java` e `docs/security.md`.
- [X] T172 [P] Adicionar secret scanning, dependency scanning e container scanning em `.github/workflows/security.yml`.
- [X] T173 [P] Executar OWASP ZAP baseline e registrar resultados em `docs/security.md` e `deploy/scripts/zap-baseline.sh`.
- [X] T174 [P] Criar e executar testes k6 do dashboard e filtros, incluindo viradas de semana, mês e ano em `America/Sao_Paulo`, em `server/perf/dashboard.js` e `docs/operations.md`.
- [X] T198 Criar e executar testes de performance para login/logout (p95 ≤ 1 s), CRUD/listagens (p95 ≤ 500 ms) e sincronização de uma conexão (≤ 60 s); dashboard e filtros permanecem em T174, em `server/perf/auth-crud-sync.js` e `docs/operations.md`.
- [X] T175 [P] Validar backup/restauração em banco limpo, incluindo expiração de backup em 30 dias e teste de dump do PostgreSQL sem key ring que comprove ilegibilidade dos campos protegidos, e documentar em `deploy/scripts/backup.sh`, `deploy/scripts/restore.sh` e `docs/runbooks/backup.md`.
- [X] T176 [P] Executar drill de rotação de chave e documentar em `server/src/main/java/com/deltafinance/shared/crypto/KeyRotationJob.java` e `docs/runbooks/key-rotation.md`.
- [X] T177 [P] Executar auditoria de acessibilidade com axe, teclado e leitor de tela em `client/e2e/a11y.spec.ts` e `docs/accessibility.md`. **Dispensa registrada em 2026-10-24**: axe, teclado, landmarks e foco automatizados mantidos; verificação manual com leitor de tela dispensada pelo proprietário.
- [X] T178 [P] Executar auditoria responsiva em 360 px e zoom 200% em `client/e2e/responsive.spec.ts`.
- [X] T179 [P] Adicionar ao CI o gate de lint do OpenAPI, geração de tipos e execução de `MigrationIT` em `client/package.json` e `.github/workflows/ci.yml`.
- [X] T199 Configurar JaCoCo e gate de cobertura mínima de 80% de linhas no domínio, com 100% dos caminhos críticos de autorização e dinheiro cobertos por testes dedicados, em `server/pom.xml` e `.github/workflows/ci.yml`.
- [X] T180 Atualizar documentação operacional, segurança e runbooks, incluindo a política de senha, a classificação de campos pesquisáveis (FR-100) e a revisão de textos em pt-BR, em `docs/operations.md`, `docs/security.md` e `docs/runbooks/README.md`.
- [X] T197 Implementar o mecanismo operacional executável de recuperação assistida de senha, com verificação de identidade pelo operador, invalidação de sessões, registro em auditoria e teste de isolamento entre usuários, em `server/src/main/java/com/deltafinance/auth/application/AssistedPasswordResetService.java`, `deploy/scripts/reset-password.sh` e `server/src/test/java/com/deltafinance/integration/PasswordResetIT.java`.
- [X] T193 Criar runbook de recuperação assistida de senha, conforme FR-139, descrevendo o mecanismo de T197 e sem exposição de dados de outro usuário, incluindo walkthrough com conta de teste, registro em auditoria e confirmação de isolamento, em `docs/runbooks/password-reset.md`.
- [X] T181 [P] Executar o roteiro de `quickstart.md` em ambiente limpo, incluindo o protocolo SC-001, cadastro e primeiro login de 5 usuários em menos de 2 minutos e lançamento manual em menos de 90 segundos, e registrar resultados em `specs/001-finance/quickstart.md`. **Validado em 2026-10-24**: roteiro técnico e protocolo SC-001 com 5 pessoas executados e aprovados pelo proprietário; resultados registrados em `specs/001-finance/quickstart.md`.
- [X] T182 Executar o gate real com duas aplicações Meu Pluggy independentes e múltiplos itemIds, verificando que a navegação e as telas do MVP não anunciam investimentos, patrimônio ou metas como funcionalidades disponíveis, em `docs/runbooks/pluggy-acceptance.md`. **Validado em 2026-10-24**: gate real executado e aprovado com duas aplicações Meu Pluggy independentes e múltiplos itemIds; critérios e evidências sanitizadas registrados em `docs/runbooks/pluggy-acceptance.md`.
- [X] T183 [P] Executar a matriz final de acesso cruzado com dois usuários e duas integrações independentes, cobrindo leitura, escrita, exclusão, exportação, relatórios e `RESTORE`/`CONFIRM_REMOVAL`, em `server/src/test/java/com/deltafinance/integration/FinalIsolationIT.java`.
- [X] T184 [P] Executar regressão final de regras financeiras, incluindo sinal assinado, `reporting_effect`, limite de reembolsos/chargebacks, carry-forward, reembolsos parciais, transferência manual em duas pernas e estornos, em `server/src/test/java/com/deltafinance/integration/FinalFinancialRulesIT.java`.
- [X] T185 [P] Executar consistência final de faturas e parcelas em `server/src/test/java/com/deltafinance/integration/FinalInvoiceIT.java`.
- [X] T186 [P] Executar reconciliação final de dashboard e drill-down via `/reports/entries`, incluindo itens de fatura sem transação, em `server/src/test/java/com/deltafinance/integration/FinalDashboardIT.java`.
- [X] T187 [P] Executar drill de atualização e rollback em `docs/runbooks/update-rollback.md`.
- [X] T188 [P] Criar checklist de release e fixar versões/digests em `docs/release-checklist.md`.
- [X] T189 [P] Remover stubs e dados de teste da configuração de produção em `server/src/main/resources/application-prod.yml` e `client/.env.production`.


**Gate manual validado em 2026-10-24**: T182 executado e aprovado com duas
aplicações Meu Pluggy independentes e múltiplos itemIds; critérios e evidências
sanitizadas em `docs/runbooks/pluggy-acceptance.md`. A Phase 11 (US8) foi
implementada antes desta validação por decisão do proprietário e entregou o
registro manual. Em 2026-10-27 a importação de investimentos foi validada para
XP via Meu Pluggy; a Phase 11A amplia o módulo sem reabrir o gate bancário.

**Checkpoint**: MVP bancário validado com dados reais, backup/restauração,
segurança, performance e acessibilidade; fases secundárias separadas.

---

## Phase 11: User Story 8 - Acompanhar investimentos (base manual) (Priority: P3 - fase secundária)

**Goal**: base histórica do módulo. As posições manuais foram removidas em
2026-10-28; o módulo passou a usar apenas a importação automática da Phase 11A.

**Independent Test**: cadastrar, editar e excluir posições, conferir totais e
data de referência e verificar que nenhuma recomendação é exibida.

### Tests for User Story 8

- [X] T148 [P] [US8] Contract + integration test de CRUD, totais e isolamento de posições em `server/src/test/java/com/deltafinance/integration/InvestmentIT.java`.
- [X] T149 [P] [US8] Teste de formulário, lista de posições e ausência de recomendação de compra/venda/alocação em `client/src/features/investments/__tests__/InvestmentPositions.test.tsx` e `client/src/features/investments/__tests__/NoAdvice.test.tsx`.

### Implementation for User Story 8

- [X] T150 [US8] Criar a migração `V007__investments.sql` para `investment_position` (broker `XP|SANTANDER|OTHER`, ativo/ticker/quantidade/preço cifrados, `reference_date` e `origin=MANUAL`) em `server/src/main/resources/db/migration/V007__investments.sql`.
- [X] T151 [US8] Implementar entidade, repositório, serviço e controller de posições manuais, com total `quantidade × preço` e derivação do preço quando apenas o total for informado, em `server/src/main/java/com/deltafinance/investments/domain/InvestmentPosition.java`, `server/src/main/java/com/deltafinance/investments/application/InvestmentPositionService.java` e `server/src/main/java/com/deltafinance/investments/api/InvestmentController.java`.
- [X] T152 [US8] Implementar lista e formulário de posições, exibindo total calculado e data de referência, em `client/src/features/investments/pages/InvestmentsPage.tsx`.
- [X] T153 [US8] Implementar aviso explícito de ausência de importação automática validada e ausência de recomendação de compra/venda/alocação, ranking ou linguagem de aconselhamento em `client/src/features/investments/components/AutoImportNotice.tsx`.
- [X] T154 [US8] Adicionar E2E de investimentos manuais em `client/e2e/investments.spec.ts`.

**Checkpoint**: US8 funcional e explicitamente secundária; não bloqueia o MVP
bancário.

---

## Phase 11A: Importação de investimentos (pós-validação XP/Meu Pluggy)

**Goal**: importar posições de itens com recursos de investimento, exibir
dashboard de alocação e valor atual, diagnosticar cobertura por conexão e
complementar proventos/rendimento com dados manuais identificados.

**Independent Test**: sincronizar um item com recursos `VARIABLE_INCOME`,
conferir posições ativas e encerradas, cobertura, provento `INTEREST` e provento
manual, custo informado, ausência de recomendação e falha isolada de
investimentos sem afetar contas/transações.

### Documentação e contrato

- [X] T200 [US8] Atualizar spec, plano, data-model, research, runbook de aceitação e documentação operacional para a importação de investimentos validada em 2026-10-27, incluindo cobertura por `resources`, limites de Open Finance, proventos/custo manual e ausência de snapshots históricos, em `specs/001-finance/*` e `docs/*`.
- [X] T201 [US8] Atualizar `contracts/openapi.yaml` e regenerar `client/src/lib/api/schema.d.ts` com overview, holdings, cobertura e proventos do provedor; validar `openapi:check`.

### Testes

- [X] T202 [P] [US8] Testes de importação, dedup, posição encerrada, indisponível, `INTEREST`, cobertura `AVAILABLE/NOT_SHARED/PENDING/UNKNOWN/ERROR` e falha isolada em `server/src/test/java/com/deltafinance/integration/InvestmentImportIT.java`.
- [X] T203 [P] [US8] Testes de dashboard, estados vazios, cobertura, proventos do provedor, duplicidade e ausência de recomendação em `client/src/features/investments/__tests__/`.
- [X] T204 [P] [US8] Fixtures de investimentos, recursos e transações no `PluggyStub` em `server/src/test/java/com/deltafinance/support/PluggyStub.java`.

### Implementação

- [X] T205 [US8] Criar migração `V006__provider_investments.sql` com `provider_investment`, `provider_investment_transaction` e `provider_investment_sync`, incluindo cifragem, HMAC, FKs e índices.
- [X] T206 [US8] Estender `PluggyModels`/`PluggyDataClient` com investimentos, transações, recursos do item e buckets de rate limit.
- [X] T207 [US8] Implementar entidades, repositórios e `InvestmentImportService`, com normalização de tipo/status, upsert idempotente, marcação de indisponibilidade e auditoria.
- [X] T208 [US8] Integrar a importação ao `PluggySyncService` sem bloquear contas/transações; ajustar status de sincronização e registrar cobertura por conexão.
- [X] T209 [US8] Implementar API de overview, holdings, cobertura e proventos do provedor.
- [X] T210 [US8] Redesenhar `/investments` como dashboard, incluindo resumo, alocação, classificação por tipo/código, posições ativas/encerradas, proventos do provedor, cobertura e posições manuais.
- [X] T211 [US8] Incluir posições importadas ativas no patrimônio, com aviso de duplicidade e opção de excluir a manual do total.
- [X] T212 [US8] Atualizar exportação, rotação de chaves, exclusão de conta, limpeza de testes e documentação de privacidade para as novas tabelas/campos.
- [X] T213 [US8] Adicionar E2E e acessibilidade de investimentos importados em `client/e2e/investments.spec.ts` e `client/e2e/a11y.spec.ts`.
- [X] T214 [US8] Criar migração `V007__investment_income.sql` e implementar provento manual, custo informado e classificação informada na fase de proventos.
- [X] T215 [US8] Remover a seção de cobertura da tela de investimentos e eliminar o cadastro, API, patrimônio e alocação de metas baseados em posições manuais; manter apenas posições importadas. Migração `V009__remove_manual_investment_positions.sql`.

**Checkpoint**: importação de investimentos funcional, isolada por usuário e
conexão, sem recomendação e sem bloquear o núcleo bancário.

---

## Phase 12: User Story 9 - Acompanhar patrimônio consolidado (Priority: P4 - pós-MVP)

**Goal**: ativos, passivos, patrimônio líquido e visão atual, sem snapshots
datados.

**Independent Test**: combinar saldos, cartões, investimentos e itens manuais e
conferir cálculos, datas, lacunas e ausência de dupla contagem.

### Tests for User Story 9

- [X] T155 [P] [US9] Estender `specs/001-finance/contracts/openapi.yaml` com os endpoints de patrimônio e escrever contract + integration test de patrimônio líquido, snapshots e dupla contagem em `server/src/test/java/com/deltafinance/integration/WealthIT.java`.
- [X] T156 [P] [US9] Teste de visão patrimonial, lacunas e histórico insuficiente sem inferência de saldos passados em `client/src/features/wealth/__tests__/WealthOverview.test.tsx`.

### Implementation for User Story 9

- [X] T157 [US9] Criar a migração `V008__wealth.sql` para `manual_asset_liability` (kind `ASSET|LIABILITY`), `wealth_snapshot` (único `(user_id, reference_date)`, completeness `COMPLETE|PARTIAL|ESTIMATED`) e `wealth_snapshot_item` em `server/src/main/resources/db/migration/V008__wealth.sql`.
- [X] T158 [US9] Implementar entidades e repositórios de patrimônio em `server/src/main/java/com/deltafinance/wealth/domain/ManualAssetLiability.java`, `server/src/main/java/com/deltafinance/wealth/domain/WealthSnapshot.java` e `server/src/main/java/com/deltafinance/wealth/domain/WealthSnapshotItem.java`.
- [X] T159 [US9] Implementar `WealthService` para ativos, passivos, patrimônio líquido e snapshots em `server/src/main/java/com/deltafinance/wealth/application/WealthService.java`.
- [X] T160 [US9] Implementar controllers e DTOs de patrimônio em `server/src/main/java/com/deltafinance/wealth/api/WealthController.java`.
- [X] T161 [US9] Implementar a visão patrimonial com data de referência, lacunas e estado de histórico insuficiente que não infere saldos passados em `client/src/features/wealth/pages/WealthPage.tsx`.
- [X] T162 [US9] Adicionar E2E de patrimônio em `client/e2e/wealth.spec.ts`.

**Checkpoint**: US9 funcional, sem ser pré-requisito do MVP bancário.

---

## Phase 13: User Story 10 - Planejar metas financeiras (Priority: P4 - pós-MVP)

**Goal**: metas com valor-alvo, prazo opcional, regra de progresso, alocações e
histórico.

**Independent Test**: criar metas com diferentes origens de progresso, atualizar
dados de origem e verificar cálculo, histórico, alocação explícita, estados e
ausência de movimentação financeira.

### Tests for User Story 10

- [X] T163 [US10] Estender `specs/001-finance/contracts/openapi.yaml` com endpoints de metas, após a consolidação de T155, e escrever contract + integration test de progresso, `allocation_mode`, alocação fixa/percentual, resíduo, saldo não alocado, excesso sobre a meta, estados e teste negativo de que metas não movimentam dinheiro nem criam ordens em `server/src/test/java/com/deltafinance/integration/GoalsIT.java`.
- [X] T164 [P] [US10] Teste de formulário e progresso de metas, incluindo `allocation_mode`, percentual, resíduo, saldo não alocado, excesso sobre a meta, dupla contagem e ausência de CTA de transferência, compra, investimento ou ordem, em `client/src/features/goals/__tests__/GoalProgress.test.tsx`.

### Implementation for User Story 10

- [X] T165 [US10] Criar a migração `V009__goals.sql` para `financial_goal` (type, status, `progress_rule`, `initial_value_enc` obrigatório para `DEBT_REDUCTION` e `changed_at`), `goal_allocation` (alocação explícita e vigência) e `goal_progress_snapshot` (único `(goal_id, reference_date)`) em `server/src/main/resources/db/migration/V009__goals.sql`.
- [X] T166 [US10] Implementar entidades e repositórios de metas em `server/src/main/java/com/deltafinance/goals/domain/FinancialGoal.java`, `server/src/main/java/com/deltafinance/goals/domain/GoalAllocation.java` e `server/src/main/java/com/deltafinance/goals/domain/GoalProgressSnapshot.java`.
- [X] T167 [US10] Implementar regras de progresso, `allocation_mode` (`FIXED_AMOUNT`/`PERCENTAGE`), alocação explícita, arredondamento/resíduo na última alocação percentual, saldo não alocado, excesso sobre a meta com progresso limitado a 100%, recálculo, histórico, progresso de redução de dívida, validação de `initial_value_enc` e critérios dos estados em `server/src/main/java/com/deltafinance/goals/application/GoalProgressService.java`.
- [X] T168 [US10] Implementar controllers e DTOs de metas em `server/src/main/java/com/deltafinance/goals/api/GoalController.java`.
- [X] T169 [US10] Implementar lista, detalhe e formulário de metas, cobrindo os tipos mínimos, os estados `ACTIVE`/`ACHIEVED`/`PAUSED`/`ARCHIVED`/`OVERDUE`/`CHANGED`, o saldo não alocado, o excesso sobre a meta e o histórico de alterações, em `client/src/features/goals/pages/GoalsPage.tsx`.
- [X] T170 [US10] Adicionar E2E de metas em `client/e2e/goals.spec.ts`.

**Checkpoint**: US10 funcional, sem movimentar dinheiro e sem ser pré-requisito
do MVP.

---

## Phase 14: Documentação Final Pós-MVP

- [X] T190 [P] Atualizar README e documentação final, incluindo investimentos
  manuais, patrimônio e metas, em `README.md` e `docs/operations.md`.

**Checkpoint**: documentação reflete o estado final após as fases secundária e
posterior.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências; começa imediatamente.
- **Foundational (Phase 2)**: depende do Setup; bloqueia todas as histórias.
- **US1–US7 (MVP bancário)**: dependem da Fundação.
- **MVP Hardening e gate (Phase 10)**: depende de US1–US7; bloqueia US8–US10.
- **US8 (fase secundária)**: a base manual iniciou após a Phase 10 (T182
  incluído); não bloqueia o MVP.
- **Phase 11A (importação de investimentos)**: inicia após a validação real de
  2026-10-27; não bloqueia o MVP nem reabre o gate bancário.
- **US9–US10 (pós-MVP)**: iniciam após o gate e não bloqueiam o MVP.
- **Documentação final (Phase 14)**: depende de US8–US10.

### User Story Dependencies

- **US1 (P1)**: base de autenticação e isolamento; pode começar após Fundação.
- **US2 (P1)**: depende de US1 para sessão/usuário; entrega configuração da
  integração e importação inicial.
- **US3 (P1)**: depende de US2 para integração, contas e transações; entrega
  sincronização, recuperação e ciclo de vida de credenciais (API Key,
  autorização anual e consentimento bancário). T192 complementa o
  `TransactionController` criado em T067.
- **US5 (P2)**: depende de US2 para transações/categorias; entrega contas
  manuais, correções e reconciliação.
- **US6 (P2)**: depende de US2 para cartões/transações; entrega faturas locais.
  Correções e pagamentos de US5 melhoram US6, mas não bloqueiam a geração local
  de faturas.
- **US4 (P2)**: depende de US2/US3 e US6 para passivos de cartão, pagamentos de
  fatura e composição de `cardOutstandingBalance`. Transferências manuais e
  reembolsos são validados em US5.
- **US7 (P2)**: depende de US1/US2 e dos módulos de dados; deve ser validada
  depois de US3–US6.
- **US8 (P3)**: depende de US1 e do gate do MVP; é independente da integração
  bancária.
- **US9 (P4)**: depende de US1, do gate, e das fontes de saldo (US2/US6/US8);
  pode começar com ativos/passivos manuais.
- **US10 (P4)**: depende de US1, do gate e, para metas de patrimônio líquido, de
  US9. T163 depende de T155 para consolidar a extensão do contrato em
  `contracts/openapi.yaml`.

### Within Each User Story

1. Testes primeiro; eles devem falhar antes da implementação.
2. Migração e entidades.
3. Serviços de domínio.
4. Controllers/DTOs.
5. Frontend.
6. E2E e validação independente.

### Parallel Opportunities

| História | Tarefas paralelas | Observação |
|---|---|---|
| US1 | T040–T043 | Testes de contrato, integração, isolamento e UI. |
| US2 | T050–T053; T055–T056 | Testes e modelos independentes. |
| US3 | T071–T075, T194; T077–T078 | Testes e modelos de sync/webhook. |
| US5 | T104–T107; T109–T110 | Testes e modelos de correção/match. |
| US6 | T122–T124, T196; T126–T128 | Testes e modelos de fatura. |
| US4 | T090–T092; T097–T102 | Testes e componentes de dashboard, após US6. |
| US7 | T139–T141; T142–T144 | Testes e serviços de privacidade. |
| US8 | T148–T149; T151–T152 | Testes e serviço/UI de investimentos. |
| US9 | T155–T156; T158–T161 | Testes e serviço/UI de patrimônio. |
| US10 | T164 | Teste de frontend de metas; T163 depende de T155 para o contrato. |

**Exemplo de paralelismo em US2**:

```text
T050 Contract test de integração
T051 Integration test com dois stubs
T052 Integration test de segredo write-only
T053 E2E de configuração da integração
T055 Modelo PluggyIntegration
T056 Modelo PluggyConnection
```

---

## Implementation Strategy

### MVP First

O MVP bancário é composto por **US1–US7**. A ordem de entrega é:

1. Setup + Fundação.
2. US1: conta e isolamento.
3. US2: configuração Pluggy e importação.
4. US3: sincronização e recuperação.
5. US5: contas manuais, correções e reconciliação.
6. US6: faturas completas.
7. US4: dashboard e relatórios.
8. US7: privacidade e ciclo de vida.
9. T171–T189, T193 e T197–T199: hardening, backup/restauração, performance,
   acessibilidade, recuperação de senha, cobertura e gate real com duas aplicações
   Meu Pluggy independentes.

**Não declarar o MVP concluído** antes do gate real com duas aplicações Meu
Pluggy independentes, múltiplos itemIds, reconexão e backup/restauração.
Nenhuma tarefa de US8–US10 pode iniciar antes de a Phase 10 ser concluída
(T182 incluído).

### Incremental Delivery

1. US1 entrega autenticação e isolamento demonstráveis.
2. US2 entrega a primeira importação bancária real.
3. US3 entrega confiança operacional e recuperação.
4. US5 entrega complemento manual e correções.
5. US6 entrega faturas completas.
6. US4 entrega leitura dos dados.
7. US7 entrega controle e saída.
8. Phase 10 entrega o MVP validado com dados reais.
9. US8 entrega a base manual de investimentos.
10. Phase 11A entrega importação, dashboard, cobertura e proventos.
11. US9/US10 entregam patrimônio e metas após o MVP.
12. Phase 14 atualiza a documentação final.

### Parallel Team Strategy

- Backend A: US2/US3 (Pluggy, sync, dados).
- Backend B: US5/US6 (manual, reconciliação, faturas).
- Frontend: US1/US4/US6/US7 em paralelo após Fundação, com US4 após US6.
- QA/Segurança: testes de isolamento, cifragem, backup/restauração e ZAP em
  paralelo, com gate final antes de US8–US10.

---

## Notes

- `[P]` indica arquivos diferentes e ausência de dependência de tarefa
  incompleta.
- `[USn]` mantém rastreabilidade com a especificação.
- Testes devem ser escritos primeiro e falhar antes da implementação.
- Commits após cada tarefa ou grupo lógico.
- Não usar dados fictícios como se fossem dados reais; stubs são apenas de
  teste.
- Transações `PENDING` não compõem totais; entram uma única vez após `POSTED`.
- Migrações DEVEM passar por `MigrationIT` em banco vazio e snapshot anterior.
- Não apagar histórico por lista vazia, falha, `LOGIN_ERROR`,
  `USER_AUTHORIZATION_EXPIRED` ou mudança de itemId.
- Testar explicitamente a expiração/renovação da API Key, a expiração anual da
  autorização da aplicação e a expiração independente do consentimento bancário.
- Não declarar importação automática de investimentos sem validação real.
- O gate real do Meu Pluggy é obrigatório e não pode ser substituído por
  Sandbox, WireMock ou dados sintéticos.
