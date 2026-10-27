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
liberado. Procedimento completo em `runbooks/update-rollback.md`.

## Health checks

| Serviço | Endpoint | Observação |
|---|---|---|
| `server` | `/actuator/health/liveness` | processo vivo |
| `server` | `/actuator/health/readiness` | banco + key ring (versões ativas, sem material de chave) |
| `client` | `/healthz` | SPA servida pelo Caddy interno |
| `proxy` | `127.0.0.1:2019/config/` | liveness do Caddy (admin local) |
| `db` | `pg_isready` | pronto para conexões |

O Actuator não deve ser exposto publicamente; apenas a rede interna do Compose
tem acesso.

## Logs

- Logs vão para stdout/stderr e são coletados pelo Docker
  (`docker compose logs -f server`).
- Formato JSON estruturado, com `requestId`, evento, resultado, duração e código
  de erro. Valores financeiros, descrições, Client Secret e API
  Key nunca são registrados.
- `sync_run` é a fonte de verdade operacional de cada tentativa de sincronização
  .
- Logs de sincronização incluem apenas `connectionId`/`integrationId`,
  `correlationId`, estado e código sanitizado; nunca descrição, valor, itemId ou
  corpo de resposta do provedor.

## Sincronização

- **Fila durável** `sync_job`: claim atômico com `FOR UPDATE SKIP LOCKED`; um
  único job ativo por conexão/tipo. `POST /api/v1/connections/{id}/sync`
  enfileira a consulta dos dados já disponíveis e responde `202`, sem forçar a
  instituição de origem a atualizar.
- **Carga inicial e reconexão** validam o estado do item antes de importar,
  salvam conexão/integração no estado de erro quando necessário e registram
  `sync_run` com contadores e resultado. Transações sem valor ou data são
  descartadas e contabilizadas em `failed_count`; a execução fica `PARTIAL` com
  `error_code=IMPORT_MISSING_FIELDS`.
- **Janelas configuráveis** (`DELTA_SYNC_*`, padrão entre parênteses):
  carga inicial de 12 meses (`INITIAL_WINDOW_MONTHS=12`); reprocessamento diário
  de 7 dias com sobreposição (`DAILY_WINDOW_DAYS=7`); reconciliação mensal de 12
  meses (`RECONCILIATION_WINDOW_MONTHS=12`) no dia
  `RECONCILIATION_DAY=1`. O scheduler roda a cada `SCHEDULER_INTERVAL=1m` com
  jitter de até `JITTER=60s`.
- **Contrato da Pluggy**: em `GET /v2/transactions` use `dateFrom`/`dateTo` e o
  cursor `next` como query string (o `after` deve ser extraído e decodificado).
  **Não envie `pageSize`**: a API responde `400 property pageSize should not
  exist`; o tamanho padrão é 500. Um `400` de parâmetro é registrado no log como
  `Falha do provedor: endpoint=... status=400` e não significa,
  necessariamente, indisponibilidade do provedor.
- **Retries**: até `MAX_ATTEMPTS=5` por job, com backoff exponencial (base 30 s,
  teto 1 h) ou `Retry-After` do provedor. Falha de uma conexão não bloqueia as
  demais. Credencial recusada e itemId inválido/inexistente exigem ação do
  usuário e não entram em retry automático.
- **Estados**: `INCOMPLETE`, `ACTIVE`, `SYNCING`, `SYNCED`,
  `CREDENTIAL_FAILURE`, `ACTION_REQUIRED`, `AUTHORIZATION_EXPIRED`,
  `ITEM_UNAVAILABLE`. O limite da modalidade (`connectionLimit`) e a data de
  verificação vêm do backend; conexões `ITEM_UNAVAILABLE` não ocupam vaga.
- **Contas ausentes** em uma leitura completa de contas ficam `UNAVAILABLE` sem
  apagar transações ou histórico; o saldo deixa de contá-las e não há duplicação
  quando a nova autorização expõe uma conta com outro ID. A expiração anual da aplicação
  (`USER_AUTHORIZATION_EXPIRED`) é distinta do consentimento bancário de cada
  item (`CONSENT_EXPIRED`); ambos exigem reautorização e atualização do itemId,
  com histórico preservado.
- **Lista vazia** produz `EMPTY` e não marca remoções. Uma leitura conclusiva e
  não vazia que deixa de trazer a transação grava `status=SOURCE_REMOVED` e
  `reconciliation_state=SOURCE_REMOVED`; o valor sai imediatamente dos totais e
  a linha permanece no histórico. Se a origem voltar a trazer o identificador, a
  transação é restaurada automaticamente. Leitura truncada (limite de páginas ou
  cursor inválido) é inconclusiva e não marca ausências.
- **Recuperação e cancelamento** (ADR-0004): `SYNCING` e `lastSyncAttemptAt` são
  persistidos antes das chamadas externas. Todo job `RUNNING` encontrado no start
  é órfão: vira `sync_run` com `INTERRUPTED`, volta para `RETRY`/`FAILED` e libera
  `SYNCING` na conexão/integração. O timeout padrão é
  `DELTA_SYNC_JOB_TIMEOUT=10m`. Remover/desconectar cancela o token em memória e
  a importação para antes do próximo lote; o worker atrasado não sobrescreve o
  job. Vale para instância única.
- **Sugestões de movimento interno** são geradas ao concluir uma sincronização,
  a carga inicial e a reconexão, mantendo o dashboard coerente.
- **Webhooks:** removidos em 2026-10-25 (S-04). A atualização continua pela
  sincronização programada e pela consulta manual.
- **Painel** `/settings/integration` mostra estado, última tentativa, último
  sucesso, fila, histórico (`GET /api/v1/sync/status` e `/sync/runs`) e ações de
  consultar agora, atualizar itemId e remover conexão.
- **Métricas**: `delta.sync.runs`, `delta.sync.duration`, `delta.sync.failures`,
  `delta.sync.jobs` e `delta.sync.records` via Actuator. Alertas recomendados:
  falhas repetidas, fila acumulada, `AUTHORIZATION_EXPIRED`/`CONSENT_EXPIRED` e
  ausência de sincronização bem-sucedida no período esperado.

## Faturas de cartão

- A fatura do ciclo e o item espelho são gerados por evento logo após a
  transação ser importada, criada manualmente ou corrigida. Não é necessário
  abrir a tela de faturas para o dashboard refletir a compra.
- Transação `PENDING` ou com remoção na origem pendente tem o espelho suspenso
  (`invoice_item.removed_at` + `mirror_suspended=true`) e sai dos totais. Quando
  volta a ser efetiva, o item é reativado na mesma fatura ou recriado na primeira
  fatura aberta.
- Fatura paga não é reescrita. Inclusão, edição e remoção de item retornam
  `409 INVOICE_PAID_IMMUTABLE`; correção de valor/data/descrição de compra
  vinculada retorna `409 TRANSACTION_PAID_INVOICE`.
- Atraso prevalece sobre pagamento parcial: fatura vencida com saldo fica
  `OVERDUE`.
- Correção de compra parcelada propaga valor, descrição e data para todas as
  parcelas; a âncora muda de ciclo quando necessário.
- Quando o conector não envia `creditData.balanceCloseDate`, a importação consulta
  as Bills do cartão e infere o dia de fechamento/vencimento pela ocorrência mais
  frequente nas últimas faturas. A inferência não sobrescreve ciclo configurado
  pelo usuário e falhas na consulta de Bills não interrompem a sincronização.
- O painel `/cards` consolida saldo devedor, limite, utilização e próximo
  vencimento sem somar cartões ao saldo das contas correntes. A página
  `/cards/{accountId}` reúne metadados (bandeira, nível, final e limite
  disponível), fatura atual, histórico, pendências e últimas transações. A
  primeira abertura após a criação do ciclo gera as faturas locais a partir das
  transações já importadas.
- Bandeira, nível e final do cartão vêm de `creditData`/`number` do provedor. O
  limite disponível oficial é armazenado separadamente do limite total; quando o
  provedor não envia `creditLimit`, o sistema não reutiliza o disponível como se
  fosse o limite total.
- `transaction.provider_bill_month` guarda o `billForecastDate` do Pluggy.
  Compras usam o próprio mês; pagamentos usam o mês anterior, porque o provedor
  informa o ciclo em que o pagamento foi lançado, não a fatura quitada.
- O vencimento local fica no mesmo mês do fechamento quando o dia de vencimento
  é maior ou igual ao de fechamento; caso contrário, no mês seguinte. Isso alinha
  o ciclo local às Bills do provedor.
- A fatura aberta do ciclo atual é criada quando existe qualquer transação no
  período, inclusive `PENDING`, para permitir acompanhamento. Compras pendentes
  aparecem separadas e não compõem o total nem o saldo devedor até serem
  efetivadas.
- Pagamentos de fatura importados do cartão são registrados em `invoice_payment`
  com `source=IMPORTED` e `source_transaction_id`. O total da fatura permanece
  bruto; `payments_total` mostra o pago e `outstanding` o saldo restante. Itens
  espelho antigos de pagamento são convertidos na primeira geração da fatura.
- Bills oficiais do provedor são persistidas em `provider_bill` e
  `provider_bill_payment` durante a importação de contas. Elas são referência de
  conciliação: guardam total, pagamento mínimo, vencimento e pagamentos oficiais,
  mas **não** alteram totais, status ou saldo devedor locais. Falhas de leitura de
  Bills não interrompem a sincronização.
- A conciliação é por ciclo (fechamento; vencimento como fallback). Cada Bill
  informa os pagamentos feitos contra ela; pagamentos muito distantes do ciclo
  (dados antigos repetidos pelo conector) são descartados da comparação e
  contados em `ignoredPayments`. A API expõe `providerBill` com
  `totalDifference` e `paymentsDifference` (`local - provedor`) e `reconciled`,
  verdadeiro somente quando total e pagamentos coincidem.
- O pagamento mínimo exibido na fatura vem de `invoice.minimum_payment_enc`
  quando preenchido; caso contrário, da Bill oficial conciliada.
- Pagamento confirmado acima da obrigação da fatura vira crédito para as próximas
  (`card_profile.credit_balance`). A obrigação usa o maior valor entre total local
  e total oficial da Bill; faturas sem obrigação conhecida (total zero) não geram
  crédito, pois normalmente indicam lacuna do conector.
- Pagamentos importados recebem `dedup_key` (HMAC de cartão, valor, data e descrição
  normalizada). Se a origem alternar `PENDING`/`POSTED`, o mesmo pagamento é
  reaproveitado; se a transação canônica for removida, uma duplicata efetiva assume
  o vínculo antes de qualquer reversão.
- Correções de valor/data na transação de pagamento atualizam o `invoice_payment`
  correspondente e recalculam a fatura. Se o ciclo mudar e a fatura atual não
  estiver paga, o pagamento acompanha a transação.
- A conta pagadora do pagamento importado nasce como o cartão e passa para a conta
  bancária quando o movimento interno correspondente é confirmado.
- Faturas com `total = 0` e `payments_total > 0` são sinalizadas como
  `missingPurchases`/“Histórico incompleto”. O sistema não inventa compras: a
  correção é explícita, pela conciliação com a Bill ou por ajuste manual auditado.
- Divergência de total com a Bill oficial é resolvida por uma ação explícita:
  `POST /api/v1/invoices/{id}/reconcile-provider` cria um item
  `MANUAL_ADJUSTMENT` com a diferença e o motivo informado. Nenhum item ou
  pagamento existente é reescrito. Em fatura paga, o status `PAID` é preservado,
  o ajuste é auditado e o crédito aplicado é normalizado.
- Faturas `PAID` mantêm a identidade `total = pagamentos + crédito aplicado`; se o
  crédito armazenado estiver menor que o necessário, ele é corrigido no recálculo
  sem reabrir a fatura. O histórico exibe `Crédito aplicado` quando maior que zero.
- Se a fatura não refletir uma compra, verifique se o cartão tem
  `card_profile` (fechamento/vencimento), se a transação está efetiva e se a
  fatura não está paga.
- Não há feature flag: o ciclo de vida automático está sempre ativo e o rollback
  é por deploy/reversão do código (ADR-0005).

## Investimentos

- Módulo da fase secundária (`/investments`). Importa posições de itens do
  Meu Pluggy e mantém posições manuais complementares.
- **Investimento não é conta.** `/accounts` só retorna `BANK`/`CREDIT`; as
  posições vêm de `GET /investments?itemId=` e não têm `accountId`. Não criar
  conta sintética.
- A cobertura é verificada por `GET /items/{id}/resources`. Um item pode listar
  `INVESTMENTS` em `products` e não compartilhar nenhuma posição; `products` não
  é prova. Estados: `AVAILABLE`, `NOT_SHARED`, `PENDING_AUTHORIZATION`,
  `UNKNOWN` e `ERROR`, considerando `resourcesCollectedAt` e
  `hasResourcesPendingAuthorization`.
- Posições importadas usam `balance` como valor atual; `amount` é o valor bruto.
  `TOTAL_WITHDRAWAL`/`RETIRADA_TOTAL` são normalizados para encerrada e não
  compõem o valor atual, mas permanecem no histórico. Ausência em leitura
  completa marca a posição como indisponível; nada é apagado.
- Transações `BUY`, `SELL`, `TAX`, `TRANSFER`, `INTEREST` e `AMORTIZATION` são
  persistidas apenas no módulo de investimentos. Elas não viram transações
  bancárias e não alteram o fluxo de caixa.
- `INTEREST` é a fonte de dividendos, aluguel de FII, JCP e cupons. Open Finance
  pode não enviar `INTEREST`, `amountProfit`, `amountOriginal` ou taxas de
  rentabilidade. Nesses casos o painel mostra "não informado" e **não deriva**
  rentabilidade de histórico incompleto. `rate`/`rateType` são remuneração
  contratada, não desempenho.
- O usuário pode registrar provento manual e custo informado para uma posição
  importada. Esses dados aparecem como manuais/estimados, não sobrescrevem o
  provedor e são auditados. O provento manual não é somado automaticamente às
  receitas do dashboard principal.
- Posições importadas ativas entram uma única vez no patrimônio; encerradas,
  indisponíveis e fora de BRL ficam de fora com motivo. Duplicidade entre posição
  manual e importada é sinalizada, sem exclusão automática; a posição manual pode
  ser marcada para não contar no patrimônio.
- Falha de investimento não interrompe contas, cartões e transações; os dados
  anteriores são preservados e a falha aparece como aviso não bloqueante.
- Não há recomendação de compra, venda ou alocação, nem snapshots históricos de
  valor de portfólio. A importação foi validada para XP via Meu Pluggy em
  2026-10-27; outras instituições dependem de validação por consentimento.
- Posições manuais continuam com total `quantidade × preço unitário`; quando
  apenas o total é informado, o preço é `total ÷ quantidade`, com 8 casas
  decimais. Elas entram na visão patrimonial e podem ser fonte de alocação de
  metas.

## Patrimônio

- Módulo pós-MVP (`/wealth`, `/api/v1/wealth/*`). A visão atual consolida contas,
  cartões como passivos, posições de investimento importadas e manuais e
  ativos/passivos manuais. Cada origem entra uma única vez; exclusões aparecem
  com o motivo explícito e não compõem o total.
- Valores manuais e de referência são os informados pelo usuário, não cotação de
  mercado.
- Snapshots patrimoniais foram removidos em 2026-10-26 (S-02); a visão atual não
  reconstrói saldos passados. As tabelas foram removidas pela baseline
  `V001__baseline.sql`.
- `completeness` é `COMPLETE`, `PARTIAL` (dados ausentes) ou `ESTIMATED` (fonte com
  referência há mais de 30 dias). Lacunas de histórico não são preenchidas.

## Metas

- Módulo pós-MVP (`/goals`, `/api/v1/goals`). Regras de progresso:
  `ALLOCATIONS` (alocações explícitas) e `NET_WORTH` (patrimônio líquido atual).
- Alocações `FIXED_AMOUNT` são exatas; `PERCENTAGE` usa
  `floor(valor-alvo × percentual ÷ 100, 2)`, com o resíduo na última alocação
  percentual. Percentuais abaixo de 100% mantêm saldo não alocado visível, sem
  redistribuição automática. O excesso é permitido, mas o progresso fica limitado
  a 100%.
- A soma das alocações de uma mesma conta, investimento ou item patrimonial entre
  metas não pode exceder o saldo da fonte; alocações `MANUAL` não têm saldo
  associado.
- Estados: `ACTIVE`, `ACHIEVED`, `PAUSED`, `ARCHIVED`, `OVERDUE`, `CHANGED`.
  `ACHIEVED` e `OVERDUE` são derivados; `PAUSED` e `ARCHIVED` congelam o
  progresso; alteração material marca `CHANGED` e exige reconhecimento explícito.
- O snapshot de progresso é único por meta e dia e é gravado **sob demanda** ao
  criar, consultar ou alterar o estado da meta ativa; pausar ou arquivar preserva
  o último snapshot. Não há job diário dedicado, então dias sem acesso podem não
  gerar ponto no histórico. Não há movimento de dinheiro nem criação de ordens.
- Excluir uma meta remove suas alocações e seu histórico; a UI exige confirmação.

## Banco e migrações

- PostgreSQL 18.6, volume `pgdata`.
- Migrações Flyway em `server/src/main/resources/db/migration/`, aplicadas no
  start. A baseline consolidada é `V001__baseline.sql` (2026-10-26); as migrações
  anteriores foram consolidadas nela e permanecem no histórico do Git. Nunca
  edite uma migração já aplicada; crie a próxima versão (`V002` em diante).
- `MigrationIT` valida banco vazio, snapshot da versão anterior, aplicação
  idempotente e ausência de objetos legados.
- `idempotency_key` guarda replay apenas de transações, itens de fatura e
  pagamentos; as demais mutações ignoram `Idempotency-Key`.
- Descrições, valores e histórico de metas são cifrados; metadados de consulta
  (corretora, origem, tipo, estado, percentual e datas) ficam em claro para
  consulta e ordenação.
- O schema ativo tem as tabelas de domínio do núcleo bancário, cartões,
  investimentos, patrimônio e metas, mais as tabelas de sessão do Spring e o
  histórico do Flyway. Webhooks, snapshots patrimoniais e dívida inicial não
  fazem parte da baseline; Bills estão em migração própria (`V003`) e os
  investimentos importados serão adicionados em `V006`.
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
- RPO de 24 h e RTO de 2 h para a operação de referência. Ensaio automatizado
  em `BackupRestoreIT`; procedimento em `runbooks/backup.md` e `runbooks/restore.md`.

## Retenção de dados

Prazos implementados pelo `RetentionJob`: `login_attempt` 30 dias, `sync_run` 90
dias, `audit_event` 1 ano, `idempotency_key` 24 h.

## Autenticação e limites

- Login: 5 falhas em 15 minutos por usuário+IP. O bloqueio de 15 minutos é
  persistido em `login_attempt.blocked_until` e sobrevive a reinícios.
- Cadastro: 5 tentativas por hora por IP, aplicadas antes do Argon2id. O contador
  fica em memória; reiniciar a aplicação zera a janela.
- A implantação de referência usa uma instância. Se o servidor for escalado
  horizontalmente, o limite de cadastro precisa migrar para banco ou Redis para
  ser compartilhado; o bloqueio de login já é persistido no PostgreSQL.
- O client expira a sessão após 30 minutos de inatividade ou 12 horas, mas a
  decisão final continua no servidor.

## Observabilidade e alertas

- Métricas Micrometer via Actuator, com foco em latência de requisições,
  fila de sincronização, falhas por código e duração de relatórios.
- Alertas recomendados: falhas repetidas de sincronização,
  fila acumulada, expiração de autorização anual da Pluggy e falha de backup.
- Não há cache de dados financeiros; no máximo metadados curtos de status.

## Recuperação assistida de senha

O MVP não usa e-mail transacional. O procedimento assistido, com verificação de
identidade pelo operador, invalidação de sessões e auditoria, é implementado
pelo `AssistedPasswordResetService` e por `deploy/scripts/reset-password.sh`;
procedimento em `runbooks/password-reset.md`. As sessões são revogadas por JDBC,
portanto o runner funciona com `--spring.main.web-application-type=none`.

## Segurança operacional

- Segredos apenas em `.env` e `secrets/`, ambos fora do Git.
- Acesso ao host restrito ao operador; TLS obrigatório na rede.
- Dependências, imagens e segredos são escaneados por `.github/workflows/security.yml`
  (gitleaks, Trivy filesystem e Trivy image).

## Performance

Cenário de referência: 5 usuários, 5 conexões por usuário e 10.000 transações por
usuário. As metas são dashboard/filtros p95 ≤ 2 s, CRUD/listas p95 ≤ 500 ms,
login/logout p95 ≤ 1 s e sincronização de uma conexão ≤ 60 s.

1. Suba um banco de desenvolvimento migrado e a API.
2. Semeie dados sintéticos:

```bash
DELTA_DB_URL=jdbc:postgresql://localhost:5432/delta_finance \
DELTA_DB_USER=delta DELTA_DB_PASSWORD=delta \
DELTA_CRYPTO_KEY_FILE=./secrets/crypto-keys.json \
./deploy/scripts/perf-seed.sh
```

3. Execute os cenários k6:

```bash
docker run --rm --network host -i grafana/k6:2.3.0 run - < server/perf/dashboard.js
CONNECTION_ID=<uuid> docker run --rm --network host -i grafana/k6:2.3.0 run - \
  < server/perf/auth-crud-sync.js
```

O semeador recria `perf.dashboard`; nunca use em produção. Registre apenas
latências e contagens, sem dados financeiros. Se o dashboard exceder 2 s,
verifique volume do período, quantidade de candidatos descriptografados e
índices por `user_id`/data; não reduza a janela de reconciliação para mascarar o
problema.

**Resultado de 2026-10-24** (stack local, 5 usuários sintéticos com 10.000
transações no usuário de performance, 3 VUs por 30 s para dashboard e 1–4 VUs por
20 s para login/CRUD/sync): dashboard/filtros/drill-down p95 ≈ 365 ms; login p95
≈ 39 ms; logout p95 ≈ 7 ms; CRUD/listas p95 ≈ 9–16 ms; sincronização p95 ≈
58 ms (stub local com 1.000 transações); 0% de falhas HTTP. A sincronização usa
um stub local apenas para medir a fila e o processamento; a latência real do
provedor deve ser medida no gate `pluggy-acceptance.md`.

## Rotação de chave

`KeyRotationJob` regrava os campos cifrados com a versão ativa, em lotes e de
forma idempotente. A execução é única, via `deploy/scripts/rotate-keys.sh --yes`,
e exige a chave antiga no key ring até o fim. A rotação do índice HMAC é manual e
exige reindexação. Passo a passo em `runbooks/key-rotation.md`.

## Segurança e dependências

- `.github/workflows/security.yml` executa gitleaks, Trivy `fs` e Trivy `image`
  em push, pull request e semanalmente.
- ZAP baseline: `deploy/scripts/zap-baseline.sh [url...]`; relatórios em
  `docs/security/zap/`.
- Cobertura: JaCoCo exige 80% de linhas no domínio e 100% em `Money`,
  `OwnershipGuard` e `AuthenticatedUser`; o build falha abaixo disso.
- Release: `docs/release-checklist.md` fixa versões/digests e gates manuais.
