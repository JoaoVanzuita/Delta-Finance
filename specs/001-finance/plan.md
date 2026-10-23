# Implementation Plan: MVP de Finanças Pessoais Familiares

**Branch**: `001-finance` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-finance/spec.md`

## Summary

Construir um monólito modular Spring Boot com PostgreSQL para o gerenciamento
financeiro privado de uma família, com configuração pessoal do Meu Pluggy por
usuário, importação bancária automática, sincronização idempotente, correções
manuais, gestão completa de faturas de cartão, dashboard com drill-down e
controles de privacidade. O cliente React/TypeScript é uma aplicação separada no
mesmo repositório, servida pelo stack Docker Compose.

A integração bancária é o caminho crítico do MVP. O sistema não depende de
descoberta automática de itens, não compartilha credenciais e não presume que o
provedor entregue Bills de cartão. Valores e descrições sensíveis são cifrados
com chave externa; a agregação financeira é feita no servidor após
descriptografia autorizada. A operação é self-hosted, com HTTPS, migrações
versionadas, backup/restauração documentados e nenhum broker externo.

Artefatos de design:

- [research.md](./research.md): versões, decisões e fatos verificados da API.
- [data-model.md](./data-model.md): entidades, cifragem, índices e transições.
- [contracts/openapi.yaml](./contracts/openapi.yaml): contrato REST principal.
- [quickstart.md](./quickstart.md): roteiro de validação ponta a ponta.

## Technical Context

**Language/Version**: Java 25 (Eclipse Temurin 25.0.4.1+1 LTS); TypeScript 6.0.3;
Node.js 24.22.0 LTS.

**Primary Dependencies**: Spring Boot 4.1.1, Spring Framework 7.0.9, Spring
Security 7.1.0, Spring Session JDBC 4.1.0, Spring Data JPA/Hibernate ORM 7,
Flyway 13.10.2, springdoc-openapi 3.1.1, Caffeine 3.3.0, logstash-logback-encoder
9.0. Frontend: React 19.3.0, React Router 8.5.0, TanStack Query 5.104.4, React
Hook Form 7.87.0, Zod 4.4.3, Recharts 3.10.1, Tailwind CSS 4.3.3, shadcn/ui CLI
4.x, decimal.js 10.x.

**Storage**: PostgreSQL 18.6. Sem Redis, sem broker externo. Cache de API Key
Pluggy em memória com Caffeine; fila de jobs e idempotência no PostgreSQL.

**Testing**: JUnit Jupiter (gerenciado pelo Spring Boot), Testcontainers 2.0.5,
ArchUnit 1.5.0, WireMock/MockWebServer, REST Assured/MockMvc, Playwright 1.63.0,
Vitest 4.1.11, Testing Library React 16.3.2, axe-core 4.12.1, k6.

**Target Platform**: Linux server com Docker Engine 29.1 e Docker Compose 2.40;
navegadores modernos em desktop e mobile; implantação self-hosted com Caddy
2.11.7 para TLS.

**Project Type**: Web application monorepo com `server/` (Spring Boot) e
`client/` (React SPA), mais `compose.yaml`, `deploy/`, migrações e documentação.

**Performance Goals**:

- Dashboard inicial em até 3 s para o cenário de referência: 5 usuários, 5
  conexões por usuário e 10.000 transações por usuário.
- Aplicação de filtros e drill-down em até 2 s no mesmo cenário.
- CRUD e listagens paginadas com p95 de até 500 ms, excluindo o custo de
  descriptografia de grandes volumes.
- Login e logout com p95 de até 1 s.
- Sincronização de uma conexão concluída em até 60 s em latência normal do
  provedor, com timeout de job de 5 min.

**Constraints**:

- Operação self-hosted, sem dependência de SaaS, broker gerenciado ou Redis.
- Isolamento por usuário verificado no servidor e reforçado por FKs compostas.
- Campos financeiros sensíveis cifrados com AES-256-GCM; agregações em memória
  após filtro por metadados claros.
- API Key Pluggy temporária (2 h), somente no servidor, em cache por
  `integrationId` com TTL de 110 min; o valor em cache inclui `userId` e
  `integrationId` e é validado na leitura; nunca persistida por padrão.
- Meu Pluggy: limite de conexões configurável e datado (atualmente 5 por conta
  pessoal), atualização de origem a cada 24 h, autorização anual, sem descoberta
  de itens e sem atualização manual de itens proxy.
- Bills de cartão não são presumidas no fluxo Meu Pluggy; faturas são locais.
- Webhooks Pluggy são opcionais e exigem HTTPS público; sincronização
  programada permanece obrigatória.
- Sem tempo real, sem movimentação de dinheiro, sem recomendação de
  investimento.
- Sessão: 30 min de inatividade, 12 h absolutas; cookies `HttpOnly`, `Secure`,
  `SameSite=Lax`; CSRF por cookie + header.
- RPO de 24 h e RTO de 2 h para a operação de referência; backup diário
  criptografado com retenção de 30 dias.
- BRL, `America/Sao_Paulo` e semana iniciando na segunda-feira como padrão
  inicial; períodos de relatório são calendário nesse fuso, com intervalos
  `[início, fim)` e filtros `from`/`to` incluindo o dia final.

**Scale/Scope**: 5 usuários de referência; 5 conexões por usuário;
50.000 transações como linha de base e até 100.000 com crescimento; 10 histórias
de usuário; 139 requisitos funcionais; 26 critérios de sucesso.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Como o plano atende |
|---|---|---|
| I. Privacidade e isolamento | PASS | `user_id` derivado da sessão; FKs compostas `(user_id, id)`; consultas por proprietário; testes cruzados com dois usuários e duas integrações. |
| II. Criptografia e segredos | PASS | Argon2id; AES-256-GCM com key ring externo; HMAC para IDs/dedup; API Key só no servidor; logs sanitizados; promessa de proteção documentada. |
| III. Integração bancária central | PASS | Configuração pessoal Meu Pluggy obrigatória; `POST /auth` no servidor; múltiplos itemIds; sync real e gate de aceitação com duas aplicações. |
| IV. Correção financeira | PASS | `BigDecimal`; chaves de dedup; IDs locais estáveis; faturas locais; sem exclusão por lista vazia; reconciliação explícita. |
| V. Simplicidade | PASS | Monólito modular; sem microserviços; sem broker; uma aplicação e um banco. Complexidade deliberada registrada abaixo. |
| VI. Confiabilidade e observabilidade | PASS | Fila de jobs, retries, `sync_run`, auditoria, health checks, métricas, logs JSON e preservação de dados. |
| VII. UX e acessibilidade | PASS | React responsivo, WCAG 2.1 AA, estados explícitos, gráficos com alternativa textual e drill-down. |
| VIII. Self-hosting | PASS | Compose com `db`, `server`, `client`, `proxy`; configuração externa; migrações; backup/restauração; HTTPS. |
| IX. Investimentos com escopo controlado | PASS | Apenas posições manuais no MVP secundário; importação automática só após validação real. |
| X. Patrimônio e metas | PASS | Modelo previsto em `data-model.md`; implementação e UI em fase posterior. |
| XI. Decisões e documentação | PASS | `research.md`, `data-model.md`, contratos, quickstart, runbooks e registro de limitações externas. |

**Post-Design Re-check**: PASS. O design não introduz violação constitucional.
As duas decisões de maior complexidade são justificadas em Complexity Tracking:
agregação de valores cifrados no servidor e fila transacional no PostgreSQL.

## Project Structure

### Documentation (this feature)

```text
specs/001-finance/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── openapi.yaml
├── checklists/
│   └── requirements.md
└── spec.md
```

### Source Code (repository root)

```text
server/
├── pom.xml
├── mvnw
├── perf/
├── src/
    ├── main/
    │   ├── java/com/deltafinance/
    │   │   ├── DeltaFinanceApplication.java
    │   │   ├── shared/
    │   │   │   ├── money/
    │   │   │   ├── time/
    │   │   │   ├── crypto/
    │   │   │   ├── errors/
    │   │   │   ├── pagination/
    │   │   │   ├── audit/
    │   │   │   ├── logging/
    │   │   │   ├── persistence/
    │   │   │   └── config/
    │   │   ├── auth/
    │   │   ├── security/
    │   │   ├── integrations/
    │   │   ├── connections/
    │   │   ├── accounts/
    │   │   ├── transactions/
    │   │   ├── invoices/
    │   │   ├── reconciliation/
    │   │   ├── categories/
    │   │   ├── reporting/
    │   │   ├── privacy/
    │   │   ├── investments/
    │   │   ├── wealth/
    │   │   └── goals/
    │   └── resources/
    │       ├── application.yml
    │       ├── db/migration/
    │       └── logback-spring.xml
    └── test/
        ├── java/com/deltafinance/
        │   ├── unit/
        │   ├── integration/
        │   ├── api/
        │   ├── architecture/
        │   └── support/
        └── resources/fixtures/pluggy/
client/
├── package.json
├── .env.production
├── vite.config.ts
├── src/
│   ├── app/
│   ├── components/
│   │   ├── ui/
│   │   └── layout/
│   ├── features/
│   │   ├── auth/
│   │   ├── integration/
│   │   ├── dashboard/
│   │   ├── accounts/
│   │   ├── transactions/
│   │   ├── invoices/
│   │   ├── categories/
│   │   ├── reconciliation/
│   │   ├── privacy/
│   │   ├── investments/
│   │   ├── wealth/
│   │   └── goals/
│   ├── lib/
│   │   ├── api/
│   │   ├── format/
│   │   └── validation/
│   ├── styles/
│   └── test/
├── docs/
│   └── design-direction.md
├── e2e/
└── public/
deploy/
├── Caddyfile
├── client.Caddyfile
├── README.md
└── scripts/
    ├── backup.sh
    ├── restore.sh
    ├── reset-password.sh
    └── zap-baseline.sh
compose.yaml
.env.example
docs/
├── operations.md
├── security.md
├── accessibility.md
├── release-checklist.md
└── runbooks/
```

**Structure Decision**: monorepo com backend e frontend separados, um único
deploy Compose. O backend usa package-by-feature com API pública por módulo; o
frontend usa feature folders e componentes shadcn/ui locais. `deploy/` contém
configuração de proxy, health checks e scripts operacionais; testes de
performance ficam em `server/perf/`. Nenhum segredo real
entra no repositório.

## Complexity Tracking

| Decisão | Por que é necessária | Alternativa simples rejeitada |
|---|---|---|
| Agregação de valores cifrados no servidor | O dump isolado do PostgreSQL não pode revelar valores/descrições; a constituição exige cifragem de campos financeiros. | Somar valores em SQL exigiria texto claro; rejeitado por violar a promessa de proteção. |
| Fila de jobs no PostgreSQL | Retry, recuperação, concorrência segura e operação self-hosted sem broker. | RabbitMQ/Kafka adicionaria serviço, operação e pontos de falha sem necessidade comprovada. |
| Modelo local de faturas | A gestão completa de faturas foi escolhida no clarify e as Bills não são garantidas no Meu Pluggy. | Depender de Bills do provedor deixaria o MVP incompleto para usuários cujo conector não as retorna. |
| Cache Caffeine para API Key | Evitar `POST /auth` por requisição e respeitar o limite de 360 req/min. | Persistir a chave no banco aumentaria exposição e exigiria criptografia/TTL adicionais; cache em memória é suficiente. |

## Phase 0: Research

**Status**: concluído em 2026-10-22. Nenhuma clarificação pendente.

Principais resultados:

- Java 25 LTS, Spring Boot 4.1.1, PostgreSQL 18.6, React 19.3, Vite 8.3.5 e
  Node 24.22.0 LTS são as escolhas estáveis e compatíveis na data.
- O fluxo pessoal Meu Pluggy permite até 5 conexões, atualiza a origem a cada
  24 h, não oferece `GET /v2/items` e exige itemId manual.
- A API Key dura 2 h e deve ser obtida por `POST /auth` no servidor.
- A autorização da aplicação dura 1 ano; a expiração pode retornar leituras
  vazias e `LOGIN_ERROR`/`USER_AUTHORIZATION_EXPIRED`.
- O endpoint atual de transações é `GET /v2/transactions` com cursor; o endpoint
  paginado antigo está obsoleto.
- Bills de cartão não são garantidas no Meu Pluggy; faturas serão locais, com
  importação opcional quando disponível.
- Parcelas não têm um identificador único de grupo; o agrupamento exige
  heurística e revisão do usuário.
- Webhooks exigem HTTPS público, respondem 2xx em 10 s e têm até 3 tentativas;
  a sincronização programada continua sendo a reconciliação.
- Rate limits são por endpoint/IP/minuto; `429` deve respeitar
  `RateLimit-Reset`/`Retry-After`.

Detalhes, fontes e alternativas: [research.md](./research.md).

## Phase 1: Design & Contracts

**Status**: concluído.

- **Modelo de dados**: [data-model.md](./data-model.md) define entidades,
  cifragem, FKs compostas, índices, estados, retenção e migrações.
- **Contrato**: [contracts/openapi.yaml](./contracts/openapi.yaml) define 39
  caminhos no total — 37 do MVP e 2 da fase secundária de investimentos (US8) —,
  schemas, erros RFC 9457, paginação por cursor, idempotência e segurança por
  sessão/CSRF. Endpoints de patrimônio e metas são adicionados nas tarefas
  T155/T163.
- **Quickstart**: [quickstart.md](./quickstart.md) define o roteiro de
  validação, incluindo dois usuários, duas integrações reais, faturas,
  dashboard, exportação, exclusão, backup e restauração.
- **Sem código de aplicação**: esta fase produz apenas artefatos de design.

## Implementation Sequence

A ordem abaixo respeita o caminho crítico do MVP. O gate de aceitação real do
Meu Pluggy não pode ser substituído por Sandbox, stub ou dados sintéticos.

### Etapa 1 — Fundação do repositório e operação

- Criar `server/`, `client/`, `deploy/`, `compose.yaml`, `.env.example`,
  `.gitignore`, README e pipeline de CI.
- Configurar PostgreSQL 18.6, Flyway, health checks, volumes, redes e Caddy.
- Configurar logs JSON, actuator, profiles `local`/`prod` e graceful shutdown.
- Definir `server/pom.xml` com versões fixadas e `npm` com lockfile.

**Saída**: stack sobe, migrações vazias aplicam, health checks verdes.

### Etapa 2 — Autenticação, sessão e isolamento

- Implementar `app_user`, Argon2id, registro, login, logout, troca de senha,
  sessão JDBC, cookies seguros, CSRF e limitação de tentativas.
- Implementar `user_id` derivado da sessão, FKs compostas de propriedade e
  testes cruzados com dois usuários.
- Implementar auditoria sanitizada de autenticação e alteração de senha.

**Saída**: US1 e critérios de isolamento; nenhuma operação atravessa usuários.

### Etapa 3 — Criptografia, classificação e chaves

- Implementar `CryptoService` AES-256-GCM, envelope de versão, AAD e key ring
  externo.
- Implementar `IndexHmacService` para IDs externos, dedup, fingerprint e
  normalização de nomes.
- Implementar `Money`, `Clock` e serialização de `BigDecimal` como string.
- Documentar classificação de campos, rotação, backup e implicações de perda.

**Saída**: testes de cifragem/decifragem, adulteração detectada, dump sem chaves
não revela campos protegidos.

### Etapa 4 — Integração pessoal Meu Pluggy

- Implementar `pluggy_integration`: Client ID/Secret cifrados, validação por
  `POST /auth`, estado, erros e atualização transacional.
- Implementar cache Caffeine da API Key (TTL 110 min), invalidação em 401 e
  chamadas server-side.
- Implementar `pluggy_connection` e configuração de múltiplos itemIds.
- Implementar mensagens acionáveis de estado, limite de conexões e
  renovação/reconexão.
- NÃO solicitar, chamar `/connect_token` ou persistir Connect Token no perfil
  pessoal do MVP.

**Saída**: US2; credenciais válidas ativam a integração, inválidas não ativam,
segredos nunca retornam à interface.

### Etapa 5 — Importação de contas, cartões e transações

- Implementar `financial_account`, `card_profile`, `transaction` e
  `external_identity_history`.
- Implementar cliente Pluggy com `GET /items/{id}`, `GET /accounts?itemId` e
  `GET /v2/transactions` com cursor.
- Implementar upsert idempotente, dedup, fingerprint, IDs locais estáveis e
  histórico de identidade.
- Implementar janela inicial de 12 meses, sincronização diária de 7 dias com
  sobreposição e reconciliação mensal de 12 meses.
- Implementar fila `sync_job`, `FOR UPDATE SKIP LOCKED`, retries, backoff e
  isolamento de falhas por usuário/conexão.

**Saída**: contas/cartões/transações importados, segunda sincronização sem
duplicação, falha de um usuário não bloqueia os demais.

### Etapa 6 — Estados, recuperação e webhooks opcionais

- Implementar estados de integração/conexão, última tentativa, último sucesso,
  `consentExpiresAt`, `nextAutoSyncAt` e códigos de erro sanitizados.
- Implementar detecção de `USER_AUTHORIZATION_EXPIRED`, `LOGIN_ERROR`, timeout,
  `429`, lista vazia e indisponibilidade sem excluir dados.
- Implementar atualização de itemId e reconciliação sem perda histórica.
- Implementar revisão explícita de transações `SOURCE_REMOVED`, com restaurar ou
  confirmar remoção e histórico preservado.
- Implementar webhooks atrás de feature flag, com segredo por integração,
  `eventId` idempotente, resposta 202, processamento assíncrono e reconciliação
  programada.
- Implementar `sync_run` e painel de status.

**Saída**: US3; recuperação de autorização expirada, mudança de itemId e falhas
externas sem perda de histórico.

### Etapa 7 — Categorias, contas manuais, lançamentos e reconciliação

- Implementar categorias predefinidas/personalizadas, com nome cifrado e
  unicidade por HMAC.
- Implementar contas manuais com saldo de referência e data, incluindo criação,
  edição e desativação; conta desativada não recebe novos lançamentos e o tipo é
  imutável após a criação.
- Implementar lançamentos manuais de receita, despesa, transferência, estorno,
  reembolso e ajuste.
- Implementar correções de transações importadas com `edited_fields`,
  `transaction_correction` e precedência do usuário.
- Implementar sugestões de transferência e pagamento de fatura com confirmação
  do usuário (`internal_match`).
- Implementar filtros e listagens com paginação por cursor.

**Saída**: US5 e parte de US4; lançamentos manuais, correções e movimentos
internos com semântica explícita.

### Etapa 8 — Faturas de cartão

- Implementar `invoice`, `invoice_item` e `invoice_payment`.
- Implementar configuração de fechamento/vencimento, associação por `billId` ou
  ciclo, parcelas, pagamentos parciais, juros, encargos e itens manuais.
- Implementar importação opcional de Bills quando o provedor retornar; sem
  presumir disponibilidade.
- Implementar recálculo de total/saldo devedor, estados, fechamento e
  reconciliação de transações atrasadas.
- Garantir que mudança de ciclo não reescreva faturas fechadas/pagas e que
  transação pós-fechamento vá para o ciclo seguinte ou ajuste explícito.
- Sinalizar ajustes manuais por `has_adjustments`/`adjustment_count`, preservando
  o status de pagamento, e registrar edições em `invoice_item_correction`.
- Implementar a UI de faturas com detalhamento, edição, histórico e
  configuração de fechamento, vencimento e limite do cartão.

**Saída**: US6; gestão completa de faturas sem dupla contagem e sem depender de
Bills do provedor.

### Etapa 9 — Dashboard, relatórios e experiência

- Implementar consultas de relatório por período, conta, cartão, categoria,
  tipo, origem e faixa de valor.
- Implementar resultado do período, saldo atual consolidado, distribuição por
  categoria, série temporal e drill-down.
- Implementar saldo atual apenas de contas `BANK`/`CASH`/`WALLET` com status
  `ACTIVE`, cartões como passivo separado, um único total de despesas e períodos
  calendário em `America/Sao_Paulo`.
- Implementar filtros e estados de carregamento/erro/vazio/sincronização.
- Implementar layout responsivo, tabelas adaptativas, gráficos acessíveis e
  navegação mobile/desktop.
- Aplicar `frontend-design` e `ui-ux-pro-max` antes de fechar tokens visuais,
  tipografia, cores e componentes; validar WCAG 2.1 AA.

**Saída**: US4; dashboard e relatórios consistentes, responsivos e acessíveis.

### Etapa 10 — Privacidade, exportação e exclusão

- Implementar exportação ZIP/JSON Lines + CSV, sem segredos e apenas com dados
  do solicitante.
- Implementar desconexão de integração com preservação de histórico por padrão.
- Implementar exclusão de conta com confirmação, revogação de sessões,
  cancelamento de jobs, remoção de dados ativos e tombstone de auditoria.
- Implementar política de retenção de backups, job diário de retenção de dados
  operacionais e documentação ao usuário.
- Implementar `audit_event` para eventos de privacidade: exportação,
  desconexão e exclusão, sem segredos ou conteúdo financeiro desnecessário.

**Saída**: US7; exportação, desconexão e exclusão testadas com dois usuários.

### Etapa 11 — Hardening, operação e aceitação real

- Executar testes de isolamento, segurança, dependências, secret scanning e ZAP.
- Validar backup/restauração em banco limpo, incluindo key ring.
- Validar performance com k6 no cenário de referência, incluindo login, CRUD,
  sincronização, dashboard e filtros.
- Validar acessibilidade com axe-core, teclado e leitor de tela.
- Executar o gate manual com duas aplicações Meu Pluggy independentes e
  múltiplos itemIds.
- Implementar e validar o mecanismo e o runbook de recuperação assistida de
  senha.
- Configurar o gate de cobertura de 80% de linhas no domínio.
- Documentar runbooks de conexão, reconexão, rotação de chaves, backup,
  restauração, atualização, incidentes e recuperação assistida de senha.

**Saída**: MVP pronto para operação familiar, com critérios de conclusão
demonstrados.

### Etapa 12 — Fase secundária: investimentos

- Implementar posições manuais de ações/FIIs.
- Validar separadamente a disponibilidade de conectores XP/Santander no fluxo
  Meu Pluggy antes de qualquer promessa de importação automática.
- Não atrasar o MVP bancário nem incluir recomendação de investimento.

### Etapa 13 — Fase posterior: patrimônio e metas

- Implementar `manual_asset_liability`, `wealth_snapshot`,
  `wealth_snapshot_item`, `financial_goal`, `goal_allocation` e
  `goal_progress_snapshot`.
- Implementar UI somente após o MVP bancário estabilizado.
- Garantir snapshots datados, sem dupla contagem, com regras de progresso
  visíveis e sem inferir saldos passados quando o histórico for insuficiente.
- Não movimentar dinheiro, não criar ordens nem oferecer aconselhamento.

## Frontend Plan

### Rotas e navegação

- `/login`, `/register`.
- `/dashboard` com resultado do período, saldo atual, categorias, série
  temporal, pendências de sincronização e drill-down.
- `/accounts`, `/accounts/:id`, `/transactions`, `/categories`.
- `/cards/:accountId/invoices`, `/invoices/:id`.
- `/reconciliation` para sugestões pendentes.
- `/settings/integration`, `/settings/privacy`.
- `/investments` na fase secundária; `/wealth` e `/goals` somente na fase
  posterior. A navegação reserva a hierarquia, mas não exibe telas vazias nem
  atalhos que sugiram funcionalidades concluídas.

### Componentes e estado

- Textos em português brasileiro; datas exibidas em `America/Sao_Paulo`; valores
  em BRL, com semana iniciando na segunda-feira.
- TanStack Query para cache/invalidação; nenhum segredo no Query Client.
- React Hook Form + Zod para formulários; mensagens de erro associadas aos
  campos e resumo de erros.
- shadcn/ui + Radix para diálogos, sheets, comboboxes e tabelas.
- `decimal.js` apenas para formatação/preview; totais autoritativos vêm do
  servidor.
- Formulários de Client Secret write-only: nunca reidratar, nunca salvar em
  `localStorage`, limpar após envio.
- Fatura: linha do tempo do ciclo, itens, pagamentos, encargos, saldo devedor e
  ações de edição com confirmação.

### Responsividade e acessibilidade

- Mobile-first; sidebar em desktop, top bar + drawer em mobile.
- Tabelas viram cards/listas em telas estreitas; filtros em sheet.
- Gráficos com resumo textual, tabela alternativa, rótulos e drill-down.
- Navegação por teclado, foco visível, skip link, landmarks, `aria-live` para
  sincronização e contraste AA.
- Testar 360 px, zoom 200%, leitor de tela e redução de movimento.

### Direção visual

Ferramenta financeira familiar, sóbria e confiável: tipografia legível, números
tabulares, contraste alto, hierarquia clara e cores semânticas para receita,
despesa, alerta e sincronização. Evitar estética de trading, excesso de
gradientes, gráficos decorativos e densidade que sacrifique leitura. As skills
`frontend-design` e `ui-ux-pro-max` serão usadas para consolidar tokens,
tipografia, paleta e componentes, com a acessibilidade como restrição.

## Backend Plan

### Serviços centrais

- `AuthService`: registro, login, senha, sessão, tentativas e auditoria.
- `AssistedPasswordResetService`: procedimento operacional auditado, com
  verificação de identidade, invalidação de sessões e isolamento.
- `OwnershipGuard`: verifica `user_id` em toda operação e em consultas.
- `CryptoService` / `KeyRing`: AES-GCM, versão de chave, rotação e AAD.
- `IndexHmacService`: HMAC de IDs, dedup, fingerprints e normalização.
- `PluggyAuthClient`: `POST /auth`, cache, renovação e tratamento de 401/429.
- `PluggyDataClient`: `GET /items/{id}`, `/accounts`, `/v2/transactions`.
- `SyncJobService`: fila, claim, retries, backoff, idempotência e locks.
- `TransactionImportService`: upsert, dedup, fingerprint, IDs estáveis e
  reconciliação.
- `ManualAccountService`: criação, edição e desativação de contas manuais.
- `CorrectionService`: precedência de campos editados e auditoria.
- `SourceRemovalReviewService`: restauração ou confirmação de remoção na origem.
- `RetentionJob`: expurgo diário conforme prazos documentados.
- `InvoiceService`: ciclos, parcelas, pagamentos, encargos, totais e status.
- `ReconciliationService`: sugestões de transferência/pagamento de fatura.
- `ReportingService`: filtros, descriptografia, agregação e projeção `ReportEntry`.
- `DrillDownService`: entradas `TRANSACTION` e `INVOICE_ITEM` sem transação.
- `PrivacyService`: exportação, desconexão e exclusão.
- `AuditService`: eventos sanitizados.
- `KeyRotationJob`: reencriptação em lotes e verificação.

### Regras financeiras

- Reembolsos e chargebacks são entradas vinculadas à transação original por
  `related_transaction_id`, herdam a categoria e reduzem despesas no período em
  que ocorrem; o original não é alterado. Reembolsos parciais são suportados.
- Somente `POSTED`/`RECONCILED` compõem `income`, `expenses`, `periodResult`,
  categorias, drill-down e totais de fatura. `PENDING` é exibido em bloco
  separado (`pendingCount`, `pendingIncome`, `pendingExpenses`);
  `PENDING→POSTED` atualiza o mesmo registro e entra uma única vez.
  `SOURCE_REMOVED` sai dos totais correntes.
- Transferência manual cria duas pernas vinculadas (`DEBIT` negativo na origem
  e `CREDIT` positivo no destino), ambas `TRANSFER`, com `internal_match`
  confirmado e sem impacto em receitas/despesas.
- Ajustes de fatura são sinalizados por `has_adjustments`/`adjustment_count`,
  sem substituir o status de pagamento; edições de item preservam histórico em
  `invoice_item_correction`.
- Itens de fatura vinculados a transações são espelhos: compõem o total da fatura
  uma única vez e não entram novamente em `expenses`, `income`, `periodResult`,
  categorias ou drill-down. O total da fatura inclui o item uma única vez. Itens
  sem transação de juros, encargos ou tarifas aumentam `expenses` uma vez; itens
  sem transação com efeito `EXPENSE_DECREASE` reduzem `expenses` observado o
  menor entre o valor do item, o total da fatura antes do item e as despesas do
  mesmo cartão no período do relatório. O excedente
  ao total da fatura vira `creditBalance` do cartão; o crédito acumulado de
  faturas anteriores é aplicado à primeira fatura não paga em ordem de vencimento;
  se exceder a fatura, o residual segue para a próxima, sem reabrir faturas pagas
  e nunca gerando despesa negativa. A fatura expõe `appliedCredit`.
- Posições de investimento têm total `quantidade × preço unitário`; quando só o
  total é informado, o preço unitário é `total ÷ quantidade`, arredondado para 8
  casas decimais.
- Metas de redução de dívida usam `(dívida inicial − dívida atual) ÷ dívida
  inicial`, limitado entre 0% e 100%; a dívida inicial é registrada na criação.
- A sugestão de movimento interno usa: mesmo usuário, contas diferentes, sinais
  opostos, tolerância de `max(R$ 0,01, 0,5% do maior valor absoluto entre os dois
  movimentos)`, janela de ±3 dias para `TRANSFER` e ±7 dias para `CARD_PAYMENT`,
  score com pesos 50 para valor, 30 para data e 20 para contas, e limiar 80;
  empates são resolvidos pela menor diferença de data, depois pela menor diferença
  de valor e, por fim, pelo menor identificador local; nunca confirma
  automaticamente.
- Metas usam `allocation_mode` (`FIXED_AMOUNT` ou `PERCENTAGE`), exatamente um
  valor por alocação e percentuais ≤ 100%. `FIXED_AMOUNT` é exato e pode
  ultrapassar a meta, com progresso limitado a 100%; `PERCENTAGE` usa
  `floor(valor-alvo × percentual ÷ 100, 2)` e o resíduo vai para a última
  alocação percentual. Percentuais abaixo de 100% deixam saldo não alocado
  visível e não redistribuído; a soma das alocações de uma mesma fonte entre
  metas não pode exceder o saldo da fonte.

### Transações e concorrência

- `@Transactional` nos casos de uso; nenhuma transação de banco mantida durante
  chamadas HTTP ao provedor.
- Claim de jobs com `FOR UPDATE SKIP LOCKED`; lock por integração para evitar
  duas sincronizações concorrentes.
- `version` para locking otimista em agregados mutáveis.
- Idempotência de mutações com `Idempotency-Key`.
- Gravação de resultados de sync em transação separada e curta.
- Falhas externas não revertem dados já confirmados.

### Segurança

- `SecurityFilterChain`: sessão, CSRF, headers, logout, login JSON.
- Argon2id com m=19 MiB, t=2, p=1, salt de 16 bytes e hash de 32 bytes;
  parâmetros calibrados para 100–250 ms no hardware de referência e verificados
  em teste.
- Política de senha: 12–128 caracteres, sem composição obrigatória; a senha não
  pode conter o nome de usuário normalizado nem ser igual a ele; lista local
  versionada com no mínimo 10.000 senhas comuns e sem chamada externa.
- CSRF cookie/header; same-origin em produção; CORS restrito em dev.
- Rate limit de login: 5 falhas por 15 minutos por usuário+IP, bloqueio de 15
  minutos e contador zerado em autenticação bem-sucedida; endpoints sensíveis
  têm limites próprios.
- `@PreAuthorize`/serviços com `AuthenticatedUser`; nunca `userId` do DTO.
- Sanitização de logs e erros; `ProblemDetail` sem resposta bruta do provedor.
- Cabeçalhos de segurança: HSTS, CSP compatível com SPA, X-Content-Type-Options,
  Referrer-Policy, Permissions-Policy.
- Actuator exposto apenas na rede interna; Prometheus opcional.

### Dados e relatórios

- Filtros por metadados claros no SQL; valores/descrições decifrados no servidor.
- `currentBalance` soma apenas contas `BANK`, `CASH` e `WALLET` com status
  `ACTIVE`; contas `INACTIVE`, `CLOSED` ou `UNAVAILABLE` ficam fora do saldo
  atual, mas permanecem no histórico e nos relatórios. Cartões são passivos
  separados em `cardOutstandingBalance`.
- `expenses` é o único total de despesas e representa o gasto efetivo; a
  distribuição por categoria é um detalhamento, não um total paralelo.
- `periodResult` é `income − expenses`; `currentBalance` é saldo consolidado.
  Nenhum deles é fluxo de caixa, e o MVP não exibe total separado de fluxo de
  caixa.
- Valores efetivos são assinados e consistentes com o tipo.
- Períodos de relatório são calendário em `America/Sao_Paulo`, com intervalos
  `[início, fim)`; filtros `from`/`to` incluem o dia final.
- Varredura limitada por requisição; se o intervalo for amplo demais, retornar
  422 com orientação para estreitar o filtro (FR-091).
- Totais sempre recalculados a partir da mesma consulta que alimenta o
  drill-down.
- Nenhum cache de dashboard com dados financeiros; no máximo cache curto de
  metadata e status de sync.
- Exportação em streaming para não carregar todos os dados em memória.

## Data and API Plan

- O modelo detalhado está em [data-model.md](./data-model.md).
- O contrato está em [contracts/openapi.yaml](./contracts/openapi.yaml).
- A API usa `/api/v1`, sessão por cookie, CSRF, `ProblemDetail`, cursor opaco,
  `Idempotency-Key` e valores monetários como string.
- `DashboardSummary` expõe `pendingCount`, `pendingIncome` e
  `pendingExpenses`, sem total líquido de pendentes; `/transactions` aceita
  filtro `status`.
- `DashboardSummary.lastSuccessfulSyncAt` é a sincronização bem-sucedida mais
  recente entre as conexões com contas/cartões incluídos no filtro; sem conexão
  aplicável, retorna nulo e a UI indica “sem sincronização no período”.
- Contas manuais são criadas por `POST /accounts` e editadas/desativadas por
  `PATCH /accounts/{id}`; contas importadas rejeitam edição manual com 409 e o
  `type` é imutável após a criação.
- `DashboardSummary` não expõe `cashFlow`; `currentBalance` e `periodResult`
  não são rotulados como fluxo de caixa.
- `CardProfile` expõe `creditBalance` e `Invoice` expõe `appliedCredit`; ambos
  são derivados e não compõem `income`, `expenses` ou `currentBalance`.
- O drill-down usa `GET /reports/entries`, retornando entradas
  `TRANSACTION` e `INVOICE_ITEM` sem transação, com `origin`
  (`IMPORTED`/`MANUAL`) em cada entrada; a soma deve bater com o total.
- O histórico auditável de correções de item de fatura é exposto por
  `GET /invoice-items/{id}/corrections`.
- A revisão de transações `SOURCE_REMOVED` usa
  `POST /transactions/{id}/source-removal` com ação `RESTORE` ou
  `CONFIRM_REMOVAL`.
- Nenhum DTO retorna Client Secret, API Key, chave de criptografia ou dados de
  outro usuário.
- A documentação springdoc é gerada a partir do código, mas o contrato versionado
  é a fonte de verdade para o cliente e para os testes de contrato. O contrato
  cobre 39 caminhos no total: 37 do MVP e 2 de investimentos da fase secundária
  (US8); extensões pós-MVP de patrimônio e metas são versionadas nas tarefas T155
  e T163.
- O cliente pode gerar tipos a partir do OpenAPI; a geração é parte do build e
  falha se o contrato for inválido.

## Operations Plan

### Compose

| Serviço | Imagem | Portas | Volumes | Healthcheck | Depende de |
|---|---|---|---|---|---|
| `db` | `postgres:18.6-alpine` | interna 5432 | `pgdata` | `pg_isready` | — |
| `server` | build multi-stage `eclipse-temurin:25.0.4.1_1-jdk` → `25.0.4.1_1-jre` | interna 8080 | key ring read-only; logs via stdout | `/actuator/health/liveness` e `/actuator/health/readiness` | `db` healthy |
| `client` | build Node 24.22.0 → `caddy:2.11.7-alpine` | interna 80 | estáticos no build | `/healthz` | `server` |
| `proxy` | `caddy:2.11.7-alpine` | 80, 443 | `caddy_data`, `caddy_config` | `/healthz` interno | `client` |
| `backup` (profile) | `postgres:18.6-alpine` + `age` | — | `pgdata`, `backups` | execução one-shot | `db` healthy |

- `db`: `postgres:18.6-alpine`, volume `pgdata`, healthcheck `pg_isready`.
- `server`: build multi-stage com Temurin 25, healthcheck de readiness,
  `depends_on: db: condition: service_healthy`, key ring read-only.
- `client`: build Node 24 + Caddy 2.11.7, servindo o SPA e fazendo proxy de
  `/api` para `server`; healthcheck `/healthz`.
- `proxy`: Caddy 2.11.7, portas 80/443, TLS automático ou CA interna, volumes
  `caddy_data`/`caddy_config`.
- Redes separadas `frontend` e `backend`; apenas `proxy` e `client` expostos.

### Configuração

- `.env.example` com placeholders: `DELTA_DB_NAME`, `DELTA_DB_USER`,
  `DELTA_DB_PASSWORD`, `DELTA_PUBLIC_BASE_URL`, `DELTA_DOMAIN`,
  `DELTA_COOKIE_SECURE`, `DELTA_PLUGGY_BASE_URL`,
  `DELTA_PLUGGY_WEBHOOKS_ENABLED`, `DELTA_PLUGGY_CONNECTION_LIMIT`,
  `DELTA_PLUGGY_CONNECTION_LIMIT_CHECKED_AT`, `DELTA_CRYPTO_KEY_FILE`,
  `DELTA_CRYPTO_ACTIVE_VERSION`, `DELTA_INDEX_KEY_VERSION`.
- Key ring em Docker secret/arquivo read-only, nunca em env versionado.
- Logs JSON, sem bodies, segredos ou valores financeiros.
- Job diário de retenção: `login_attempt` 30 dias, `sync_run`/`webhook_event`
  90 dias, `audit_event` 1 ano e `idempotency_key` 24 h.
- Migrações Flyway no start; falha de migração impede readiness.

### Backup e restauração

- `pg_dump -Fc` diário, criptografado com `age`, retenção de 30 dias.
- Backup separado do key ring e do `.env`; sem chaves, os dados cifrados são
  irrecuperáveis.
- Restauração testada em banco limpo; migrações pendentes aplicadas antes do
  app.
- RPO 24 h, RTO 2 h para a operação de referência.
- Runbooks em `docs/runbooks/` para conexão, reconexão, rotação, backup,
  restauração, atualização, incidente e recuperação assistida de senha.

### Observabilidade

- Logs JSON com `requestId`, `userId` pseudônimo, evento, resultado, duração e
  código de erro.
- Métricas Micrometer: requisições, latência, jobs, sync runs, falhas por
  código, tamanho de fila, cache hit/miss, duração de relatórios.
- `sync_run` é a fonte de verdade operacional para cada tentativa.
- Health liveness/readiness; readiness inclui DB e key ring.
- Alertas documentados para falhas repetidas, fila acumulada, expiração de
  autorização e falha de backup.

## Testing and Quality Gates

### Pirâmide

- **Unitários**: dinheiro, ciclo de fatura, parcelas, pagamento parcial, juros,
  transferência manual em duas pernas, reembolso/chargeback vinculado (inclusive
  parcial), correção de item de fatura, dedup, fingerprint, crypto, key version.
- **Integração**: Testcontainers PostgreSQL 18.6, Flyway, FKs compostas,
  isolamento, recuperação assistida de senha, jobs concorrentes, locks e
  idempotência.
- **Migração**: `MigrationIT` aplicando todas as migrações em banco vazio e em
  snapshot da versão anterior; falha de migração quebra o build e impede
  readiness.
- **API**: MockMvc/REST Assured, dois usuários e duas integrações independentes,
  CSRF, 401/403/404, validação, paginação, idempotência, sinal de `amount`,
  criação/desativação de contas manuais, composição de `currentBalance` somente
  com contas `ACTIVE`, item espelho compondo o total da fatura uma única vez,
  limite de `EXPENSE_DECREASE` conforme FR-077, `ReportEntry.origin`,
  `lastSuccessfulSyncAt` conforme filtro, soma de `/reports/entries` igual ao
  total, cache de API Key isolado por integração e renovado após expiração, e
  contrato OpenAPI.
- **Pluggy**: WireMock/MockWebServer com fixtures oficiais para auth 200/401,
  API Key expirada e renovada via `POST /auth` no servidor, `/items/{id}`,
  `/accounts`, `/v2/transactions`, cursor, `429`, timeout, lista
  vazia, `USER_AUTHORIZATION_EXPIRED`, parcelas, `billId`, mudança de ciclo,
  transação tardia, ausência de Connect Token, credencial nova inválida,
  `SOURCE_REMOVED`, `PENDING`→`POSTED`, remoção de item, consentimento bancário
  independente e remoção/recriação.
- **E2E**: Playwright com stub server; cadastro, configuração da integração,
  sync, dashboard, manual, fatura, reconciliação, exportação, exclusão,
  responsividade e axe.
- **Real**: gate manual com duas aplicações Meu Pluggy independentes, sem
  recomendação de investimento; não usar Sandbox/stub como prova.

### Gates de qualidade

- Build falha em violação ArchUnit, contrato inválido, teste de isolamento
  quebrado, segredo detectado ou migração inválida.
- Cobertura mínima: 80% de linhas no domínio, verificada por JaCoCo no build
  (T199); 100% dos caminhos críticos de autorização e dinheiro com testes
  dedicados (não apenas cobertura).
- Nenhum log/erro contém Client Secret, API Key, descrição/valor financeiro.
- Nenhuma transação importada é apagada por lista vazia/erro.
- Nenhum total de gráfico difere do drill-down.
- Nenhum endpoint retorna dados de outro usuário.
- Backup/restauração e key ring testados antes do release.
- axe-core sem violações críticas/sérias nos fluxos principais; teclado e leitor
  de tela verificados manualmente.
- Performance dentro das metas no cenário de referência, incluindo login, CRUD,
  sincronização, dashboard e filtros (T174 e T198).

### Segurança

- Secret scanning, dependency scanning, container scanning e ZAP baseline.
- Revisão de headers, cookies, CSRF, CORS, rate limit e mensagens de erro.
- Auditoria sanitizada de exportação, desconexão e exclusão, sem segredos ou
  conteúdo financeiro desnecessário.
- Teste de dump do PostgreSQL sem key ring: campos protegidos ilegíveis.
- Teste de rotação de chave: dados antigos continuam legíveis; novos usam a
  versão ativa.
- Teste de perda de chave documentado como irrecuperável.

## Risks and Mitigations

| Risco | Impacto | Mitigação |
|---|---|---|
| Bills indisponíveis no Meu Pluggy | Faturas incompletas se dependessem do provedor | Fatura local por ciclo + `creditCardMetadata`; Bills opcionais. |
| Parcelas sem ID de grupo | Duplicação ou agrupamento errado | Heurística + revisão do usuário; nunca duplicar automaticamente. |
| Transação removida/recriada com novo ID | Perda ou duplicação | `external_identity_history` + fingerprint; marcar, não apagar. |
| `itemId` muda após renovação | Histórico órfão | IDs locais estáveis e workflow de reconciliação. |
| Autorização anual expira | Leituras vazias | `consentExpiresAt`, `LOGIN_ERROR`, estado de ação, dados preservados. |
| Rate limit por IP | Sync falhando | Janela de 7 dias, reconciliação mensal, concorrência limitada, backoff. |
| Webhook indisponível em LAN | Atraso de parcelas | Sincronização programada obrigatória; webhook opcional. |
| Agregação de valores cifrados lenta | Dashboard acima da meta | Filtro por metadados, varredura limitada, testes k6, possível cache de leitura no futuro. |
| Perda da chave | Dados irrecuperáveis | Backup separado do key ring, runbook e aviso explícito. |
| Escopo de faturas aumentar o MVP | Atraso | Módulo isolado; implementação após o núcleo bancário, antes do gate final. |
| Dependência de versões recentes | Incompatibilidade | Versões fixadas, lockfiles, testes de build e atualização controlada; validar overrides de Flyway, springdoc e logstash contra o BOM do Boot. |

## Banking Integration Completion Criteria

A integração bancária só é declarada concluída quando o gate real demonstrar:

- dois usuários com pares Client ID/Client Secret distintos;
- múltiplos itemIds por usuário;
- validação de credenciais e itens antes de ativar;
- API Keys obtidas/renovadas somente no servidor;
- contas, cartões e transações importados corretamente;
- segunda sincronização sem duplicação;
- falha de um usuário sem bloquear os demais;
- nenhum acesso cruzado;
- expiração anual, mudança de itemId e reconexão sem perda histórica;
- estado, última tentativa e último sucesso compreensíveis;
- operação local com dados importados quando a Pluggy estiver indisponível;
- backup/restauração com chaves externas validado.

Sandbox, mocks e dados sintéticos não contam para esse gate.

## Post-MVP Evolution

- **Investimentos**: posições manuais de ações/FIIs; importação automática
  somente após validação real do conector no Meu Pluggy.
- **Patrimônio**: ativos, passivos, snapshots datados e patrimônio líquido;
  modelo já previsto, sem UI no MVP.
- **Metas**: valor-alvo, prazo, regra de progresso, alocações explícitas com
  `allocation_mode`, histórico e ausência de movimentação de dinheiro.
- **Multi-moeda**: fora do MVP; registros em moeda diferente de BRL são
  sinalizados e não entram em totais consolidados sem regra definida.
- **Webhooks**: podem ser promovidos a caminho principal após validação em
  produção, mantendo a reconciliação programada.
- **Cache de relatórios**: se o volume crescer, avaliar projeções de leitura
  cifradas ou materialized views sem expor valores em texto claro.

## Definition of Done

- [ ] Stack Compose sobe com health checks verdes.
- [ ] Migrações aplicadas em banco vazio e existente, com `MigrationIT` verde.
- [ ] Autenticação, sessão com expiração de 30 minutos/12 horas, CSRF, política
      de senha, limite de login e isolamento com testes cruzados.
- [ ] Transações `PENDING` e itens de fatura sem transação com semântica validada.
- [ ] Job de retenção, mecanismo e runbook de recuperação assistida de senha
      validados (T191, T193, T197).
- [ ] Criptografia, key ring, rotação e dump sem chaves validados.
- [ ] Configuração da integração Pluggy, API Key cache e múltiplos itemIds.
- [ ] Importação de contas, cartões e transações idempotente.
- [ ] Recuperação de autorização expirada e mudança de itemId.
- [ ] Contas manuais, lançamentos, correções, desativação e reconciliação.
- [ ] Faturas completas, configuração de ciclo/limite, parcelas, pagamentos
      parciais, encargos, crédito/carry-forward, mudança de ciclo e transações
      pós-fechamento.
- [ ] Dashboard com resultado do período, saldo atual sem cartões e somente de
      contas `ACTIVE`, última sincronização relevante e drill-down via
      `/reports/entries`, incluindo itens de fatura sem transação.
- [ ] `CONFIRM_REMOVAL` e `RESTORE` de `SOURCE_REMOVED` sem duplicação.
- [ ] Cadastro e primeiro login de 5 usuários em menos de 2 minutos e
      lançamento manual em menos de 90 segundos.
- [ ] Exportação, desconexão e exclusão de conta, com dados ativos removidos em
      até 24 horas e eventos de auditoria sanitizados.
- [ ] Nenhuma tela exibe recomendação de compra, venda, alocação ou
      aconselhamento de investimento.
- [ ] Backup/restauração, key ring, runbooks e expiração de backup em 30 dias
      validados.
- [ ] Acessibilidade, responsividade e performance, incluindo login, CRUD,
      sincronização, dashboard e filtros (T174, T198).
- [ ] Cobertura de 80% de linhas no domínio e 100% dos caminhos críticos de
      autorização e dinheiro verificados por testes dedicados (T199).
- [ ] Gate real com duas aplicações Meu Pluggy independentes.
- [ ] Nenhum código de aplicação foi gerado nesta fase de planejamento.
