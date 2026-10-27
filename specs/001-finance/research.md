# Research: MVP de Finanças Pessoais Familiares

**Data**: 2026-10-22

**Spec**: [spec.md](./spec.md)

**Constituição**: [../../.specify/memory/constitution.md](../../.specify/memory/constitution.md)

> **Nota de 2026-10-26:** este documento é histórico. Webhooks (S-04), Bills
> experimentais (S-05), snapshots patrimoniais (S-02) e redução de dívida nas
> metas (S-03) foram removidos. As seções abaixo registram a pesquisa original;
> o escopo vigente está em [`docs/analise-simplificacao.md`](../../docs/analise-simplificacao.md),
> seção 11.

## 1. Versões escolhidas

As versões abaixo foram verificadas em 2026-10-22. Todas as imagens Docker devem
ser fixadas por tag exata e, quando possível, por digest no momento da
implementação.

| Camada | Tecnologia | Versão | Justificativa |
|---|---|---|---|
| JDK | Eclipse Temurin | 25.0.4.1+1 (LTS) | LTS suportado até 2031; compatível com Spring Boot 4.1.x. |
| Build backend | Apache Maven | 3.9.16 | Versão estável recomendada; suporta Java 25. |
| Backend | Spring Boot | 4.1.1 | GA mais recente em 2026-10-21; suporte OSS até 2027-07. |
| Framework | Spring Framework | 7.0.9 (gerenciado pelo Boot) | Dependência transitiva do Boot 4.1.1. |
| Segurança | Spring Security | 7.1.0 (gerenciado pelo Boot) | Sessão, CSRF, Argon2id e autorização. |
| Sessão | Spring Session JDBC | 4.1.0 (gerenciado pelo Boot) | Sessões persistentes no PostgreSQL, sem Redis. |
| Persistência | Spring Data JPA / Hibernate ORM | Hibernate 7.x (gerenciado pelo Boot) | Mapeamento relacional e locking otimista. |
| Banco | PostgreSQL | 18.6 | Minor atual suportado; PostgreSQL 19 ainda estava em RC em 2026-10-22. |
| Migrações | Flyway | 13.10.2 | Suporte declarado a PostgreSQL 18; se o BOM do Boot gerenciar versão anterior, fazer override explícito e testar a compatibilidade. |
| Documentação da API | springdoc-openapi | 3.1.1 | Linha 3.x compatível com Spring Boot 4. |
| Logs | logstash-logback-encoder | 9.0 | JSON estruturado com Jackson 3, alinhado ao Boot 4. |
| Cache | Caffeine | 3.3.0 | Cache em memória com TTL para API Key temporária. |
| Testes de arquitetura | ArchUnit | 1.5.0 | Verificação de limites entre módulos e ausência de ciclos. |
| Testes de integração | Testcontainers | 2.0.5 | PostgreSQL 18.6 real em testes. |
| Runtime frontend | Node.js | 24.22.0 (LTS) | Linha LTS ativa; compatível com Vite 8 e React Router 8. |
| Frontend | React / React DOM | 19.3.0 | Versão estável atual; compatível com React Router 8. |
| Linguagem frontend | TypeScript | 6.0.3 | Estável e compatível com o ecossistema; TypeScript 7.0.2 existe, mas a linha 6.x reduz risco de incompatibilidade de tooling no início do projeto. |
| Build frontend | Vite | 8.3.5 | Build atual com Rolldown; requer Node 20.19+/22.12+. |
| Roteamento | React Router | 8.5.0 | Versão estável; baseline React 19, Vite 7+, ESM. |
| Estado assíncrono | TanStack Query | 5.104.4 | Cache, invalidação e estados de servidor. |
| Formulários | React Hook Form | 7.87.0 | Formulários performáticos e acessíveis. |
| Validação frontend | Zod | 4.4.3 | Schemas compartilháveis com os tipos do contrato. |
| Gráficos | Recharts | 3.10.1 | Gráficos SVG acessíveis e drill-down. |
| Estilo | Tailwind CSS | 4.3.3 | Utilitários e tokens consistentes com shadcn/ui. |
| Componentes | shadcn/ui CLI | 4.x | Componentes copiados para o repositório; sem dependência de runtime do CLI. |
| Testes unitários frontend | Vitest | 4.1.11 | Integração nativa com Vite. |
| Testes de componentes | Testing Library React | 16.3.2 | Testes orientados ao usuário. |
| Testes end-to-end | Playwright | 1.63.0 | Chromium, Firefox e WebKit; acessibilidade e responsividade. |
| Acessibilidade automatizada | axe-core | 4.12.1 | Regras WCAG 2.1 A/AA. |
| Proxy/TLS | Caddy | 2.11.7 | HTTPS automático ou CA interna, configuração simples. |
| Containers | Docker Engine / Compose | 29.1 / 2.40 | Versões estáveis de referência; fixar no host. |

### Alternativas consideradas

- **Spring Boot 3.5.x**: rejeitado porque o suporte OSS terminou em 2026-06-30.
- **PostgreSQL 19**: rejeitado porque ainda estava em RC na data do plano.
- **TypeScript 7.0.2**: adiado; a linha 6.0.3 é estável e reduz risco de
  incompatibilidade com ESLint, shadcn/ui e ferramentas de teste no início.
- **React Router 7.x**: preterido em favor da linha 8.x, que é a estável atual e
  mantém o mesmo modelo de rotas.
- **TanStack Router**: preterido por simplicidade; React Router 8 cobre o SPA
  sem adicionar um framework de rotas baseado em arquivos.
- **Spring Modulith**: preterido para o MVP; a fronteira modular será verificada
  com ArchUnit, sem mais uma dependência de runtime.
- **Redis**: preterido; cache de API Key é local e a fila de jobs usa PostgreSQL.
- **Broker externo (RabbitMQ/Kafka)**: preterido; fila transacional no PostgreSQL
  com `FOR UPDATE SKIP LOCKED` é suficiente para o porte familiar.

## 2. Meu Pluggy: fluxo pessoal confirmado

Fontes oficiais consultadas em 2026-10-22:

- Guia pessoal: https://docs.pluggy.ai/pt/docs/guides/meu-pluggy-personal-use
- Autenticação: https://v2.docs.pluggy.ai/pt/reference/authentication
- Contas: https://v2.docs.pluggy.ai/pt/reference/account/accounts-list
- Transações: https://v2.docs.pluggy.ai/pt/reference/transaction/transactions-list-by-cursor
- Item: https://v2.docs.pluggy.ai/pt/reference/items/items-retrieve
- Webhooks: https://v2.docs.pluggy.ai/pt/reference/webhooks
- Limites de taxa: https://v2.docs.pluggy.ai/pt/reference/rate-limits
- Faturas de cartão: https://v2.docs.pluggy.ai/pt/docs/products/credit-card-bills
- Parcelas de cartão: https://v2.docs.pluggy.ai/pt/docs/products/credit-card-installments

Fatos confirmados:

- A modalidade pessoal permite até **5 conexões** e é destinada a uso pessoal,
  sem finalidade comercial.
- Cada banco é autorizado separadamente no Meu Pluggy.
- Os itens refletem a atualização da origem a cada **24 horas**, não permitem
  atualização manual no fluxo pessoal e enviam webhooks como qualquer item.
- `GET /v2/items` não está disponível para contas Meu Pluggy; o usuário precisa
  copiar o `itemId` de cada conexão.
- A API Key é emitida por `POST /auth`, dura **2 horas** e deve ser usada apenas
  no servidor.
- A autorização da aplicação para ler as conexões dura **1 ano**. Quando expira,
  as leituras podem retornar listas vazias e o item pode ir para `LOGIN_ERROR`
  com `USER_AUTHORIZATION_EXPIRED`. `GET /items/{id}` expõe `consentExpiresAt`,
  `status`, `executionStatus`, `lastUpdatedAt`, `nextAutoSyncAt` e erros.
- O `itemId` pode mudar após a renovação; a reconciliação deve usar IDs locais
  estáveis, não o `itemId` como chave primária.
- `GET /accounts?itemId=` retorna contas `BANK` e `CREDIT`. Contas de crédito
  podem trazer `creditData` com `balanceCloseDate`, `balanceDueDate`,
  `availableCreditLimit`, `minimumPayment`, `creditLimit` e `status`.
- O endpoint paginado antigo `GET /transactions` está **obsoleto** e será
  removido em 2026-12-31. O endpoint atual é `GET /v2/transactions`, com
  paginação por cursor (`after`/`next`).
- Transações de cartão podem trazer `creditCardMetadata` com `totalInstallments`,
  `installmentNumber`, `billId`, `billForecastDate`, `paymentType`,
  `billPostDate` e `purchaseDate`.
- A API pode não fornecer um identificador único que agrupe todas as parcelas da
  mesma compra. O agrupamento exige heurística e correção do usuário.
- A entidade Bill (fatura) é retornada obrigatoriamente em conexões Open Finance
  reguladas e apenas em alguns conectores diretos. **Não presumir** que o fluxo
  Meu Pluggy fornecerá Bills. O MVP deve construir e manter faturas localmente,
  importando Bills quando estiverem disponíveis.
- Webhooks exigem URL HTTPS, respondem `2xx` em até 10 segundos e têm até 3
  tentativas. Eventos de transação não trazem `clientUserId`; a associação deve
  ser feita pelo `itemId` persistido localmente.
- Limites de taxa são por endpoint, por IP, por minuto. `POST /auth`,
  `/accounts`, `/v2/transactions` e `/investments` têm 360 req/min;
  `PATCH /items/{id}` tem 20 req/min. Em `429`, respeitar `RateLimit-Reset` e
  `Retry-After`.

## 3. Decisão de arquitetura

### Decisão

Monólito modular Spring Boot com módulos de domínio explícitos, um único
processo de aplicação e um PostgreSQL. O cliente React é uma aplicação separada
no mesmo repositório, servida pelo próprio stack Docker Compose.

### Módulos

| Módulo | Responsabilidade |
|---|---|
| `auth` | cadastro, login, logout, senha, sessões, tentativas de login, CSRF. |
| `security` | autorização por proprietário, classificação de dados, criptografia e chaves. |
| `integrations` | credenciais Pluggy, validação, API Key temporária e estados da integração. |
| `connections` | itens externos, mapeamento local estável, consentimentos e reconciliação de itemId. |
| `accounts` | contas bancárias, cartões, perfis de cartão e saldos. |
| `transactions` | importação, deduplicação, correções, movimentos pendentes e histórico. |
| `invoices` | ciclos, faturas, parcelas, pagamentos, encargos e itens manuais. |
| `categories` | categorias predefinidas e personalizadas. |
| `reconciliation` | sugestões de transferência interna, pagamento de fatura e confirmação do usuário. |
| `reporting` | dashboard, agregações, filtros e drill-down. |
| `privacy` | exportação, exclusão de conta e desconexão. |
| `investments` | posições manuais de ações/FIIs, fase secundária. |
| `wealth` | ativos, passivos, snapshots e patrimônio líquido, fase posterior. |
| `goals` | metas, alocações e progresso, fase posterior. |
| `shared` | dinheiro, tempo, erros, paginação, auditoria e utilidades. |

### Regras de dependência

- `shared` não depende de módulos de negócio.
- Controladores dependem de serviços de aplicação, nunca de repositórios de
  outro módulo.
- Cada módulo expõe uma API pública de serviços e DTOs; entidades JPA não
  atravessam a fronteira.
- `reporting` lê por serviços de consulta públicos, não por repositórios alheios.
- ArchUnit falha o build se houver ciclo entre módulos ou acesso a repositórios
  internos de outro módulo.

### Alternativas rejeitadas

- Microserviços: complexidade operacional sem necessidade comprovada.
- Spring Modulith: útil, mas adiciona dependência e cerimônia; ArchUnit cobre a
  verificação necessária no MVP.
- Banco separado por usuário: inviável para o porte e para a operação
  self-hosted; o isolamento será lógico, testado e reforçado por FKs compostas.

## 4. Criptografia e classificação de dados

### Decisão

- **Senhas**: Argon2id via Spring Security, salt aleatório, hash de 32 bytes.
  Parâmetros iniciais: 19 MiB, 2 iterações, paralelismo 1; calibrar para
  250–500 ms no servidor de referência.
- **Campos sensíveis**: AES-256-GCM com nonce aleatório de 96 bits, tag de 128
  bits e AAD vinculando `user_id`, tabela, coluna, ID do registro e versão da
  chave.
- **Índices de busca**: HMAC-SHA-256 com chave separada para `itemId`,
  `accountId`, `transactionId` externo e chave de deduplicação. O valor bruto é
  guardado cifrado; o HMAC é usado para igualdade e unicidade.
- **Chaves**: key ring externo ao banco e à imagem, montado como Docker secret
  ou arquivo read-only. Cada ciphertext carrega a versão da chave. A rotação de
  chave de criptografia é online; a rotação da chave de índice exige runbook e
  reindexação.
- **Sessão**: cookie `__Host-delta_session`, `HttpOnly`, `Secure`,
  `SameSite=Lax`, path `/`, 30 minutos de inatividade e 12 horas absolutas.
  CSRF por cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`.
- **API Key Pluggy**: cache Caffeine por integração, TTL de 110 minutos, máximo
  de 100 entradas, nunca persistida e nunca devolvida ao cliente.

### Classificação

| Classe | Exemplos | Tratamento |
|---|---|---|
| Secreto irreversível | senha | Argon2id. |
| Secreto recuperável | Client Secret, Client ID, segredo de webhook | AES-256-GCM + versão de chave. |
| Identificador externo | itemId, accountId, transactionId | AES-256-GCM + HMAC para busca/unicidade. |
| Financeiro sensível | valores, saldos, descrições, nomes de categoria, apelidos, instituição, totais de fatura, encargos e pagamentos | AES-256-GCM. |
| Metadado de consulta | user_id, IDs locais, datas, tipo, status, moeda, origem, ciclo, timestamps | Texto claro, indexado. |
| Credencial de sessão | session id | Cookie opaco; sessão no servidor. |
| Log/auditoria | evento, resultado, duração, código de erro | Sanitizado, sem valores ou descrições. |

### Consequência para relatórios

Como valores e descrições são cifrados, o PostgreSQL não consegue somar,
ordenar ou filtrar por faixa de valor diretamente. O fluxo será:

1. filtrar no banco por `user_id`, período, conta, cartão, categoria, tipo e
   origem (campos claros);
2. carregar candidatos em lotes com paginação por cursor;
3. descriptografar no servidor;
4. aplicar filtro de faixa de valor e agregações com `BigDecimal`;
5. devolver totais e a lista de detalhamento.

Para o cenário de referência (5 usuários, 5 conexões por usuário, 10.000
transações por usuário), esse custo é aceitável. Consultas de descrição livre
não fazem parte do MVP. O plano limita a varredura por requisição e mede o tempo
do dashboard com dados sintéticos.

## 5. Sincronização e idempotência

### Decisão

- Fila de jobs no PostgreSQL (`sync_job`) com `FOR UPDATE SKIP LOCKED`.
- Um job por integração ou conexão; concorrência global limitada e concorrência
  por integração igual a 1.
- IDs locais estáveis: `pluggy_integration.id`, `pluggy_connection.id` e
  `financial_account.id` não mudam quando o `itemId` ou o ID externo muda.
- Deduplicação por `HMAC(provider, connection_id, account_id, external_id)` com
  restrição única por usuário. Quando o ID externo muda, a transação é
  reconciliada por fingerprint (conta, valor, data, descrição normalizada,
  parcela) e o novo ID entra em `external_identity_history`; não se cria uma
  segunda despesa.
- Janela diária: últimos 7 dias com sobreposição de 2 dias.
- Importação inicial: 12 meses.
- Reconciliação mensal: 12 meses por conta, uma vez a cada 30 dias, respeitando
  o limite operacional de 4 chamadas históricas por CPF/instituição/mês.
- Webhooks são opcionais e exigem HTTPS público. Quando habilitados, são
  registrados por integração, validados por segredo, idempotentes por
  `eventId`, respondem `202` em menos de 10 s e enfileiram o processamento.
- A sincronização programada continua sendo a reconciliação de segurança.
- Lista vazia, `LOGIN_ERROR`, `USER_AUTHORIZATION_EXPIRED`, timeout, `429` ou
  indisponibilidade nunca disparam exclusão. A transação só é marcada como
  `SOURCE_REMOVED` após reconciliação conclusiva e permanece no histórico.
- Falha de um usuário/conexão não bloqueia os demais.

### Limites operacionais

- `GET /v2/transactions` com paginação por cursor; o padrão da API é 500 e o
  parâmetro `pageSize` não deve ser enviado (a API o recusa com `400`). Use
  `dateFrom`/`dateTo`; `next` é uma query string pronta e o `after` é opaco.
- `GET /accounts?itemId=` com paginação.
- `GET /items/{id}` para status, `consentExpiresAt`, `nextAutoSyncAt`,
  `lastUpdatedAt` e erro.
- `POST /auth` com cache de 110 minutos; nunca uma chamada por requisição.
- `429`: esperar `RateLimit-Reset`; retries com backoff e jitter.
- `PATCH /items/{id}` não será usado no fluxo diário; atualizações de item são
  operações explícitas e limitadas.

## 6. Faturas de cartão

### Decisão

- A fatura é uma entidade **local**, mesmo quando o provedor não fornece Bill.
- Cada cartão tem `closing_day` e `due_day` configuráveis. Os campos
  `creditData.balanceCloseDate` e `balanceDueDate` são usados como sugestão e
  conferidos com o usuário.
- Transações de cartão são associadas à fatura por `billId` quando presente;
  caso contrário, pelo ciclo configurado e por `creditCardMetadata`/data.
- Parcelas: cada transação de parcela é uma despesa no ciclo correspondente.
  O valor total da compra não é contabilizado antecipadamente. O agrupamento de
  parcelas usa heurística (`installmentNumber`, `totalInstallments`,
  comerciante, primeira data) e pode ser corrigido manualmente.
- Bills do provedor, quando disponíveis, são importadas para reconciliar totais,
  encargos e pagamentos. Se não estiverem disponíveis no Meu Pluggy, o usuário
  completa os dados manualmente e o sistema não inventa fatura.
- Pagamentos totais ou parciais são movimentos internos, não despesas; reduzem o
  saldo devedor. Juros, encargos e tarifas são despesas explícitas. O sistema
  não calcula juros automaticamente.
- Mudanças de ciclo não reescrevem faturas fechadas/pagas. Transações atrasadas
  vão para o ciclo seguinte ou para um ajuste explícito com auditoria.
- Edições manuais de itens preservam valores anteriores e origem do ajuste.

### Riscos específicos

- O fluxo Meu Pluggy pode não retornar Bills. O produto não pode depender disso
  para a gestão de faturas.
- O Open Finance não padroniza o agrupamento de parcelas. A heurística pode
  falhar; a interface deve permitir revisão e correção.
- Bancos podem remover e recriar transações com novo ID. A reconciliação por
  fingerprint e o histórico de identidades externas são obrigatórios.
- Transações `PENDING` sem `billId` podem virar `POSTED` com `billId` depois;
  webhooks e reconciliação mensal cobrem a transição.

## 7. Contratos e API

### Decisão

- REST/JSON em `/api/v1`, contrato OpenAPI 3.1 versionado em
  `contracts/openapi.yaml`.
- Sessão por cookie; CSRF por cookie + header; sem JWT no MVP.
- Erros no formato RFC 9457 (`ProblemDetail`) com `code`, `requestId` e
  mensagens sanitizadas.
- Paginação por cursor opaco; página máxima de 100 itens; transações com
  `limit` padrão de 50.
- Valores monetários trafegam como strings decimais; o backend usa
  `BigDecimal`; o cliente usa `decimal.js` apenas para pré-visualização.
- `Idempotency-Key` restrito às escritas financeiras críticas sujeitas a retry:
  transações, itens de fatura e pagamentos.
- `version` para concorrência otimista em agregados mutáveis.
- Nenhum DTO expõe `Client Secret`, API Key, chave de criptografia ou `user_id`
  enviado pelo cliente.
- A API é same-origin em produção: `client` serve o SPA e faz proxy de `/api`
  para `server`; o proxy Caddy termina TLS. Em desenvolvimento, o Vite faz proxy
  para o servidor.

### Contratos principais

- Auth: registro, login, logout, sessão atual, alteração de senha e CSRF.
- Integração: configuração Pluggy, validação, conexões/itens, sincronização e
  estado.
- Contas/cartões: listagem, detalhe e perfil de cartão.
- Transações: listagem filtrável, criação manual, correção de importada,
  exclusão de manual e exportação.
- Categorias: CRUD.
- Faturas: listagem por cartão, detalhe, itens manuais, pagamentos e status.
- Reconciliação: sugestões de transferência/pagamento de fatura, confirmação e
  rejeição.
- Dashboard: resumo, categorias, série temporal e drill-down.
- Privacidade: exportação, exclusão e desconexão.
- Investimentos: posições manuais.
- Futuro: patrimônio e metas.

## 8. Frontend

### Decisão

- React 19.3 + TypeScript 6.0.3 + Vite 8.3.5.
- shadcn/ui com componentes copiados para `client/src/components/ui`; Tailwind
  4.3.3; Radix primitives.
- React Router 8.5.0 para rotas; TanStack Query 5.104.4 para estado de servidor;
  React Hook Form 7.87.0 + Zod 4.4.3 para formulários; Recharts 3.10.1 para
  gráficos; `decimal.js` para formatação/preview de valores.
- Layout: sidebar em desktop, top bar + drawer em mobile; tabelas viram listas
  de cartões em telas estreitas; filtros em sheet/drawer.
- Gráficos com resumo textual, tabela alternativa/drill-down e cores
  suplementadas por rótulos e padrões.
- Estados de carregamento, vazio, erro, sincronização pendente e autorização
  expirada sempre explícitos.
- O Client Secret é write-only no formulário; nunca é reidratado, armazenado no
  Query Client ou em `localStorage`.
- As skills `frontend-design` e `ui-ux-pro-max` serão usadas na fase de
  implementação para definir direção visual, tipografia, paleta e componentes,
  subordinadas à acessibilidade e aos requisitos funcionais.

### Direção visual

Produto financeiro familiar, confiável e sóbrio: tipografia legível, números
tabulares, contraste alto, hierarquia clara, densidade moderada e cores
semânticas para receita/despesa/alerta. Evitar estética de trading, excesso de
gradientes e gráficos decorativos. O dashboard deve parecer uma ferramenta de
conferência, não uma tela promocional.

## 9. Testes e qualidade

### Decisão

- Unitários: dinheiro, ciclos de fatura, parcelas, rateio, pagamentos parciais,
  estornos, deduplicação, criptografia e autorização.
- Integração: PostgreSQL 18.6 via Testcontainers, migrações Flyway, FKs
  compostas, isolamento e jobs concorrentes.
- API: MockMvc/RestAssured com dois usuários, CSRF, 401/403/404, idempotência,
  paginação e OpenAPI.
- Pluggy: WireMock/MockWebServer com fixtures oficiais; testar 401, 429,
  timeout, paginação por cursor, lista vazia, `USER_AUTHORIZATION_EXPIRED`,
  mudança de itemId, parcelas e transações removidas/recriadas.
- E2E: Playwright com dois usuários, configuração da integração em stub, sync, dashboard,
  lançamentos manuais, faturas, exportação, exclusão, responsividade e axe-core.
- Segurança: testes de acesso cruzado, secret scanning, dependency scanning,
  OWASP ZAP baseline e revisão de logs.
- Operação: backup/restauração em banco limpo, health checks, migração e
  inicialização do Compose.
- Performance: k6 com 5 usuários, 5 conexões e 10.000 transações por usuário;
  dashboard ≤ 3 s e filtros ≤ 2 s.
- Validação real: dois usuários com aplicações Meu Pluggy independentes e
  múltiplos itemIds, executada manualmente como gate de aceitação; Sandbox e
  stubs não contam como validação real.

## 10. Operação self-hosted

### Decisão

- Compose com `db` (PostgreSQL 18.6), `server` (Spring Boot), `client`
  (Caddy 2.11.7 servindo o SPA e proxy `/api`) e `proxy` (Caddy 2.11.7 para
  TLS).
- Migrações Flyway no start do `server`, com lock de banco; `db` precisa estar
  healthy.
- Health checks: `pg_isready`, `/actuator/health/liveness` e
  `/actuator/health/readiness`, `/healthz` no cliente.
- Volumes persistentes: `pgdata`, `caddy_data`, `caddy_config`; key ring em
  Docker secret/arquivo read-only, fora do repositório.
- Backup: `pg_dump -Fc` + criptografia com `age`; retenção de 30 dias;
  restauração testada em banco limpo; backup das chaves separado e obrigatório.
- Atualização: backup → migração → smoke test → rollback de imagem se falhar.
- Logs JSON em stdout, sem segredos ou valores financeiros; retenção de 90 dias
  para sync runs e webhook events, 1 ano para auditoria.
- Métricas Micrometer/Prometheus em endpoint interno; sem dependência de SaaS.
- Graceful shutdown: parar de pegar jobs, finalizar o job atual e encerrar.

### Segredos

Nunca versionar `.env`, key ring, dumps ou backups. O `.env.example` contém
apenas placeholders. O Compose lê segredos de `.env`/Docker secrets e monta o key
ring read-only. Sem as chaves, os campos cifrados são irrecuperáveis; isso deve
estar explícito no runbook.

## 11. Riscos e decisões que exigem verificação

| Risco | Mitigação |
|---|---|
| Bills não disponíveis no Meu Pluggy | Fatura local baseada em ciclo e `creditCardMetadata`; Bills importadas só quando disponíveis. |
| Parcelas sem ID de grupo | Heurística + revisão do usuário; nunca duplicar automaticamente. |
| Transação removida e recriada com novo ID | Histórico de identidades externas + fingerprint; marcar, não apagar. |
| `itemId` muda após renovação | IDs locais estáveis + workflow de reconciliação com confirmação do usuário. |
| Autorização anual expira | `consentExpiresAt`, `LOGIN_ERROR`, estado de ação, preservação de dados e runbook de reconexão. |
| Limite de 5 conexões | UI mostra o limite e não confunde conexões com contas/cartões. |
| Rate limit por IP | Fila, concorrência limitada, janela de 7 dias, reconciliação mensal e backoff. |
| Webhook não acessível em LAN | Feature flag; sincronização programada continua obrigatória. |
| Valores cifrados impedem SQL agregado | Filtro por metadados + descriptografia/agregação no servidor; limite de varredura e teste de performance. |
| Perda da chave | Backup separado do key ring, runbook e aviso explícito de irrecuperabilidade. |
| Escopo de faturas aumentar o MVP | Módulo `invoices` separado; implementado após o núcleo bancário, mas antes do gate final do MVP. |

## 12. Decisões deferidas

- Formato exato da exportação: JSON Lines + CSV no ZIP, definido em
  `contracts/openapi.yaml` e no plano; sem segredos.
- Retenção de logs/auditoria: 90 dias para sync/webhook, 1 ano para auditoria
  de segurança; ajustável por configuração.
- Rotação da chave de índice HMAC: runbook de manutenção, não automática no MVP.
- Webhooks: opcionais e dependentes de HTTPS público; não são pré-requisito para
  a conclusão do MVP.
- Importação automática de investimentos: fora do MVP; só após validação real.
- Patrimônio e metas: fase posterior, com modelo já previsto.

## 13. Fontes

- Spring Boot: https://spring.io/projects/spring-boot
- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Boot 4.1 release notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes
- PostgreSQL releases: https://www.postgresql.org/support/versioning/
- Flyway supported databases: https://documentation.red-gate.com/fd/supported-databases-and-versions-143754067.html
- Java/Temurin: https://adoptium.net/support
- Maven: https://maven.apache.org/download.cgi
- React versions: https://react.dev/versions
- Vite: https://vite.dev/blog/announcing-vite8
- React Router: https://reactrouter.com/changelog
- Tailwind CSS: https://github.com/tailwindlabs/tailwindcss/releases
- Node.js releases: https://nodejs.org/en/about/previous-releases
- shadcn/ui CLI: https://ui.shadcn.com/docs/changelog
- Playwright: https://playwright.dev
- Caddy: https://github.com/caddyserver/caddy/releases
- Docker/Compose: https://github.blog/changelog/2026-01-30-docker-and-docker-compose-version-upgrades-on-hosted-runners
- Meu Pluggy: https://docs.pluggy.ai/pt/docs/guides/meu-pluggy-personal-use
- Pluggy auth: https://v2.docs.pluggy.ai/pt/reference/authentication
- Pluggy accounts: https://v2.docs.pluggy.ai/pt/reference/account/accounts-list
- Pluggy transactions: https://v2.docs.pluggy.ai/pt/reference/transaction/transactions-list-by-cursor
- Pluggy items: https://v2.docs.pluggy.ai/pt/reference/items/items-retrieve
- Pluggy webhooks: https://v2.docs.pluggy.ai/pt/reference/webhooks
- Pluggy rate limits: https://v2.docs.pluggy.ai/pt/reference/rate-limits
- Pluggy credit-card bills: https://v2.docs.pluggy.ai/pt/docs/products/credit-card-bills
- Pluggy credit-card installments: https://v2.docs.pluggy.ai/pt/docs/products/credit-card-installments
