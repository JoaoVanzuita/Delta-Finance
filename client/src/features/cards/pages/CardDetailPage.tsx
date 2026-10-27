import { Link, useParams } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type Invoice } from '@/features/integration/api'
import { CardCycleSettings } from '@/features/invoices/components/CardCycleSettings'

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativo',
  INACTIVE: 'Inativo',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrado',
}

const INVOICE_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

/**
 * Página consolidada do cartão.
 *
 * Reúne limite, fatura atual, histórico, compras pendentes e últimas transações. A fonte de cada
 * valor continua sendo a API de faturas/contas; a página não recalcula nem inventa dados.
 */
export function CardDetailPage() {
  const { accountId = '' } = useParams<{ accountId: string }>()
  const accountQuery = useQuery({
    queryKey: ['account', accountId],
    queryFn: () => financeApi.account(accountId),
    enabled: Boolean(accountId),
  })
  const overviewQuery = useQuery({
    queryKey: ['cards', 'overview'],
    queryFn: financeApi.cardOverview,
  })
  const invoicesQuery = useQuery({
    queryKey: ['invoices', accountId],
    queryFn: () => financeApi.invoices(accountId),
    enabled: Boolean(accountId),
  })
  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'card', accountId],
    queryFn: () => financeApi.transactions({ limit: 100, accountId }),
    enabled: Boolean(accountId),
  })

  const account = accountQuery.data
  const card = overviewQuery.data?.items.find((item) => item.accountId === accountId)
  const profile = account?.cardProfile
  const invoices = invoicesQuery.data?.items ?? []
  const currentInvoice =
    card?.currentInvoice ?? invoices.find((invoice) => invoice.status === 'OPEN') ?? null
  const history = currentInvoice
    ? invoices.filter((invoice) => invoice.id !== currentInvoice.id)
    : invoices
  const transactions = transactionsQuery.data?.items ?? []
  const pendingTransactions = currentInvoice
    ? transactions.filter(
        (transaction) =>
          transaction.status === 'PENDING' &&
          transaction.type === 'DEBIT' &&
          transaction.postedOn >= currentInvoice.cycleStart &&
          transaction.postedOn <= currentInvoice.cycleEnd,
      )
    : []
  const recentTransactions = transactions.slice(0, 10)
  const queryError = accountQuery.error ?? overviewQuery.error ?? invoicesQuery.error

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <Link to="/cards" className="text-sm font-medium text-action underline underline-offset-2">
        ← Cartões
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Cartão de crédito
          </p>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">
            {account?.alias ?? card?.alias ?? 'Cartão'}
          </h1>
          <p className="text-sm text-ink-muted">
            {account?.institutionName ?? card?.institutionName ?? 'Instituição não informada'}
            {profile?.brand ? ` · ${profile.brand}` : ''}
            {profile?.level ? ` · ${profile.level}` : ''}
            {profile?.last4 ? ` · •••• ${profile.last4}` : ''}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-xs font-semibold text-ink">
            {STATUS_LABELS[account?.status ?? card?.status ?? ''] ??
              account?.status ??
              card?.status ??
              '—'}
          </span>
          <Link
            to={`/cards/${accountId}/invoices`}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            Faturas
          </Link>
          <Link
            to={`/transactions?accountId=${accountId}`}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            Transações
          </Link>
        </div>
      </header>

      {queryError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {queryError instanceof ApiError
            ? queryError.message
            : 'Não foi possível carregar o cartão.'}
        </p>
      ) : null}

      <section aria-labelledby="card-summary-heading" className="space-y-3">
        <h2 id="card-summary-heading" className="text-lg font-semibold text-ink">
          Resumo
        </h2>
        <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <SummaryTile
            label="Saldo devedor"
            value={formatMoney(card?.outstanding ?? '0.00', account?.currency ?? 'BRL')}
            hint="Soma das faturas não pagas deste cartão."
          />
          <SummaryTile
            label="Limite total"
            value={formatMoney(profile?.creditLimit, account?.currency ?? 'BRL')}
            hint="Limite informado pelo provedor ou configurado manualmente."
          />
          <SummaryTile
            label="Limite disponível"
            value={
              profile?.availableCreditLimit
                ? formatMoney(profile.availableCreditLimit, account?.currency ?? 'BRL')
                : 'Não informado'
            }
            hint="Valor disponível informado oficialmente pelo provedor."
          />
          <SummaryTile
            label="Valor utilizado"
            value={formatMoney(card?.usedAmount ?? '0.00', account?.currency ?? 'BRL')}
            hint="Pode incluir compras ainda pendentes."
          />
          <SummaryTile
            label="Crédito acumulado"
            value={formatMoney(profile?.creditBalance ?? '0.00', account?.currency ?? 'BRL')}
            hint="Crédito para próximas faturas; não é receita nem saldo em conta."
          />
          <SummaryTile
            label="Próximo vencimento"
            value={card?.nextDueDate ? formatDate(card.nextDueDate) : 'Sem pendência'}
            hint="Vencimento da fatura pendente mais próxima."
          />
        </dl>
      </section>

      {currentInvoice ? (
        <CurrentInvoiceCard invoice={currentInvoice} pendingCount={pendingTransactions.length} />
      ) : (
        <section className="rounded-card border border-line bg-surface p-5">
          <h2 className="text-lg font-semibold text-ink">Fatura atual</h2>
          <p className="mt-2 text-sm text-ink-muted">
            Nenhuma fatura aberta no ciclo atual. Compras efetivadas criam a fatura automaticamente.
          </p>
        </section>
      )}

      {history.length > 0 ? (
        <section aria-labelledby="card-history-heading" className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="card-history-heading" className="text-lg font-semibold text-ink">
              Histórico de faturas
            </h2>
            <Link
              to={`/cards/${accountId}/invoices`}
              className="text-sm font-medium text-action underline underline-offset-2"
            >
              Ver todas
            </Link>
          </div>
          <ul className="grid gap-3 sm:grid-cols-2">
            {history.slice(0, 6).map((invoice) => (
              <li key={invoice.id}>
                <Link
                  to={`/invoices/${invoice.id}`}
                  className="block rounded-card border border-line bg-surface p-4 transition-colors hover:bg-surface-muted"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-ink">
                        {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)}
                      </p>
                      <p className="mt-1 text-xs text-ink-muted">
                        Vence em {formatDate(invoice.dueDate)}
                      </p>
                    </div>
                    <span className="rounded-card border border-line bg-surface-muted px-2 py-1 text-xs text-ink">
                      {INVOICE_STATUS_LABELS[invoice.status] ?? invoice.status}
                    </span>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                    <dt className="text-ink-muted">Total</dt>
                    <dd className="numeric text-right text-ink">
                      {formatMoney(invoice.total, account?.currency ?? 'BRL')}
                    </dd>
                    <dt className="text-ink-muted">Pago</dt>
                    <dd className="numeric text-right text-ink">
                      {formatMoney(invoice.paymentsTotal, account?.currency ?? 'BRL')}
                    </dd>
                    <dt className="text-ink-muted">Saldo devedor</dt>
                    <dd className="numeric text-right text-ink">
                      {formatMoney(invoice.outstanding, account?.currency ?? 'BRL')}
                    </dd>
                    {Number(invoice.appliedCredit) > 0 ? (
                      <>
                        <dt className="text-ink-muted">Crédito aplicado</dt>
                        <dd className="numeric text-right text-ink">
                          {formatMoney(invoice.appliedCredit, account?.currency ?? 'BRL')}
                        </dd>
                      </>
                    ) : null}
                  </dl>
                  <div className="mt-2 flex flex-wrap gap-1">
                    {invoice.missingPurchases ? (
                      <span className="rounded-card border border-warning/40 bg-warning/5 px-2 py-0.5 text-xs text-warning">
                        Histórico incompleto
                      </span>
                    ) : null}
                    {invoice.providerBill ? (
                      <span
                        className={
                          invoice.providerBill.reconciled
                            ? 'rounded-card border border-income/30 bg-income/5 px-2 py-0.5 text-xs text-income'
                            : 'rounded-card border border-warning/40 bg-warning/5 px-2 py-0.5 text-xs text-warning'
                        }
                      >
                        {invoice.providerBill.reconciled ? 'Conciliada' : 'Divergente'}
                      </span>
                    ) : null}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {card && card.unallocatedCount > 0 ? (
        <section className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4">
          <h2 className="text-sm font-semibold text-ink">Compras não alocadas</h2>
          <p className="text-xs text-ink-muted">
            {card.unallocatedCount} transação(ões) efetivada(s) ainda não pertencem a uma fatura.
            Verifique o ciclo do cartão.
          </p>
        </section>
      ) : null}

      <section aria-labelledby="card-transactions-heading" className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="card-transactions-heading" className="text-lg font-semibold text-ink">
            Últimas transações
          </h2>
          <Link
            to={`/transactions?accountId=${accountId}`}
            className="text-sm font-medium text-action underline underline-offset-2"
          >
            Ver todas
          </Link>
        </div>
        {transactionsQuery.isLoading ? (
          <p className="text-sm text-ink-muted">Carregando transações…</p>
        ) : recentTransactions.length === 0 ? (
          <p className="rounded-card border border-line bg-surface p-4 text-sm text-ink-muted">
            Nenhuma transação encontrada.
          </p>
        ) : (
          <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {recentTransactions.map((transaction) => (
              <li
                key={transaction.id}
                className="flex items-baseline justify-between gap-3 px-4 py-3 text-sm"
              >
                <span className="min-w-0">
                  <span className="block truncate text-ink">
                    {transaction.description ?? 'Sem descrição'}
                  </span>
                  <span className="text-xs text-ink-muted">
                    {formatDate(transaction.postedOn)}
                    {transaction.status === 'PENDING' ? ' · Pendente' : ''}
                  </span>
                </span>
                <span className="numeric shrink-0 font-medium text-ink">
                  {formatMoney(transaction.amount, transaction.currency)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <CardCycleSettings accountId={accountId} profile={profile} />
    </div>
  )
}

function SummaryTile({
  label,
  value,
  hint,
}: {
  readonly label: string
  readonly value: string
  readonly hint: string
}) {
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <dt className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">{label}</dt>
      <dd className="mt-2">
        <span className="numeric block text-xl font-semibold text-ink">{value}</span>
        <span className="mt-2 block text-xs leading-relaxed text-ink-muted">{hint}</span>
      </dd>
    </div>
  )
}

function CurrentInvoiceCard({
  invoice,
  pendingCount,
}: {
  readonly invoice: Invoice
  readonly pendingCount: number
}) {
  return (
    <section
      aria-labelledby="card-current-invoice-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Ciclo em andamento
          </p>
          <h2 id="card-current-invoice-heading" className="mt-1 text-lg font-semibold text-ink">
            Fatura atual
          </h2>
          <p className="mt-1 text-sm text-ink-muted">
            {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)} · fecha{' '}
            {formatDate(invoice.closingDate)} · vence {formatDate(invoice.dueDate)}
          </p>
        </div>
        <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-sm font-semibold text-ink">
          {INVOICE_STATUS_LABELS[invoice.status] ?? invoice.status}
        </span>
      </div>
      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Total</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.total, 'BRL')}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Pago</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.paymentsTotal, 'BRL')}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Saldo devedor</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.outstanding, 'BRL')}
          </dd>
        </div>
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <dt className="text-xs text-ink-muted">Compras pendentes</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">{pendingCount}</dd>
        </div>
      </dl>
      {invoice.missingPurchases ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-xs leading-relaxed text-ink">
          Esta fatura tem pagamento registrado, mas nenhuma compra local no ciclo. O histórico do
          conector pode estar incompleto; use a conciliação com a Bill oficial ou registre um ajuste
          explícito.
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Link
          to={`/invoices/${invoice.id}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface hover:bg-primary-hover"
        >
          Abrir fatura atual
        </Link>
        <Link
          to={`/cards/${invoice.cardAccountId}/invoices`}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
        >
          Ver faturas
        </Link>
      </div>
    </section>
  )
}

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
