# Fase 3 — Faturas, Espelhos e Relatórios

**Status:** Concluída em 2026-10-25  
**Itens:** `C09`, `C10`, `C11`, `C12`, `C13`  
**Plano:** [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)  
**Decisão:** [ADR-0005](adr/0005-feature-flag-espelho-fatura.md) aplicada (sem flag)

> Esta fase torna o espelho de fatura reativo à transação de origem, protege
> faturas pagas e garante que parcelas e reduções de despesa tenham a mesma
> projeção em resumo, categorias, série e drill-down.

## 1. O que mudou

### C09 — Ciclo de vida do espelho de fatura

- Novos eventos internos `TransactionFinancialChangedEvent` e
  `TransactionDeletedEvent`, publicados em importação, criação manual, correção,
  revisão de remoção e exclusão manual.
- `InvoiceMirrorListener` sincroniza o item espelho de forma síncrona na mesma
  transação do evento.
- Transação `PENDING` ou com remoção na origem pendente
  (`reconciliation_state=SOURCE_REMOVED`) tem o espelho suspenso: `removed_at`
  preenchido e `mirror_suspended=true` (`V019`). O item sai dos totais.
- Ao voltar a `POSTED`/`RECONCILED`, o item é reativado na mesma fatura ou
  recriado na primeira fatura aberta.
- Fatura paga não é reescrita em nenhum caminho: geração, suspensão,
  reativação, correção ou exclusão.
- Mudança de data move a âncora para o ciclo correto e atualiza
  `transaction.invoiceId`.
- Correção de compra parcelada propaga valor, descrição e data para todas as
  parcelas ativas; apenas a âncora muda de ciclo.
- Exclusão de lançamento manual de cartão remove o espelho antes de apagar a
  transação, sem violar a FK.
- Toda alteração recalcula total, saldo devedor e crédito do cartão.

Testes: `InvoiceIT` (pendente/efetiva, remoção na origem, exclusão e mudança de
ciclo), `FinalInvoiceIT` (correção parcelada) e `MigrationIT` (`V019`).

### C10 — Proteção de fatura paga e status `OVERDUE`

- `POST/PATCH/DELETE` de item em fatura `PAID` retorna
  `409 INVOICE_PAID_IMMUTABLE`.
- Correção de valor, data ou descrição de transação com espelho ativo em fatura
  paga retorna `409 TRANSACTION_PAID_INVOICE`.
- A exclusão de transação manual em fatura paga também é recusada.
- A ordem de status passou a ser: `PAID` quando o saldo é zero; `OVERDUE` quando
  o vencimento passou e há saldo (mesmo com pagamento parcial);
  `PARTIALLY_PAID` quando há pagamento e o vencimento ainda não passou;
  `CLOSED` após o fechamento; caso contrário `OPEN`.
- Ajustes em fatura fechada continuam sinalizados por `hasAdjustments` e
  `adjustmentCount`.

Testes: `InvoiceIT` (item e correção em fatura paga; vencida com pagamento
parcial).

### C11 — Parcelas e itens manuais

- Cada parcela continua vinculada à mesma transação, com
  `installment_number`/`total_installments` e chave de plano.
- A projeção de relatórios substitui a compra pelo conjunto de parcelas, cada
  uma no seu `occurredOn`; não há dupla contagem.
- A categoria da compra é aplicada a todas as parcelas.
- `totalInstallments=1` em item manual é normalizado para item comum (sem
  metadados de parcelamento) e aparece em `expenses` e no drill-down.
- `installmentNumber` passou a ser opcional quando o total é informado; o plano
  sempre nasce completo, da parcela 1 até o total.

Testes: `InvoiceIT` e `FinalInvoiceIT` (plano de 3 parcelas; item manual com
total 1).

### C12 — Consistência de reembolsos nos relatórios

- `summary`, `categories`, `timeseries` e `/reports/entries` compartilham a
  mesma lista de entradas.
- Nenhuma categoria exibe valor negativo: a redução é limitada ao que entrou na
  categoria.
- Nenhum bucket da série exibe despesa negativa: reduções excedentes são
  carregadas para o próximo dia com despesa e o residual volta para os últimos
  dias, preservando a soma do período.
- Itens de redução são limitados ao total da fatura antes do item, ao total de
  despesas do cartão no período e ao valor original do reembolso/chargeback.
- O excedente permanece como crédito do cartão para faturas futuras.
- O filtro `type=REFUND` inclui somente reduções, nunca juros, encargos ou
  tarifas.
- Reembolso sem vínculo continua fora de receitas e despesas (ADR-0001).

Testes: `FinalInvoiceIT` e `DashboardIT`.

### C13 — Geração após sync, compra e correção

- O listener de eventos cria a fatura e o espelho logo após a transação ser
  importada, criada manualmente ou corrigida.
- `GET /cards/{accountId}/invoices` continua garantindo a geração, mas não é mais
  necessário abrir a tela para o dashboard refletir a compra.
- Nenhuma fatura duplicada é criada: a busca é por `(cartão, cycle_start)` e a
  geração é idempotente.
- `cardOutstandingBalance` passa a refletir a compra imediatamente.

Testes: `InvoiceIT` (sync e compra manual seguidos de summary; correção e
mudança de ciclo) e `FinalInvoiceIT`.

## 2. Validação

| Suite | Resultado |
|---|---|
| Backend `./mvnw verify` | **171 testes**, 0 falhas |
| JaCoCo / Spotless / Checkstyle | Verdes |
| Frontend `npm test` | **72 testes / 18 arquivos** |
| `typecheck`, `lint`, `format:check`, `build` | Verdes |
| `MigrationIT` | Verde, incluindo `V019` |
| `openapi:lint` | Verde |

## 3. Limitações conhecidas

- Fatura paga é imutável por design; o caminho para corrigir o passado é um
  ajuste explícito em fatura posterior.
- A reativação do espelho ocorre na mesma fatura, inclusive fechada, marcando
  `hasAdjustments`. Ela não move automaticamente o item para um ciclo futuro.
- O listener é síncrono e recalcula o cartão a cada evento. Para o volume atual
  isso é aceitável; uma fila/agregação fica para uma fase futura.
- A carry-over da série temporal é uma decisão de apresentação; o total do
  período permanece igual ao resumo.
- Itens de fatura sem transação não têm categoria própria; por isso não aparecem
  em filtros por categoria específica, apenas sem filtro ou em “Sem categoria”.
- O item espelho de uma transação de plano que muda de data move apenas a âncora;
  as demais parcelas permanecem nos ciclos originais.
- Na correção de valor de uma compra parcelada, o novo valor efetivo da transação
  é copiado para cada parcela, conforme o contrato atual; não há redividisão
  automática entre as parcelas.
- `V019` não faz backfill retroativo de espelhos antigos. Transações que já
  estavam `PENDING`/`SOURCE_REMOVED` antes do deploy são suspensas no próximo
  evento de sincronização, correção ou revisão de remoção.

## 4. Próxima fase

Fase 4 — sincronização e conexões:

- `C14` recuperação e cancelamento de sincronização;
- `C15` estado, limite e probe de conexão;
- `C16` contas e leitura truncada.

## 5. Referências

- [`plano-implementacao-correcoes.md`](plano-implementacao-correcoes.md)
- [`adr/0001-reembolso-sem-vinculo.md`](adr/0001-reembolso-sem-vinculo.md)
- [`adr/0005-feature-flag-espelho-fatura.md`](adr/0005-feature-flag-espelho-fatura.md)
- [`funcionalidades.md`](funcionalidades.md)
- [`operations.md`](operations.md)
- [`telas-e-componentes.md`](telas-e-componentes.md)
