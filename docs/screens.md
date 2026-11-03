# Telas — Delta Finance

> Documentação descritiva das telas, componentes e sua relação com as
> funcionalidades. Documenta **o que já existe**; não propõe correções. As
> funcionalidades estão detalhadas em [`features.md`](features.md).

## Composição da aplicação

- **`client/src/app/App.tsx`**: raiz da SPA — `AppProviders` → `RouterProvider`.
- **`client/src/app/providers.tsx`**: único provider global é o
  `QueryClientProvider` (react-query: `retry: 1`, `staleTime: 30s`,
  `refetchOnWindowFocus: false`, `gcTime: 5min`; mutations `retry: 0`).
- **`client/src/app/router.tsx`**: `createBrowserRouter` com rota raiz `/`
  (`RootLayout`) e filhos listados abaixo.
- **`client/src/app/RootLayout.tsx`**: `AppShell` + `SessionExpiryListener` +
  `SessionExpiredBanner` + `<Outlet/>`.
- **`client/src/app/ProtectedRoute.tsx`**: usa `useSession()`; carregando →
  "Carregando sua sessão…"; erro 403 → "Acesso negado"; outro erro → "Tentar
  novamente" (+ `requestId`); sem usuário → redireciona para `/login` com
  `state.from`.
- **`client/src/components/layout/AppShell.tsx`**: layout base (desktop com
  sidebar; tablet com rail de ícones; mobile com barra superior e drawer). Itens
  de navegação: Dashboard, Contas, Cartões, Transações, Investimentos,
  Patrimônio, Metas, Categorias, Reconciliação, Preferências, Integração,
  Privacidade e Conta.

### Sessão e expiração

- **`client/src/features/auth/hooks/useSession.ts`**: query `['session']` sobre
  `GET /auth/me`; 401 → usuário `null` + limpeza de cache; `useSessionExpiryListener`
  controla inatividade (30 min) e timeout absoluto (12 h) no cliente e revalida em
  `focus`/`pageshow`/`visibilitychange`.
- **Evento global `delta:unauthenticated`** (`lib/api/client.ts`): disparado em
  401 (somente quando há sessão estabelecida); aciona o banner e limpa a sessão.
- **`SessionExpiredBanner`**: aviso "Sua sessão expirou…" (dispensável).

---

## Telas públicas

### Home (`/`)

- **Arquivo:** `app/HomePage.tsx` · acesso público.
- **Propósito:** página de apresentação; título/tagline, links "Entrar"
  (`/login`) e "Criar conta" (`/register`), seção "Módulos disponíveis" e link
  "Abrir dashboard" (`/dashboard`). Estática, sem dados.
- **Componentes:** apenas `Link` e ícone `Check` (lucide-react).
- **Funcionalidades:** nenhuma diretamente; porta de entrada.

### Login (`/login`)

- **Arquivo:** `features/auth/pages/LoginPage.tsx` · público.
- **Propósito:** autenticação. Ao entrar, navega para `state.from ?? '/dashboard'`.
- **Componentes:** `FormErrorSummary`.
- **Formulário** (react-hook-form + Zod `loginSchema`): `username`, `password`
  (obrigatórios). Erros de API mapeiam para campos; mensagem genérica para
  credenciais; `429` → "Muitas tentativas de login…" com retry-after.
- **API:** `useLogin()` → `POST /api/v1/auth/login`.
- **Estados:** resumo de erros + erros inline; botão "Entrando…" enquanto pendente;
  banner de sucesso quando veio de cadastro (`state.registered`).
- **Funcionalidade:** [Identidade, autenticação e sessão](features.md#1-identidade-autenticação-e-sessão).

### Cadastro (`/register`)

- **Arquivo:** `features/auth/pages/RegisterPage.tsx` · público.
- **Propósito:** criar conta; ao concluir navega para `/login` com
  `{registered, username}`.
- **Componentes:** `FormErrorSummary`.
- **Formulário** (Zod `registerSchema`): `username` (3–40) e `password` (≥12).
  `USERNAME_TAKEN` → erro no campo username.
- **API:** `useRegister()` → `POST /api/v1/auth/register`.
- **Estados:** resumo + erros inline; "Criando conta…".
- **Funcionalidade:** [Identidade, autenticação e sessão](features.md#1-identidade-autenticação-e-sessão).

### Página não encontrada (`*`)

- **Arquivo:** `app/NotFoundPage.tsx` · público.
- **Propósito:** 404 com links para `/` e `/login`. Estática.

---

## Dashboard

### Dashboard (`/dashboard`)

- **Arquivo:** `features/dashboard/pages/DashboardPage.tsx` · protegida.
- **Propósito:** visão financeira do período — resultado em destaque, receitas,
  despesas, saldo em contas, dívida de cartões, pendências, sincronização,
  evolução, despesas por categoria e últimas movimentações.
- **Hierarquia:** cabeçalho + período; hero do resultado; indicador de aportes do
  período; indicadores secundários; painel “Precisa de atenção”; evolução;
  categorias; movimentações.
- **Filtros:** barra compacta com período (`WEEK`/`MONTH`/`YEAR`), intervalo
  personalizado e chips de filtros ativos. Conta, categoria, tipo, origem e faixa
  de valor ficam em drawer. Período, contas, categoria, tipo, origem e valores
  são persistidos em search params (`period`, `from`, `to`, `accountId`,
  `categoryId`, `type`, `origin`, `minAmount`, `maxAmount`), preservando voltar,
  avançar e recarregar.
- **Categorias:** seção com abas `Despesas` e `Receitas`; ambas usam donut,
  tabela acessível e drill-down preservando filtros/período. Categorias
  bidirecionais recebem o sentido na legenda: `Outros gastos` × `Outras
receitas` e `Transferências externas (saídas)` × `(entradas)`. A categoria
  `Transferências` é reservada a movimentos internos e não aparece nos totais;
  `Aplicações` e `Resgates` também ficam fora.
- **Componentes:**
  - `DashboardOverview`: hero do resultado e indicadores secundários, com data de
    referência do saldo e dívida de cartões separada. Quando o rastreamento de
    investimentos está disponível, exibe “Aportes no período” (ou “Resgates
    líquidos no período”), explicação de que não é despesa e link para
    `/investments?tab=contributions`.
  - `DashboardFilters`: período/intervalo visíveis; filtros avançados em `Drawer`;
    chips removíveis; validação de intervalo e faixa de valor.
  - `DashboardAttention`: pendências, reconciliações e estado de sincronização
    agregado de todas as conexões (`/sync/status`). Diferencia falha temporária de
    autorização expirada/credencial inválida e não expõe códigos internos.
  - `TimeSeriesChart`: SVG com seleção de séries, pontos navegáveis por teclado,
    tooltip com receitas/despesas/resultado e tabela equivalente. Acima de 120
    buckets, agrega apenas a visualização; a tabela mantém todos os períodos.
  - `CategoryChart`: sempre donut para despesas ou receitas, com top 8 +
    “Outras” quando houver mais categorias. Cada categoria tem ícone/cor
    determinística, nome, valor e participação; a tabela completa fica em
    disclosure.
  - `DashboardCategoryDrawer`: lista completa de categorias com drill-down.
  - `DashboardDrillDownDrawer`: detalhamento contextual em drawer, sem perder
    filtros ou período; paginação por cursor.
  - `DashboardMovements`: últimas movimentações compactas, com categoria, conta,
    data, origem e valor; abre o detalhe da transação/fatura ou a lista completa.
  - `DashboardStates`: skeleton, erro geral, erro parcial, período sem dados,
    filtros sem resultado e referência de saldo desatualizada/ausente.
- **API:** `dashboardApi.summary` → `GET /api/v1/dashboard/summary`;
  `dashboardApi.categories` → `GET /api/v1/dashboard/categories`;
  `dashboardApi.timeseries` → `GET /api/v1/dashboard/timeseries`;
  `dashboardApi.entries` → `GET /api/v1/reports/entries` (infinite query para as
  últimas movimentações e para o drill-down).
- **Estados:** carregamento com skeleton; erro geral com retry; falhas parciais de
  categorias/série/movimentações sem derrubar os indicadores; período vazio e
  filtros sem resultado com mensagens e ações distintas; sincronização pendente,
  ação necessária ou indisponível.
- **Acessibilidade:** um `h1`; seções com `h2`; gráficos com resumo e tabela;
  drawer com foco preso, `Escape`, retorno de foco e fundo inerte; pontos do
  gráfico alcançáveis por teclado; cores de categoria não são o único canal.
- **Funcionalidade:** [Dashboard e relatórios](features.md#13-dashboard-e-relatórios).

---

## Contas

### Contas (`/accounts`)

- **Arquivo:** `features/accounts/pages/AccountsPage.tsx` · protegida.
- **Propósito:** saldos de contas não-crédito, agrupados por instituição, com
  total consolidado apenas de contas ativas em BRL e seção secundária para contas
  inativas ou em outra moeda. O total do hero soma apenas `currentBalance`; saldos
  reservados aparecem como informação separada.
- **Componentes:** `AccountCard` (local) — apelido, tipo, origem, saldo atual,
  total reservado quando houver, referência/data para conta manual, status,
  atualização e links para detalhe e transações. `PageHeader`, `Card`, `Badge`,
  `EmptyState`, `Skeleton`.
- **Organização:** total no hero; grupos por instituição em “Contas em uso”;
  “Outras contas” em disclosure, sem competir com o saldo principal. Contas com
  reserva exibem o badge `Reserva` e a linha “Reservado: …” abaixo do saldo.
- **API:** `accountsApi.accounts` → `GET /api/v1/accounts`.
- **Estados:** skeleton; erro; vazio; aviso de moeda estrangeira; total ausente
  quando não há conta elegível (nunca zero artificial).
- **Funcionalidades:** [Contas](features.md#7-contas-manuais-e-importadas),
  [Cartões](features.md#8-cartões-de-crédito).

### Detalhe da conta (`/accounts/:id`)

- **Arquivo:** `features/accounts/pages/AccountDetailPage.tsx` · protegida.
- **Propósito:** detalhe de conta em abas `Visão geral`, `Movimentações` e
  `Configurações`, preservadas em `?tab=`.
- **Abas:**
  - `Visão geral`: saldo atual e, quando o tratamento é `SEPARATE_ASSET`,
    “Total com reservas”; seção `Saldos reservados` com total, seletor de
    tratamento no patrimônio (`UNKNOWN`, `INCLUDED_IN_BALANCE`, `SEPARATE_ASSET`),
    indicação de tratamento herdado da conexão e aviso de lacuna quando o
    provedor sinaliza reserva sem detalhe; lista com nome exibido e original,
    valor, data de referência, remuneração contratada, badge de referência antiga,
    edição inline do nome e disclosure `Evolução mensal da reserva`; dados da
    conta; alerta de moeda estrangeira.
  - `Movimentações`: últimas 20 transações.
  - `Configurações`: nome de exibição para conta importada ou edição para conta
    manual; em conta importada, marcação do perímetro de investimento
    (`investmentAccount`) com explicação de aportes, aplicações e resgates; origem
    dos dados.
- **API:** `GET /api/v1/accounts/{id}`;
  `GET /api/v1/transactions?accountId=&limit=20`;
  `PUT/DELETE /api/v1/accounts/{id}/display-name`;
  `GET /api/v1/accounts/{id}/reserved-balances`;
  `PUT /api/v1/accounts/{id}/reserved-balance-treatment`;
  `PUT/DELETE /api/v1/reserved-balances/{id}/display-name`;
  `GET /api/v1/wealth/reserved-balances/{id}/history`;
  `PUT /api/v1/accounts/{id}/investment`.
- **Contas de cartão:** `/accounts/:id` de um `CREDIT` redireciona para
  `/cards/:id`, preservando a rota antiga.
- **Estados:** skeleton; erro; abas vazias; conta em moeda estrangeira destacada;
  reservas carregando, indisponíveis ou sem detalhe; histórico da reserva vazio;
  marcação de investimento pendente ou com erro.
- **Funcionalidades:** [Contas](features.md#7-contas-manuais-e-importadas),
  [Cartões](features.md#8-cartões-de-crédito).

### Nova/Editar conta manual (`/accounts/new`, `/accounts/:id/edit`)

- **Arquivo:** `features/accounts/pages/ManualAccountPage.tsx` · protegida.
- **Propósito:** criar ou editar conta manual com divulgação progressiva.
- **Seções:** dados principais (`type`, `alias`, `status` na edição); saldo de
  referência (`referenceBalance`, `referenceDate`); observações recolhidas.
- **Regras:** tipo imutável após criação; conta importada não é editável.
- **API:** `POST /api/v1/accounts`; `PATCH /api/v1/accounts/{id}`.
- **Estados:** saving; mensagem de sucesso; erro preservando os campos.
- **Funcionalidade:** [Contas](features.md#7-contas-manuais-e-importadas).

---

## Cartões

### Cartões (`/cards`)

- **Arquivo:** `features/cards/pages/CardsPage.tsx` · protegida.
- **Propósito:** dívida total em BRL, limite, próximo vencimento e cartões
  individuais com saldo devedor, limite, disponível, utilização e vencimento.
- **Hierarquia:** hero de dívida; resumo sem recalcular utilização agregada;
  cards compactos com a utilização informada por cartão.
- **Multi-moeda:** cartões em outra moeda aparecem na moeda original e não entram
  no total em BRL.
- **Fatura:** “Fatura atual” somente quando `status=OPEN`; fechada/paga aparece
  como “Última fatura”.
- **API:** `GET /api/v1/cards/overview`.
- **Estados:** skeleton; erro; vazio; ciclo não configurado; compras não alocadas;
  fatura vencida.
- **Funcionalidade:** [Cartões](features.md#8-cartões-de-crédito).

### Detalhe do cartão (`/cards/:accountId`)

- **Arquivo:** `features/cards/pages/CardDetailPage.tsx` · protegida.
- **Propósito:** página do cartão em abas `Resumo`, `Faturas`, `Transações` e
  `Configurações`, preservadas em `?tab=`.
- **Resumo:** saldo devedor, limite, disponível, utilizado, próximo vencimento,
  crédito acumulado, compras não alocadas e fatura atual/última.
- **Faturas:** fatura atual somente `OPEN`; histórico separado; link para a página
  completa de faturas.
- **Transações:** últimas transações do cartão.
- **Configurações:** identificação e ciclo (`CardCycleSettings`).
- **API:** `GET /api/v1/accounts/{id}`, `GET /api/v1/cards/overview`,
  `GET /api/v1/cards/{accountId}/invoices`,
  `GET /api/v1/transactions?accountId=&limit=100`.
- **Multi-moeda:** todos os valores usam `account.currency`.
- **Funcionalidades:** [Cartões](features.md#8-cartões-de-crédito),
  [Faturas](features.md#11-faturas-de-cartão).

---

## Transações

### Transações (`/transactions`)

- **Arquivo:** `features/transactions/pages/TransactionsPage.tsx` · protegida.
- **Propósito:** lista operacional agrupada por data, com busca local explícita,
  filtros avançados em drawer, detalhe em drawer e correção contextual.
- **Filtros:** `accountId`, `categoryId`, `status`, `type`, `origin`, `from`, `to`,
  `minAmount`, `maxAmount` na URL; validação de intervalo e faixa; chips ativos.
- **Busca:** somente client-side sobre os registros já carregados; a interface
  informa quantos registros foram considerados e não promete busca global.
- **Linha:** categoria com ícone/cor determinística, descrição, tipo, conta,
  status, valor alinhado e origem; ações secundárias em menu contextual.
- **Detalhe:** `TransactionDetailDrawer` com conta, categoria, origem, estado,
  conciliação, observações, vínculo de reembolso/chargeback, fatura,
  `editedFields`, correção e exclusão de lançamento manual.
- **API:** `GET /api/v1/transactions` (cursor, limite 50); `PATCH` e `DELETE`
  permanecem nos componentes existentes.
- **Estados:** skeleton; erro; sem transações; busca sem resultado; filtros sem
  resultado; carregar mais.
- **Funcionalidade:** [Transações](features.md#9-transações-e-lançamentos-manuais).

### Novo lançamento manual (`/transactions/new`)

- **Arquivo:** `features/transactions/pages/ManualEntryPage.tsx` · protegida.
- **Propósito:** wrapper do formulário de novo lançamento manual.
- **Componentes:** `ManualEntryForm`.
- **Fluxo:** tipo primeiro; resumo do efeito (entrada/saída/transferência);
  conta, valor, data, descrição e categoria; campos condicionais para
  transferência, reembolso/chargeback e ajuste; observações recolhidas.
- **API:** `POST /api/v1/transactions` com `Idempotency-Key` no cliente.
- **Estados:** validação antes do envio; erro preservando dados; sucesso.
- **Conta importada:** aviso explícito de que o lançamento entra no histórico e
  nos relatórios, mas não ajusta o saldo atual informado pelo provedor.
- **Funcionalidade:** [Transações](features.md#9-transações-e-lançamentos-manuais).

---

## Categorias

### Categorias (`/categories`)

- **Arquivo:** `features/categories/pages/CategoriesPage.tsx` · protegida.
- **Propósito:** gerenciar categorias predefinidas e personalizadas, agrupadas
  por tipo e com busca local.
- **Identidade:** `CategoryIcon` + cores determinísticas por `predefinedKey`/nome
  canônico ou hash estável do `id` para personalizadas; fallback `Tag`.
- **Taxonomia:** importações usam `CategoryTaxonomy`; nomes conhecidos são
  consolidados e nomes desconhecidos vão para “Outros”. A lista local foi
  reduzida de 47 para 16 categorias canônicas após o backfill (incluindo
  Rendimentos de investimentos, Aplicações, Resgates e Transferências externas).
  `Transferências` representa movimentos internos; `Transferências externas`
  representa TED/PIX/DOC com terceiros e entra em receitas ou despesas conforme
  a direção. `Aplicações` e `Resgates` são movimentos patrimoniais (aplicação e venda de
  ativo), ficam no grupo de transferências e não entram em receitas/despesas.
  O conceito de **aporte** continua no relatório de investimentos.
- **Formulários:** criação/edição em dialog (`name`, `kind`); desativação em
  dialog com reatribuição (`reassignTo`) e explicação do impacto.
- **API:** `GET/POST /api/v1/categories`, `PATCH/DELETE /api/v1/categories/{id}`.
- **Estados:** loading; erro; vazio; busca sem resultado; mensagens por dialog.
- **Funcionalidade:** [Categorias](features.md#10-categorias).

---

## Reconciliação

### Reconciliação (`/reconciliation`)

- **Arquivo:** `features/reconciliation/pages/ReconciliationPage.tsx` · protegida.
- **Propósito:** comparar as duas pernas de transferências internas e pagamentos
  de fatura, mostrando saída, entrada, conta, data, valor, diferença e ações.
- **Regras de confirmação:** liberada apenas quando as duas transações existem e
  estão na mesma moeda; confirmação explícita. Score, critérios e motivo técnico
  ficam em disclosure secundário.
- **API:** `GET /api/v1/internal-matches?status=PENDING&limit=50`,
  `GET /api/v1/transactions?limit=100`, `GET /api/v1/accounts`,
  `POST /api/v1/internal-matches/{id}/confirm|reject`.
- **Estados:** skeleton; erro; vazio; dados insuficientes; ação em andamento.
- **Funcionalidade:** [Reconciliação](features.md#12-reconciliação-de-movimentos-internos).

---

## Faturas

### Faturas do cartão (`/cards/:accountId/invoices`)

- **Arquivo:** `features/invoices/pages/InvoicesPage.tsx` · protegida.
- **Propósito:** faturas do cartão em abas `Fatura atual`, `Histórico`,
  `Compras pendentes` e `Configurações`, preservadas em `?tab=`.
- **Fatura atual:** somente `OPEN`; resumo de total, pago, saldo devedor,
  pendentes, mínimo e crédito aplicado.
- **Compras pendentes:** ciclo atual e lista separada de compras não alocadas e
  créditos/estornos não alocados. Créditos não aparecem como compras. Totais e
  contagens vêm de `GET /api/v1/cards/overview`; a lista é uma amostra de até 100
  itens e mostra “mostrando X de Y” com link para Transações.
- **Histórico:** fechadas/pagas, sem reescrita.
- **Configurações:** `CardCycleSettings` com a moeda do cartão.
- **Multi-moeda:** valores na moeda original do cartão.
- **API:** `GET /api/v1/accounts/{id}`, `GET /api/v1/cards/{cardAccountId}/invoices`,
  `GET /api/v1/cards/overview`, `GET /api/v1/transactions?accountId=&limit=100`.
- **Estados:** loading; erro de faturas; erro de transações com alerta próprio;
  sem fatura aberta; histórico vazio; não alocadas vazias.
- **Funcionalidade:** [Faturas](features.md#11-faturas-de-cartão).

### Detalhe da fatura (`/invoices/:id`)

- **Arquivo:** `features/invoices/pages/InvoiceDetailPage.tsx` · protegida.
- **Propósito:** detalhe de uma fatura — status, conciliação com a Bill oficial,
  compras pendentes, itens e pagamentos.
- **Hierarquia:** `PageHeader` fornece o único `h1` (“Detalhe da fatura” ou
  “Fatura paga”); `InvoiceStatus` usa `h2` para o ciclo e os totais.
- **Itens:** compras/encargos e créditos/estornos com sinal coerente com
  `reportingEffect`; crédito reduz a fatura e não é exibido como despesa.
- **Pagamentos:** `InvoicePaymentDialog` mostra fatura, saldo devedor, valor,
  conta de origem, data e crédito excedente. A seleção exclui contas inativas,
  cartões de crédito e moeda diferente da fatura; o servidor repete a validação
  em `InvoicePaymentService`.
- **API:** `GET /api/v1/invoices/{id}`, `GET /api/v1/accounts`,
  `GET /api/v1/transactions?accountId=&limit=200`,
  `DELETE /api/v1/invoice-items/{id}`,
  `GET /api/v1/invoice-items/{id}/corrections`.
- **Estados:** loading; erro; fatura paga sem ações; itens/pagamentos vazios;
  histórico incompleto; Bill divergente.
- **Funcionalidade:** [Faturas](features.md#11-faturas-de-cartão).

### Componentes de faturas

- **`CardCycleSettings`**: configura fechamento/vencimento/limite na moeda do
  cartão. Usa `PUT /api/v1/cards/{accountId}/profile`.
- **`InvoiceItemDialog`**: adiciona/edita item manual ou vínculo de transação.
- **`InvoicePaymentDialog`**: registra pagamento total/parcial em conta ativa e
  compatível; excedente vira crédito.
- **`InvoiceStatus`**: status, ajustes, totais, progresso e crédito acumulado.
- **`ProviderReconciliationDialog`**: conciliação auditada com a Bill oficial.

---

## Configurações

As áreas administrativas compartilham `SettingsLayout`
(`features/settings/pages/SettingsLayout.tsx`) e a navegação secundária
`SettingsNav`, com URLs independentes:

- `/settings/preferences`
- `/settings/integration`
- `/settings/privacy`
- `/account`

`/settings` redireciona para `/settings/preferences`. As rotas antigas
(`/settings/integration`, `/settings/privacy`, `/account`) foram preservadas.

### Preferências (`/settings/preferences`)

- **Arquivo:** `features/settings/pages/PreferencesPage.tsx` · protegida.
- **Propósito:** escolher tema (Sistema, Claro, Escuro) e paleta principal
  (Azul, Verde, Roxo, Âmbar), com pré-visualização.
- **Persistência:** apenas `theme` e `accent` em `localStorage`, namespaced por
  usuário (`delta.prefs.<userId>`; `guest` antes da sessão). Nenhum dado
  financeiro, credencial ou identificador adicional é gravado. Não há
  sincronização entre dispositivos.
- **Componentes:** `useTheme`, `Card`, `Button`, `Badge`, `Progress`.
- **Estados:** tema resolvido exibido; prévia com valores explicitamente
  ilustrativos; contraste preservado nas quatro paletas e nos dois temas.
- **Funcionalidade:** [Fundações transversais](features.md#18-fundações-transversais-cross-cutting).

### Integração (`/settings/integration`)

- **Arquivo:** `features/integration/pages/IntegrationSetupPage.tsx` · protegida.
- **Propósito:** acompanhar o estado da integração, gerenciar conexões, consultar
  o histórico e configurar credenciais do Meu Pluggy.
- **Hierarquia:** estado geral e sincronização; conexões; adicionar conexão;
  credenciais/limites em seção própria.
- **Componentes:** `SyncStatusPanel`, `ConnectionsList`, `ConnectionForm`,
  `ConnectionLimitNotice`, `CredentialsDialog`, `SecretField`.
- **Formulários:** credenciais em dialog (`clientId`, `clientSecret` write-only)
  com "Salvar credenciais" e "Validar sem salvar"; adição de conexão (`itemId`);
  atualização de autorização na própria conexão.
- **API:** `GET /api/v1/integrations/pluggy`, `GET /api/v1/connections`,
  `GET /api/v1/sync/status`, `GET /api/v1/sync/runs`,
  `PUT /api/v1/integrations/pluggy`, `POST /api/v1/integrations/pluggy/validate`.
- **Estados:** configuração incompleta, conectado, sincronizando, atualizado,
  falha temporária, credenciais recusadas, autorização expirada, ação necessária,
  conexão indisponível; erros de carregamento; histórico vazio.
- **Funcionalidades:** [Integração](features.md#4-integração-meu-pluggy),
  [Conexões](features.md#5-conexões-itens-e-importação-de-dados-bancários),
  [Sincronização](features.md#6-sincronização-programada-e-recuperação).

### Componentes de integração

- **`CredentialsDialog`**: formulário de credenciais em dialog acessível; limpa o
  segredo após envio e mantém os valores quando a API falha.
- **`ConnectionForm`**: adiciona `itemId`. Usa `POST /api/v1/connections`.
- **`ConnectionLimitNotice`**: "X de Y conexões configuradas" + aviso ao atingir.
- **`ConnectionsList`**: cards com instituição, status, última leitura, contas e
  consentimento. Ações seguras ("Sincronizar agora", "Atualizar autorização",
  renomear) ficam separadas da remoção; o dialog de remoção distingue desativar
  (preserva histórico) de excluir dados importados. Cada conexão também define o
  tratamento padrão de saldos reservados herdado pelas contas
  (`UNKNOWN`, `INCLUDED_IN_BALANCE`, `SEPARATE_ASSET`) via
  `PUT /api/v1/connections/{id}/reserved-balance-treatment`.
- **`SecretField`**: campo de senha write-only (nunca hidratado; sem localStorage).
- **`SyncStatusPanel`**: estado geral a partir de todas as conexões, última
  tentativa, última bem-sucedida, fila, ações necessárias, falhas temporárias e
  histórico paginado. Códigos de diagnóstico ficam em disclosure secundário.

---

## Investimentos

### Investimentos (`/investments`)

- **Arquivo:** `features/investments/pages/InvestmentsPage.tsx` · protegida.
- **Propósito:** acompanhar posições importadas do provedor em quatro abas
  preservadas em `?tab=`: `Resumo`, `Posições`, `Aportes` e `Proventos`.
- **Resumo:** valor atual em destaque; proventos dos últimos 12 meses,
  resultado informado pelo provedor e resultado estimado com cobertura;
  posições ativas/encerradas/indisponíveis; alocação em donut por classe, com
  detalhe das posições ao clicar na categoria; evolução mensal do valor
  investido por classe; cobertura da importação por conexão.
- **Cobertura:** `overview.coverage` é apresentado com estados
  `AVAILABLE`, `NOT_SHARED`, `PENDING_AUTHORIZATION`, `UNKNOWN` e `ERROR`.
  Ausência de posição não é tratada como saldo zero.
- **Posições:** busca local por nome/código/ISIN, filtros de situação e classe,
  cards compactos e carregamento progressivo. Detalhes e edição em drawer.
- **Aportes:** período selecionável; métricas de aportes externos, resgates,
  aporte líquido, aplicado em ativos, custos, saldo ocioso, posições e resultado
  desde o início; conciliação do caixa (`settlementDebits`, compras, custos e
  diferença); perímetro incluído por conexão, com aviso quando a conexão não tem
  conta marcada (`NOT_CONFIGURED`) e os fluxos bancários não entram no relatório;
  tabela mensal e lançamentos em disclosure. O
  texto diferencia aporte (caixa que entra) de aplicação (compra do ativo) e
  deixa claro que aporte não é receita nem despesa.
- **Proventos:** total do período, gráfico mensal, agrupamento por ativo, lista de
  lançamentos e cadastro/edição manual em drawer. O texto deixa explícito que
  proventos não entram automaticamente no Dashboard.
- **API:** `GET /api/v1/investments/overview`,
  `GET /api/v1/investments/holdings`, `GET /api/v1/investments/history`,
  `GET /api/v1/investments/income`,
  `GET /api/v1/investments/contributions?from&to&connectionId`,
  `PATCH /api/v1/investments/holdings/{id}/cost-basis`,
  `PATCH /api/v1/investments/holdings/{id}/asset-class`,
  `POST/PATCH/DELETE /api/v1/investments/income`.
- **Estados:** skeleton; erro geral; erro por aba; sem posições; sem proventos;
  sem aportes no período; cobertura indisponível; perímetro não configurado;
  referência antiga.
- **Funcionalidade:** [Investimentos](features.md#14-investimentos).

### Componentes de investimentos

- **`InvestmentSummary`**: valor atual, proventos, resultado do provedor,
  resultado estimado e posições não disponíveis, com nomenclatura distinta.
- **`InvestmentAllocation`**: donut por classe com total no centro, legenda
  interativa e tabela acessível; percentuais vêm do backend. Cada classe abre o
  `InvestmentClassDrawer` com as posições que compõem a fatia.
- **`InvestmentClassDrawer`**: detalhe da classe com valor, participação,
  posições ativas e acesso ao `InvestmentHoldingDrawer`.
- **`InvestmentCoverage`**: cobertura e recursos por conexão, sem inventar dados.
- **`InvestmentContributionsSection`**: métricas de aportes/resgates,
  conciliação do caixa, perímetro incluído, tabela mensal e lançamentos; explica
  a diferença entre aporte e aplicação.
- **`InvestmentHoldings`** e **`InvestmentHoldingDrawer`**: cards compactos e
  detalhes do provedor, resultado estimado e configuração de custo/classificação.
- **`InvestmentIncomeChart`** e **`InvestmentIncomeSection`**: gráfico mensal,
  agrupamento por ativo, lista e ações de proventos manuais.
- **`InvestmentIncomeForm`**: provento manual em drawer, vinculado a uma posição.

---

## Patrimônio

### Patrimônio (`/wealth`)

- **Arquivo:** `features/wealth/pages/WealthPage.tsx` · protegida.
- **Propósito:** patrimônio líquido, ativos brutos, passivos, composição por
  fonte e itens manuais.
- **Hierarquia:** patrimônio líquido em destaque; ativos e passivos separados;
  composição com gráficos por fonte; itens fora do cálculo com motivo.
- **Regras:** cartões aparecem como passivos; itens excluídos não entram nos
  totais; nenhuma soma entre moedas. Saldos reservados aparecem como fonte
  `Saldo reservado` apenas quando o tratamento efetivo é `SEPARATE_ASSET`; com
  `INCLUDED_IN_BALANCE` ficam só no detalhamento do saldo e com `UNKNOWN` não
  somam. Reserva sem detalhe vira lacuna explícita, não zero.
- **Composição:** ativos por fonte (conta, saldo reservado, investimento, ativo
  manual) e passivos por fonte (cartão, passivo manual), com tabela acessível e
  itens fora do cálculo com o motivo.
- **Evolução:** gráfico mensal de ativos, passivos e patrimônio líquido, com
  tabela acessível e detalhe do snapshot em drawer. Histórico começa no primeiro
  snapshot real; meses anteriores não são reconstruídos.
- **Itens manuais:** listados por natureza, com criação/edição em drawer e
  exclusão com confirmação explícita.
- **API:** `GET /api/v1/wealth/overview`,
  `GET/POST /api/v1/wealth/history`, `GET /api/v1/wealth/history/{id}`,
  `GET /api/v1/wealth/manual-items`,
  `POST/PATCH/DELETE /api/v1/wealth/manual-items[/{id}]`.
- **Estados:** skeleton; erro; sem dados (`Sem dados suficientes`, não zero);
  `COMPLETE`/`PARTIAL`/`ESTIMATED`; item manual vazio.
- **Funcionalidade:** [Patrimônio](features.md#15-patrimônio).

---

## Metas

### Metas (`/goals`)

- **Arquivo:** `features/goals/pages/GoalsPage.tsx` · protegida.
- **Propósito:** listar metas com progresso, prazo, valores e status; detalhes e
  ações em drawer; criação/edição em drawer.
- **Cards:** nome, tipo, prazo, status, barra de progresso, percentual, alvo,
  valor atual e restante. O percentual aparece uma única vez no card.
- **Detalhe:** progresso, alocações planejadas, contribuições, fontes, histórico
  paginado (50 por página, “Carregar mais”), capacidade excedida, notas e ações
  de pausar/arquivar/reativar/excluir.
- **Copy:** alocações são planejamento, não movimentações; progresso por
  alocações é distinto de progresso por patrimônio líquido.
- **Formulário:** seções de informações, regra e alocações, com observações em
  divulgação progressiva e resumo do planejamento.
- **API:** `GET /api/v1/goals`, `POST /api/v1/goals`, `PATCH /api/v1/goals/{id}`,
  `POST /api/v1/goals/{id}/status`, `DELETE /api/v1/goals/{id}`;
  `GET /api/v1/goals/{id}/progress?cursor&limit` para o histórico; fontes via
  contas, holdings e ativos manuais.
- **Estados:** skeleton; erro; vazio; todos os status; erro de formulário no
  drawer.
- **Funcionalidade:** [Metas](features.md#16-metas).

---

## Privacidade e Conta

### Privacidade (`/settings/privacy`)

- **Arquivo:** `features/privacy/pages/PrivacySettingsPage.tsx` · protegida.
- **Propósito:** exportação de dados, desconexão da integração (com opção de
  excluir dados importados) e exclusão de conta.
- **Hierarquia:** exportação; desconexão; exclusão em superfície de perigo com
  resumo das consequências antes da confirmação.
- **Formulários/diálogos:**
  - Exportação: botão (baixa `delta-finance-export.zip`).
  - Desconexão: checkbox `deleteImportedData` + diálogo de confirmação.
  - Exclusão: resumo de retenção + `password` (obrigatório), `confirmation`
    (= `EXCLUIR`) e `policyAcknowledged` (obrigatório).
- **API:** `GET /api/v1/integrations/pluggy`,
  `DELETE /api/v1/integrations/pluggy?deleteImportedData=`,
  `POST /api/v1/me/delete`, `GET /api/v1/me/export` (download).
- **Estados:** exportando; sucesso/erro de exportação; carregamento/erro da
  integração; sucesso/erro de desconexão; erro de exclusão. Após exclusão:
  limpa sessão e navega para `/login` com `{accountDeleted:true}`.
- **Funcionalidade:** [Privacidade e ciclo de vida](features.md#17-privacidade-e-ciclo-de-vida).

### Conta (configurações) (`/account`)

- **Arquivo:** `features/auth/pages/AccountSettingsPage.tsx` · protegida.
- **Propósito:** exibir identidade (username, data de criação), alterar senha e
  sair.
- **Componentes:** `PageHeader`, `Card`, `SectionHeader`, `Field`, `Input`,
  `Button`, `Alert`, `FormErrorSummary`.
- **Formulário** (Zod `passwordChangeSchema`): `currentPassword`, `newPassword`,
  `confirmNewPassword` (confirmação de igualdade). Logout como ação.
- **API:** `PUT /api/v1/auth/password`; `POST /api/v1/auth/logout`;
  `GET /api/v1/auth/me`.
- **Estados:** "Senha alterada. As outras sessões foram encerradas."; erros;
  identidade com "—" quando ausente. Não há promessa de recuperação de senha
  automatizada.
- **Funcionalidade:** [Identidade, autenticação e sessão](features.md#1-identidade-autenticação-e-sessão).

---

## Componente compartilhado

- **`components/DisplayNameEditor.tsx`**: edição inline de nome de exibição
  (renomear/restaurar nome original do provedor). Props: `currentName`,
  `originalName`, `customized`, `disabled`, `onRename`, `onReset`. Valida vazio e
  > 120; mensagens "Nome de exibição atualizado." / "Nome original restaurado.".
  > Usado em Detalhe da conta, Detalhe do cartão, `ConnectionsList` e nos saldos
  > reservados.

---

## Mapa telas ↔ funcionalidades

| Tela (rota)                                               | Funcionalidade(s) principal(is)     |
| --------------------------------------------------------- | ----------------------------------- |
| Home (`/`)                                                | — (apresentação)                    |
| Login (`/login`)                                          | Identidade e sessão                 |
| Cadastro (`/register`)                                    | Identidade e sessão                 |
| Dashboard (`/dashboard`)                                  | Dashboard e relatórios              |
| Contas (`/accounts`)                                      | Contas; Cartões                     |
| Detalhe da conta (`/accounts/:id`)                        | Contas; Cartões                     |
| Nova/Editar conta (`/accounts/new`, `/accounts/:id/edit`) | Contas                              |
| Cartões (`/cards`)                                        | Cartões                             |
| Detalhe do cartão (`/cards/:accountId`)                   | Cartões; Faturas                    |
| Transações (`/transactions`)                              | Transações                          |
| Novo lançamento (`/transactions/new`)                     | Transações                          |
| Categorias (`/categories`)                                | Categorias                          |
| Reconciliação (`/reconciliation`)                         | Reconciliação                       |
| Faturas (`/cards/:accountId/invoices`)                    | Faturas                             |
| Detalhe da fatura (`/invoices/:id`)                       | Faturas                             |
| Preferências (`/settings/preferences`)                    | Tema, paleta e persistência local   |
| Integração (`/settings/integration`)                      | Integração; Conexões; Sincronização |
| Investimentos (`/investments`)                            | Investimentos                       |
| Patrimônio (`/wealth`)                                    | Patrimônio                          |
| Metas (`/goals`)                                          | Metas                               |
| Privacidade (`/settings/privacy`)                         | Privacidade e ciclo de vida         |
| Conta (`/account`)                                        | Identidade e sessão                 |
