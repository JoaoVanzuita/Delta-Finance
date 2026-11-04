# Features — Delta Finance

> Documentação descritiva das funcionalidades implementadas. Documenta **o que já
> existe** no código-fonte; não propõe correções nem mudanças. Referência
> complementar de [`specs/001-finance/spec.md`](../specs/001-finance/spec.md) e
> [`data-model.md`](../specs/001-finance/data-model.md).
>
> Relacionamento com as telas: [`screens.md`](screens.md).

## Visão geral

Aplicação web self-hosted de finanças pessoais para um grupo familiar, com
isolamento total por usuário. Backend Spring Boot (monólito modular em
`server/src/main/java/com/deltafinance`) e frontend React SPA
(`client/src`). Cada funcionalidade abaixo é apresentada com: descrição, regras
implementadas, endpoints REST relacionados e telas correspondentes.

Módulos do backend: `auth`, `accounts` (inclui cartões), `categories`,
`connections`, `integrations`, `transactions`, `invoices`, `reconciliation`,
`reporting`, `investments`, `wealth`, `goals`, `privacy`, `security`, `shared`.

---

## 1. Identidade, autenticação e sessão

**Módulo backend:** `auth` · **Frontend:** `features/auth`

Cadastro, login, logout, identidade do usuário e alteração de senha, com sessão
HTTP por cookie e proteção contra abuso.

- **Cadastro** (`POST /api/v1/auth/register`): nome de usuário único e senha.
  Rate limit por IP em memória (`RegistrationRateLimiter`, padrão 5/hora).
- **Política de senha** (`PasswordPolicy`): 12–128 caracteres (contagem por
  code point), sem composição obrigatória; rejeita senha que contenha/iguale o
  nome de usuário normalizado e senhas presentes em lista local versionada
  (`security/common-passwords-v1.txt`, mínimo 10.000 entradas). Hash Argon2id
  (m=19 MiB, t=2, p=1, salt 16 B, hash 32 B) via `PasswordConfig`.
- **Login** (`POST /api/v1/auth/login`): mensagens genéricas (não distingue
  usuário inexistente de senha incorreta); dummy hash para usuário desconhecido
  (anti-enumeração por timing); prevenção de fixação de sessão (`changeSessionId`).
- **Proteção de força bruta** (`LoginAttemptService`): 5 falhas por 15 minutos
  por combinação usuário+IP, bloqueio de 15 minutos; contador zera em sucesso;
  username/IP armazenados apenas como HMAC.
- **Logout** (`POST /api/v1/auth/logout`): invalida a sessão e limpa o cookie CSRF.
- **Sessão** (`GET /api/v1/auth/me`): retorna o usuário autenticado; sessão por
  cookie HttpOnly `__Host-delta_session` na release TLS ou `delta_session` no
  modo de compatibilidade `lan-http`, 30 min de inatividade + 12 h absolutas.
- **Alteração de senha** (`PUT /api/v1/auth/password`): exige senha atual,
  aplica a política e encerra as demais sessões do usuário.
- **Recuperação assistida de senha** (`AssistedPasswordResetService` +
  `AssistedPasswordResetRunner`): fluxo operacional fora do HTTP (CLI), ativado
  por configuração, gera senha temporária e audita a operação (FR-139).

**Telas:** [Login](screens.md#login), [Cadastro](screens.md#cadastro),
[Conta (configurações)](screens.md#conta-configurações).

---

## 2. Isolamento e autorização por usuário

**Módulo backend:** `security` · aplicado em todos os módulos

Todo recurso financeiro pertence a exatamente um usuário; a autorização é
verificada no servidor em toda operação.

- **`AuthenticatedUser`**: identidade derivada exclusivamente da sessão; nenhum
  controller aceita `user_id` do cliente.
- **`OwnershipGuard`**: verifica posse por `(user_id, id)`; acesso cruzado é
  negado como 404 (nunca 403), para impedir sondagem de existência, e audita
  `AUTHORIZATION_DENIED`.
- **`SecurityConfig`**: CSRF por cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`;
  CSP estrita, HSTS em TLS, `frame-options: deny`, referrer policy e permissions policy;
  endpoints públicos restritos a `/auth/csrf`, `/auth/register`, `/auth/login`,
  health/info do actuator e Swagger; CORS desabilitado.
- **`UserPrincipal`** e **`SessionAttributes`**: principal com username
  normalizado; atributo `STARTED_AT` para o timeout absoluto.

---

## 3. Criptografia e proteção de dados em repouso

**Módulo backend:** `shared/crypto`, `shared/config`

Campos financeiros sensíveis são cifrados em repouso; metadados permanecem
pesquisáveis.

- **`CryptoService`**: AES-256-GCM; ciphertext `[format][keyVersion][nonce][dados]`
  com AAD vinculando `user_id`, tabela, coluna e id do registro.
- **`KeyRing`**: chaves carregadas de arquivo JSON externo (secreto/read-only);
  chaves separadas por finalidade `data` e `index`; versões antigas mantidas para
  descriptografia; versão ativa cifra valores novos.
- **`IndexHmacService`**: HMAC-SHA-256 para índices de busca, deduplicação e
  fingerprint (username, IP, ids externos, `dedupKey`, `fingerprint`).
- **`KeyRotationJob` / `KeyRotationRunner`**: rotação online de chaves de dados
  (batch, idempotente); runner CLI (`delta.crypto.rotation.run=true`).

---

## 4. Integração Meu Pluggy

**Módulo backend:** `integrations` · **Frontend:** `features/integration`

Configuração individual do Meu Pluggy (modalidade pessoal), validação de
credenciais e desconexão.

- **Configuração** (`GET/PUT /api/v1/integrations/pluggy`): Client ID e Client
  Secret por usuário; Client Secret é write-only (nunca retornado); credenciais
  novas só substituem as anteriores após validação bem-sucedida no provedor.
- **Validação** (`POST /api/v1/integrations/pluggy/validate`): valida credenciais
  e conexão sem salvar.
- **Desconexão** (`DELETE /api/v1/integrations/pluggy?deleteImportedData=`):
  remove credenciais, interrompe sincronizações e, opcionalmente, apaga dados
  importados (nunca dados manuais).
- **`PluggyAuthClient`**: obtém API Key no servidor (`POST /auth`); a chave fica
  apenas em cache em memória (`ApiKeyCache`, TTL 110 min) — nunca persistida nem
  retornada.
- **`PluggyDataClient`**: leitura de itens, recursos, contas, cartões, faturas
  (bills), transações (`/v2/transactions`) e investimentos; paginação (pageSize 500) e tratamento de erros sanitizado.
- **`PluggyRateLimiter`**: token bucket por endpoint (240/min) com backoff
  exponencial.
- **`IntegrationExceptionHandler`**: mapeia falhas do provedor para `502`
  (`PROVIDER_AUTH_FAILED`, `PROVIDER_RATE_LIMITED`, `PROVIDER_UNAVAILABLE`) e
  `422` para item inválido.

**Telas:** [Integração](screens.md#integração), [Privacidade](screens.md#privacidade).

---

## 5. Conexões (itens) e importação de dados bancários

**Módulo backend:** `connections` + `accounts`/`transactions`/`invoices` · **Frontend:** `features/integration`

Cadastro e manutenção dos itemIds (conexões) e importação de contas, cartões e
transações.

- **CRUD de conexões** (`/api/v1/connections`): listar, adicionar (`POST`, valida
  o item no provedor antes de gravar), atualizar itemId (`PATCH`, reconcilia o
  histórico), renomear/restaurar nome (`PUT/DELETE /{id}/display-name`), remover
  (`DELETE /{id}?deleteHistory=`), sincronizar sob demanda (`POST /{id}/sync`,
  aceito com 202). Limite de conexões em configuração (`delta.pluggy.connectionLimit=5`).
- **`AccountImportService`**: importa contas/cartões com upsert por
  `(connection_id, external_account_id_hash)`; contas ausentes numa leitura
  completa ficam `UNAVAILABLE`; infere ciclo do cartão a partir das Bills quando o
  provedor não informa `balanceCloseDate`/`balanceDueDate`.
- **`TransactionImportService`**: importação idempotente; deduplicação por HMAC
  (provedor+conexão+conta+id externo); reconciliação por `fingerprint` quando o id
  externo muda; `PENDING→POSTED` atualiza o mesmo registro; leitura conclusiva não
  vazia marca ausentes como `SOURCE_REMOVED` (e restaura ao reaparecer); normaliza
  sinal de cartão de crédito e categoria de transferência; preserva campos
  corrigidos pelo usuário.
- **`ItemReconciliationService`**: troca de itemId sem perder histórico local.
- **Limites e tolerância a falhas**: falha de um usuário/conexão não bloqueia os
  demais; resposta vazia é `EMPTY` (nunca exclusão).

**Telas:** [Integração](screens.md#integração), [Contas](screens.md#contas),
[Cartões](screens.md#cartões).

---

## 6. Sincronização programada e recuperação

**Módulo backend:** `connections` (serviços de sync) · **Frontend:** `SyncStatusPanel`

Fila durável de sincronização com retry, estado e histórico.

- **`SyncJobService`**: fila `sync_job` com claim atômico (`FOR UPDATE SKIP
LOCKED`), lock por integração, retry com backoff exponencial (honra
  `Retry-After`), recuperação de jobs órfãos no startup e timeout com watchdog.
- **`SyncScheduler`**: janela diária de 7 dias por conexão + reconciliação mensal
  de 12 meses, com jitter; isolamento de falha por conexão. Parâmetros em
  `delta.sync.*`.
- **`PluggySyncService`**: orquestra a leitura completa (estado do item →
  consentimento → contas/cartões → transações → investimentos) e produz
  `SyncRun` + métricas.
- **`SyncStateService`**: máquina de estados e mapeamento de erros
  (`CREDENTIAL_FAILURE`, `USER_AUTHORIZATION_EXPIRED`, `CONSENT_EXPIRED`,
  `LOGIN_ERROR`, `RATE_LIMITED`, `TIMEOUT`, `PROVIDER_UNAVAILABLE`,
  `ITEM_UNAVAILABLE`).
- **Consulta** (`/api/v1/sync/status` e `/api/v1/sync/runs`): estado geral, fila
  pendente e histórico paginado de execuções; `lastSuccessfulSyncAt` considera
  `SUCCESS`/`EMPTY` como sucesso.
- **`SyncMetrics`**: contadores/timers Micrometer (`delta.sync.*`).

**Telas:** [Integração](screens.md#integração) (painel de status).

---

## 7. Contas (manuais e importadas)

**Módulo backend:** `accounts` · **Frontend:** `features/accounts`

Contas bancárias, dinheiro e carteira, importadas ou manuais.

- **Listagem/detalhe** (`GET /api/v1/accounts`, `GET /api/v1/accounts/{id}`).
- **Conta manual** (`POST /api/v1/accounts`, `PATCH /api/v1/accounts/{id}`): tipo
  (`BANK`, `CASH`, `WALLET`), apelido, saldo de referência, data de referência,
  observações e status (`ACTIVE`/`INACTIVE`). O tipo é imutável após a criação;
  conta desativada não recebe novos lançamentos mas preserva histórico.
- **Nome de exibição** (`PUT/DELETE /api/v1/accounts/{id}/display-name`): renome
  local de contas importadas sem sobrescrever o nome original do provedor.
- **`AccountBalanceService`**: saldo efetivo da conta manual = saldo de referência
  - transações `POSTED`/`RECONCILED` posteriores + pagamentos de fatura
    confirmados sem transação de origem (evita dupla contagem). Contas importadas
    mantêm o saldo do provedor.
- **Saldos reservados** (`GET /api/v1/accounts/{id}/reserved-balances`): cofrinhos,
  caixinhas e bloqueios informados em `bankData.reservedBalances`. A reserva não é
  conta nem investimento: é dinheiro dentro da conta, apenas earmarked, e não vira
  transação, aporte, receita ou despesa.
  - O total reservado é exibido separado do saldo e **não** é somado
    automaticamente ao saldo disponível. `hasReservedBalance=true` com lista
    vazia/nula é lacuna explícita, nunca zero estimado.
  - O nome local (`PUT/DELETE /api/v1/reserved-balances/{id}/display-name`) não
    sobrescreve o nome do provedor; várias reservas e várias faixas de remuneração
    na mesma conta são somadas e preservadas.
  - **Tratamento no patrimônio** (`PUT
/api/v1/accounts/{id}/reserved-balance-treatment`): `UNKNOWN` não soma,
    `INCLUDED_IN_BALANCE` apenas detalha um saldo que já contém a reserva e
    `SEPARATE_ASSET` soma a reserva como ativo separado. O padrão pode ser definido
    por conexão (`PUT /api/v1/connections/{id}/reserved-balance-treatment`); a
    conta sem override herda o padrão e informa a origem em
    `reservedBalanceTreatmentSource` (`ACCOUNT`, `CONNECTION` ou `UNKNOWN`).
  - **Importação:** itens Open Finance usam o endpoint dedicado
    `/accounts/{id}/reserved-balances` como fallback quando o inline vem vazio;
    itens Meu Pluggy não o chamam. Ausência numa leitura completa marca a reserva
    como removida sem apagar o histórico local.
  - A remuneração contratada e a evolução mensal (`GET
/api/v1/wealth/reserved-balances/{id}/history`) aparecem no detalhe da conta;
    o histórico deriva dos snapshots patrimoniais e não calcula rendimento.
  - Reservas entram na exportação e na exclusão de conta; não entram no Dashboard
    nem no relatório de aportes.

**Telas:** [Contas](screens.md#contas), [Detalhe da conta](screens.md#detalhe-da-conta),
[Nova/Editar conta manual](screens.md#novaeditar-conta-manual).

---

## 8. Cartões de crédito

**Módulo backend:** `accounts` (perfil) + `invoices` (overview) · **Frontend:** `features/cards`

Painel de cartões separado das contas.

- **Perfil do cartão** (`PUT /api/v1/cards/{accountId}/profile`): dia de
  fechamento, dia de vencimento (1–31) e limite de crédito.
- **Visão geral** (`GET /api/v1/cards/overview`): total devido, limite, utilizado,
  utilização percentual, próximo vencimento e itens por cartão.
- Cartões sem ciclo configurado exibem transações não alocadas, sem faturas
  fictícias.

**Telas:** [Cartões](screens.md#cartões), [Detalhe do cartão](screens.md#detalhe-do-cartão).

---

## 9. Transações e lançamentos manuais

**Módulo backend:** `transactions` · **Frontend:** `features/transactions`

Movimentos financeiros de contas e cartões, importados ou manuais.

- **Listagem** (`GET /api/v1/transactions`): filtros por conta, categoria,
  status, tipo, origem, período e faixa de valor; paginação por cursor
  (`posted_on desc, id desc`).
- **Lançamento manual** (`POST /api/v1/transactions`): receita, despesa,
  transferência, estorno/reembolso, chargeback e ajuste, com sinal normalizado por
  tipo. Transferência cria duas pernas vinculadas + `internal_match` já confirmado.
- **Correção** (`PATCH /api/v1/transactions/{id}`): descrição, data, valor,
  categoria, observações e transação relacionada; valores originais da origem
  preservados em auditoria; campos corrigidos prevalecem sobre sincronizações.
- **Exclusão** (`DELETE /api/v1/transactions/{id}`): apenas lançamentos manuais.
- **`RefundMatcher`**: vincula reembolso/chargeback à despesa original (mesma
  conta, ≤90 dias, magnitude compatível, tokens de descrição; ambiguidade ⇒ sem
  vínculo automático).
- **Idempotência**: `POST`/`PATCH`/`DELETE` de transações usam `Idempotency-Key`.

**Telas:** [Transações](screens.md#transações), [Novo lançamento](screens.md#novo-lançamento-manual).

---

## 10. Categorias

**Módulo backend:** `categories` · **Frontend:** `features/categories`

Classificação financeira predefinida e personalizada por usuário.

- **CRUD** (`/api/v1/categories`): listar, criar, editar (`PATCH`) e desativar
  (`DELETE ?reassignTo=`).
- 16 categorias predefinidas (Moradia, Alimentação, Transporte, Saúde, Educação,
  Lazer, Compras, Serviços, Salário, Rendimentos de investimentos, Investimentos,
  Aplicações, Resgates, Transferências, Transferências externas, Outros); nomes
  cifrados, unicidade por HMAC normalizado.
- **Taxonomia de importação** (`CategoryTaxonomy`): rótulos conhecidos do provedor
  são mapeados para a categoria predefinida correspondente; nomes não mapeados ou
  ausentes usam `Outros`. `Proceeds interests and dividends` usa
  `Rendimentos de investimentos`. `Same person transfer`, `Transfer - Internal` e
  `Credit card payment` usam `Transferências` (internas, fora dos totais).
  `Transfers`, `Transfer - TED/PIX/DOC/Bank slip` e `Third-party transfers` usam
  `Transferências externas` e seguem a direção: crédito é receita, débito é
  despesa. Categorias de investimento do provedor (`Investments`, `Automatic
investment`, `Fixed income`, `Mutual funds`, `Variable income`, `Margin`,
  `Pension`) são movimentos patrimoniais: débito de corretagem vira `Aplicações`,
  crédito de venda/resgate vira `Resgates`, ambos `kind=TRANSFER` e fora de
  receitas/despesas. O conceito de **aporte** (caixa que entra no perímetro de
  investimento) continua no relatório de investimentos; ele não é a categoria do
  débito de corretagem. `Proceeds
interests and dividends` continua em `Rendimentos de investimentos`. Novos
  imports não criam categorias soltas.
- **Créditos em categorias de despesa:** em conta bancária, um `CREDIT` com
  categoria de despesa (por exemplo, devolução de mercado) é direcionado para
  `Outros`, para não aparecer como despesa no gráfico de receitas. Em cartão, o
  crédito permanece na categoria original e reduz a despesa correspondente. Como
  salvaguarda, a leitura do Dashboard também agrupa em `Outros` qualquer receita
  antiga ou manual que ainda esteja sob categoria de despesa.
- **Backfill** (`CategoryConsolidationService`): reatribui transações de
  categorias importadas antigas, registra `CATEGORY_MERGED` e remove a origem
  vazia. Pode ser executado em modo dry-run ou aplicação. A divisão
  interna/externa de transferências usa o histórico do provedor; no banco local
  foi aplicada com backup. Ambientes já existentes precisam de re-sync amplo ou
  de um backfill equivalente para reclassificar o histórico.
- Desativação reatribui transações via JDBC (marca `edited_fields` com
  "category") sem apagar lançamentos.
- `kind`: `INCOME`, `EXPENSE`, `TRANSFER`, `OTHER`.

**Telas:** [Categorias](screens.md#categorias).

---

## 11. Faturas de cartão

**Módulo backend:** `invoices` · **Frontend:** `features/invoices`

Ciclos, itens, parcelas, pagamentos e conciliação com a Bill oficial do provedor.

- **Faturas por cartão** (`GET /api/v1/cards/{accountId}/invoices`) e **detalhe**
  (`GET /api/v1/invoices/{id}`).
- **Geração** (`InvoiceGenerationService`): organiza compras por ciclo; apenas
  transações `POSTED`/`RECONCILED` viram itens; compras parceladas geram parcelas
  por ciclo; pagamentos de fatura são reconhecidos como `InvoicePayment`, não como
  espelho; deduplicação de pagamentos por HMAC (cartão|valor|data|descrição).
- **Itens** (`POST /api/v1/invoices/{id}/items`, `PATCH/DELETE /api/v1/invoice-items/{id}`):
  tipos `PURCHASE`, `INSTALLMENT`, `INTEREST`, `FEE`, `TAX`, `CREDIT`, `REFUND`,
  `MANUAL_ADJUSTMENT` com `reporting_effect` (`EXPENSE_INCREASE`,
  `EXPENSE_DECREASE`, `NONE`). Itens espelho de transação não são somados duas
  vezes; `PURCHASE`/`INSTALLMENT` exigem transação vinculada.
- **Correções auditáveis** (`GET /api/v1/invoice-items/{id}/corrections`): valores
  anteriores/motivo registrados.
- **Pagamentos** (`POST /api/v1/invoices/{id}/payments`): total ou parcial, a
  partir de conta ativa, que não seja cartão de crédito e use a mesma moeda da
  fatura; reduz saldo devedor sem criar despesa; excedente vira crédito para
  faturas futuras. O servidor valida a compatibilidade mesmo que a UI já filtre
  as contas.
- **Conciliação com a Bill** (`POST /api/v1/invoices/{id}/reconcile-provider`):
  ajuste manual auditado para alinhar total local ao oficial; a Bill nunca altera
  a fatura local.
- **`InvoiceBalanceService`**: recalcula total/saldo devedor/crédito aplicado e
  status (`OPEN`, `CLOSED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`); fatura paga não
  é reaberta; crédito aplicado à primeira fatura não paga.
- **`CardCycleService`** e **`InstallmentService`**: cálculo de ciclo local e
  chave de plano de parcelamento.

**Telas:** [Faturas do cartão](screens.md#faturas-do-cartão),
[Detalhe da fatura](screens.md#detalhe-da-fatura).

---

## 12. Reconciliação de movimentos internos

**Módulo backend:** `reconciliation` · **Frontend:** `features/reconciliation`

Detecção assistida de transferências entre contas próprias e pagamentos de
fatura, com confirmação explícita do usuário.

- **Sugestões** (`GET /api/v1/internal-matches?status=`): critérios
  determinísticos (contas diferentes, sinais opostos, tolerância
  `max(R$0,01, 0,5%)`, janela ±3 dias transferência / ±7 dias pagamento, score
  50/30/20, limiar 80, `criteriaVersion`).
- **Confirmação/rejeição** (`POST /api/v1/internal-matches/{id}/confirm|reject`):
  confirmar marca ambos os lados como `MATCHED` e publica
  `InternalMatchConfirmedEvent`; somente confirmados saem de receitas/despesas.
- Transferência manual já nasce como `internal_match` confirmado (score 100).

**Telas:** [Reconciliação](screens.md#reconciliação).

---

## 13. Dashboard e relatórios

**Módulo backend:** `reporting` · **Frontend:** `features/dashboard`

Consolidação financeira com totais, categorias, série temporal e drill-down.

- **Sumário** (`GET /api/v1/dashboard/summary`): `periodResult` (receitas −
  despesas), `income`, `expenses`, `currentBalance` (apenas contas ativas
  `BANK`/`CASH`/`WALLET` em BRL), `cardOutstandingBalance` (cartões em BRL;
  outras moedas não entram no consolidado), pendentes
  (`pendingCount`/`pendingIncome`/`pendingExpenses`), `pendingInternalMatches`,
  `lastSuccessfulSyncAt` e o indicador de investimentos
  (`investmentContributions`, `investmentWithdrawals`,
  `netInvestmentContributions`, `investmentTrackingAvailable`).
- **Aportes no Dashboard:** o indicador usa o mesmo relatório de
  `GET /api/v1/investments/contributions`, respeita o período selecionado e não
  entra em receitas nem despesas. Créditos em conta marcada como investimento
  deixam de ser receita mesmo sem conciliação confirmada; categorias `INCOME`
  (proventos) continuam entrando.
- **Categorias** (`GET /api/v1/dashboard/categories`): distribuição de despesas
  (`direction=EXPENSE`, padrão) ou receitas (`direction=INCOME`). Transferências
  internas, Aplicações e Resgates continuam fora dos dois totais; transferências
  externas entram como receita (crédito) ou despesa (débito) até serem
  conciliadas como movimento interno. Categorias usadas nos dois sentidos são
  rotuladas pelo gráfico (`Outros gastos`/`Outras receitas`,
  `Transferências externas (saídas)`/`(entradas)`), sem duplicar valores.
- **Série temporal** (`GET /api/v1/dashboard/timeseries`): buckets por período.
- **Drill-down** (`GET /api/v1/reports/entries`): projeção `ReportEntry`
  (`TRANSACTION`/`INVOICE_ITEM`) com a mesma regra dos totais; a soma da lista é
  idêntica ao indicador. Aceita `direction=EXPENSE|INCOME` para que o detalhe de
  uma categoria bidirecional mostre apenas o lado do gráfico.
- **Regras financeiras** (`ReportingService`): apenas `POSTED`/`RECONCILED`;
  transferências e matches internos confirmados excluídos; itens espelho não
  contados em duplicidade; reembolsos reduzem despesa até o valor original
  (excedente vira crédito); períodos calendário America/Sao_Paulo com semana
  iniciada na segunda; filtros `from`/`to` incluem o dia final.
- **`MoneyAggregator`** e **`DrillDownService`**: agregação em string decimal e
  drill-down paginado.

**Telas:** [Dashboard](screens.md#dashboard).

---

## 14. Investimentos

**Módulo backend:** `investments` · **Frontend:** `features/investments`

Importação automática de posições e proventos; complemento manual de proventos e
custo informado. Sem recomendação de investimento.

- **Visão geral** (`GET /api/v1/investments/overview`), **posições**
  (`GET /api/v1/investments/holdings?status&assetClass`),
  **detalhe** (`GET /api/v1/investments/holdings/{id}`),
  **fontes/cobertura** (`GET /api/v1/investments/sources`),
  **proventos** (`GET /api/v1/investments/income?from&to`).
- **Importação** (`InvestmentImportService`): somente para itens cujos
  `/items/{id}/resources` declaram recursos de investimento; valor atual = `balance`
  do provedor; posições `TOTAL_WITHDRAWAL`/`RETIRADA_TOTAL` normalizadas para
  `CLOSED`; ausência em leitura completa marca indisponível sem apagar.
- **Proventos manuais** (`POST/PATCH/DELETE /api/v1/investments/income`): tipos
  `DIVIDEND`, `INTEREST`, `JCP`, `RENT`, `COUPON`, `OTHER`, vinculados a posição
  importada.
- **Custo informado e classe de ativo** (`PATCH /api/v1/investments/holdings/{id}/cost-basis`
  e `.../asset-class`): complementos manuais identificados como estimados, sem
  sobrescrever valores do provedor.
- **Custo derivado**: quando não há custo manual nem `amountOriginal`, a soma dos
  `BUY` vira custo se a soma das quantidades compradas conferir com a quantidade
  atual da posição; a origem aparece como `DERIVED` e não inclui corretagem.
- **Aportes** (`GET /api/v1/investments/contributions?from&to&connectionId`):
  separa aporte (crédito externo na conta marcada), resgate (débito para fora),
  aplicação (`BUY`), custos e saldo ocioso. A conta de caixa do perímetro é
  marcada em `PUT /api/v1/accounts/{id}/investment`; sem marca, o sistema não
  infere perímetro: os fluxos bancários ficam zerados, `trackingAvailable=false`
  e o relatório sinaliza `NOT_CONFIGURED`. A aba `Aportes` mostra o relatório
  por período, conciliação do caixa, perímetro incluído e lançamentos; o mesmo
  endpoint alimenta o indicador do Dashboard, que não trata aporte como receita
  nem despesa.
- **Classificação** (`InvestmentClassification`): user override → type/subtype do
  provedor → heurística por código → `OUTRO`.
- **Alocação**: `overview.allocation` agrega valor, quantidade e percentual por
  classe; a UI mostra um donut com o total no centro e abre as posições da classe
  em drawer ao clicar na legenda.
- **Histórico** (`GET /api/v1/investments/history`): série mensal derivada dos
  snapshots patrimoniais, com total e alocação por classe; não calcula
  rentabilidade.
- Cobertura por conexão (`ProviderInvestmentScope`): `AVAILABLE`, `NOT_SHARED`,
  `PENDING_AUTHORIZATION`, `UNKNOWN`, `ERROR`.

**Telas:** [Investimentos](screens.md#investimentos).

---

## 15. Patrimônio

**Módulo backend:** `wealth` · **Frontend:** `features/wealth`

Visão consolidada de patrimônio bruto, passivos, patrimônio líquido e snapshots
mensais imutáveis.

- **Visão geral** (`GET /api/v1/wealth/overview`): `grossAssets`, `liabilities`,
  `netWorth`, `completeness` (`COMPLETE`/`PARTIAL`/`ESTIMATED`) e itens por fonte.
- **Histórico** (`GET/POST /api/v1/wealth/history`,
  `GET /api/v1/wealth/history/{id}`): snapshots mensais com totais, composição,
  completude, moeda original e itens excluídos. Captura manual opcional; job
  mensal no primeiro dia. Sem backfill retroativo.
- **Histórico de investimentos** (`GET /api/v1/investments/history`): série
  mensal derivada dos snapshots, com total e alocação por classe; não calcula
  rentabilidade.
- **Itens manuais** (`GET/POST/PATCH/DELETE /api/v1/wealth/manual-items`): ativo ou
  passivo com tipo, descrição, valor de referência, data e observações.
- **Regras** (`WealthService`): contas ativas `BANK`/`CASH`/`WALLET` em BRL são
  ativos; cartões em BRL entram como passivo pelo saldo devedor das faturas
  (nunca por compra, sem dupla contagem); cartões em outra moeda ficam fora do
  consolidado com motivo explícito; investimentos importados + itens manuais
  contados uma única vez; lacunas e moeda estrangeira explicitadas; referência
  > 30 dias marcada `ESTIMATED`. Investimentos em moeda diferente de BRL não
  > compõem o consolidado.
- **Saldos reservados** (`WealthSourceType.RESERVED_BALANCE`): só entram como
  ativo separado quando o tratamento efetivo da conta é `SEPARATE_ASSET` e a
  conta está ativa em BRL. Com `INCLUDED_IN_BALANCE`, a reserva é apenas
  detalhamento do saldo; com `UNKNOWN`, não soma. Reserva em outra moeda é
  excluída com motivo e `hasReservedBalance` sem detalhe vira lacuna explícita.
  O item é gravado no snapshot patrimonial e alimenta a evolução mensal da
  reserva, sem estimativa de rendimento.
- **Retenção:** snapshots expiram após 60 meses (configurável) e são removidos
  pelo job diário; exportação e exclusão de conta incluem snapshots e itens.

**Telas:** [Patrimônio](screens.md#patrimônio).

---

## 16. Metas

**Módulo backend:** `goals` · **Frontend:** `features/goals`

Objetivos financeiros com progresso por alocações ou patrimônio líquido. Não
movimenta dinheiro.

- **CRUD** (`/api/v1/goals`): nome, descrição, observações, tipo
  (`EMERGENCY_FUND`, `TRAVEL`, `PLANNED_PURCHASE`, `NET_WORTH`), valor-alvo,
  data-alvo opcional, regra de progresso (`ALLOCATIONS`/`NET_WORTH`) e alocações.
- **Status** (`POST /api/v1/goals/{id}/status`): `ACTIVE`, `ACHIEVED`, `PAUSED`,
  `ARCHIVED`, `OVERDUE`, `CHANGED`.
- **Alocações** (`GoalProgressService`): modo `FIXED_AMOUNT` (exato) ou
  `PERCENTAGE` (com arredondamento `floor` e resíduo na última parcela); soma das
  alocações de uma fonte entre metas não excede o saldo da fonte; fontes:
  `MANUAL`, `ACCOUNT`, `INVESTMENT`, `MANUAL_ASSET`.
- **Histórico**: um snapshot de progresso por meta/dia (`goal_progress_snapshot`),
  lido por `GET /api/v1/goals/{id}/progress?cursor&limit` em páginas de até 100
  (padrão 50), mais recente primeiro. A resposta da meta não carrega o histórico.

**Telas:** [Metas](screens.md#metas).

---

## 17. Privacidade e ciclo de vida

**Módulo backend:** `privacy` · **Frontend:** `features/privacy`

Exportação, desconexão e exclusão de conta.

- **Exportação** (`GET /api/v1/me/export`): ZIP em streaming com JSON Lines +
  CSV por conjunto de dados e `manifest.json`; valores descriptografados apenas
  para o titular; exclui segredos, HMACs e ids externos.
- **Exclusão de conta** (`POST /api/v1/me/delete`): confirma senha + palavra
  `EXCLUIR`; cancela jobs, desconecta integração, remove dados financeiros
  (filhos antes dos pais via JDBC), revoga sessões e deixa tombstone de auditoria
  sem dados financeiros.
- **Desconexão** (`DELETE /api/v1/integrations/pluggy?deleteImportedData=`).
- **Retenção** (`RetentionJob`, cron diário 03:30 America/Sao_Paulo): purga
  `login_attempt` (30 d), `sync_run` (90 d), `audit_event` (365 d) e
  `idempotency_key` expirada (24 h).

**Telas:** [Privacidade](screens.md#privacidade).

---

## 18. Fundações transversais (cross-cutting)

Componentes compartilhados que sustentam os módulos acima.

- **Tema e aparência** (`app/ThemeProvider`, `lib/theme/preferences`): temas
  `light`, `dark` e `system`, paletas curadas `blue`, `green`, `purple` e
  `amber`. A preferência é local ao navegador, em namespace por usuário, e não
  contém dados financeiros. A tela `/settings/preferences` aplica a escolha
  imediatamente, com prévia e contraste preservado.
- **Configurações** (`features/settings`): navegação secundária compartilhada
  entre Preferências, Integração, Privacidade e Conta, com URLs independentes.
- **Performance** (`app/lazy-pages.ts`, `RootLayout`, `AuthenticatedLayout`):
  páginas de negócio carregadas sob demanda por rota, com fallback de esqueleto
  dentro dos layouts. O chunk principal passou a ficar abaixo do limite de aviso
  de 500 kB; login, cadastro e Home pública continuam eager.
- **Auditoria visual** (`client/screenshots` e `client/e2e`): matriz de rotas em
  desktop/tablet/mobile, temas claro/escuro e estados de loading, vazio, erro,
  modal, drawer, dropdown, validação e ação em andamento.
- **Integração** (`features/integration`): estado geral derivado de todas as
  conexões, distinção entre falha temporária e ação necessária, histórico
  paginado com detalhes técnicos secundários e credenciais write-only.
- **Dinheiro** (`shared/money/Money`): `BigDecimal` obrigatório, escala 2 para
  BRL (`HALF_EVEN`), operações entre moedas diferentes falham; JSON sempre como
  string decimal (`MoneyJsonModule`).
- **Erros** (`shared/errors`): hierarquia `ApiException{status, code}` →
  `ValidationException` (422), `ConflictException` (409), `NotFoundException`
  (404), `InvalidCredentialsException`/`UnauthenticatedException` (401),
  `TooManyRequestsException` (429); `GlobalExceptionHandler` emite `ProblemDetail`
  RFC 9457 com `requestId` e erros por campo, sem vazar stack/SQL/segredos.
- **Idempotência** (`security/IdempotencyFilter`): `Idempotency-Key` para
  mutações críticas (transações, itens e pagamentos de fatura); reuso com corpo
  diferente → 409; resposta persistida por 24 h.
- **Auditoria** (`shared/audit`): `AuditService` grava eventos sanitizados
  (apenas IDs/códigos/contadores) em `audit_event`; 58 tipos em `AuditEventType`.
- **Paginação** (`shared/pagination/CursorPage`): cursor opaco, limite máximo 100.
- **Entidades** (`shared/persistence/BaseEntity`): id UUID gerado pela aplicação,
  `created_at`/`updated_at` UTC e `version` (locking otimista).
- **Relógio** (`shared/time/Clock`): abstração de relógio (injetável em testes).
- **Cancelamento** (`shared/sync/SyncCancellationRegistry`): encerramento seguro
  de sincronizações em curso (exclusão de conta/desconexão).
- **Logs** (`shared/logging`): campos MDC (`requestId`, `userId`, `event`,
  `outcome`, `errorCode`, `durationMs`) e `SensitiveDataMasker` (última linha de
  defesa contra vazamento de segredos).
- **Observabilidade** (`shared/config/ObservabilityConfig`): health do key ring,
  tags de métricas e info contributor.
