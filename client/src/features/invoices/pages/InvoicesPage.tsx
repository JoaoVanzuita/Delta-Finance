import type { ReactNode } from 'react'

import { useQuery } from '@tanstack/react-query'
import { CalendarClock, CircleDollarSign, ReceiptText, Settings2, ShoppingCart } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'
import { addDecimals, formatDate, formatMoney } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type Invoice, type Transaction } from '@/features/integration/api'

import { CardCycleSettings } from '../components/CardCycleSettings'

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

type InvoiceTab = 'current' | 'history' | 'pending' | 'settings'

function parseTab(value: string | null): InvoiceTab {
  return value === 'history' || value === 'pending' || value === 'settings' ? value : 'current'
}

function absoluteAmount(value: string): string {
  return value.startsWith('-') ? value.slice(1) : value
}

function isCreditTransaction(transaction: Transaction): boolean {
  return (
    transaction.type === 'CREDIT' ||
    transaction.type === 'REFUND' ||
    transaction.type === 'CHARGEBACK' ||
    Number(transaction.amount) > 0
  )
}

/**
 * Faturas do cartão em abas: atual, histórico, compras pendentes/não alocadas e
 * configurações do ciclo. A fatura atual é somente OPEN; pagas e fechadas ficam
 * no histórico. Créditos não alocados aparecem separados das compras.
 */
export function InvoicesPage() {
  const { accountId = '' } = useParams<{ accountId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = parseTab(searchParams.get('tab'))

  const accountQuery = useQuery({
    queryKey: queryKeys.account(accountId),
    queryFn: () => financeApi.account(accountId),
    enabled: Boolean(accountId),
  })
  const invoicesQuery = useQuery({
    queryKey: queryKeys.invoicesByCard(accountId),
    queryFn: () => financeApi.invoices(accountId),
    enabled: Boolean(accountId),
  })
  const transactionsQuery = useQuery({
    queryKey: queryKeys.transactionFilters({ accountId, scope: 'card-invoices' }),
    queryFn: () => financeApi.transactions({ limit: 100, accountId }),
    enabled: Boolean(accountId),
  })

  const account = accountQuery.data
  const currency = account?.currency ?? 'BRL'
  const invoices = invoicesQuery.data?.items ?? []
  const currentInvoice = invoices.find((invoice) => invoice.status === 'OPEN') ?? null
  const history = currentInvoice
    ? invoices.filter((invoice) => invoice.id !== currentInvoice.id)
    : invoices
  const allTransactions = transactionsQuery.data?.items ?? []
  const pendingTransactions = currentInvoice
    ? allTransactions.filter(
        (transaction) =>
          transaction.status === 'PENDING' &&
          transaction.type === 'DEBIT' &&
          transaction.postedOn >= currentInvoice.cycleStart &&
          transaction.postedOn <= currentInvoice.cycleEnd,
      )
    : []
  const pendingTotal = addDecimals(
    pendingTransactions.map((transaction) => absoluteAmount(transaction.amount)),
  )
  const unallocated = allTransactions.filter(
    (transaction) =>
      !transaction.invoiceId &&
      (transaction.status === 'POSTED' || transaction.status === 'RECONCILED'),
  )
  const unallocatedPurchases = unallocated.filter(
    (transaction) => !isCreditTransaction(transaction),
  )
  const unallocatedCredits = unallocated.filter(isCreditTransaction)

  function selectTab(next: string) {
    const params = new URLSearchParams(searchParams)
    if (next === 'current') params.delete('tab')
    else params.set('tab', next)
    setSearchParams(params, { replace: false })
  }

  return (
    <div className="space-y-6">
      <Link
        to={`/cards/${accountId}`}
        className="inline-flex min-h-11 items-center text-body-sm font-medium text-action underline underline-offset-4 hover:text-action-hover"
      >
        ← Cartão
      </Link>

      <PageHeader
        overline="Cartões"
        title={`Faturas · ${account?.alias ?? 'Cartão'}`}
        description="Faturas organizadas por ciclo local. Compras contam como despesa uma única vez; pagamentos apenas reduzem a dívida."
        meta={
          <>
            <Badge variant="neutral">{currency}</Badge>
            {currentInvoice ? <Badge variant="info">Fatura atual aberta</Badge> : null}
          </>
        }
        actions={
          <Link to={`/cards/${accountId}`} className={cn(buttonVariants({ variant: 'secondary' }))}>
            <ReceiptText aria-hidden="true" className="size-4" />
            Abrir cartão
          </Link>
        }
      />

      {accountQuery.isError || invoicesQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as faturas">
          {(accountQuery.error ?? invoicesQuery.error) instanceof ApiError
            ? ((accountQuery.error ?? invoicesQuery.error) as ApiError).message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {invoicesQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando faturas…</span>
          <Skeleton className="h-56 rounded-card" />
        </div>
      ) : null}

      <Tabs value={tab} onValueChange={selectTab}>
        <TabList label="Seções de faturas">
          <Tab value="current">Fatura atual</Tab>
          <Tab value="history">Histórico</Tab>
          <Tab value="pending">Compras pendentes</Tab>
          <Tab value="settings">Configurações</Tab>
        </TabList>

        <TabPanel value="current">
          <div className="space-y-4">
            {currentInvoice ? (
              <CurrentInvoiceSection
                invoice={currentInvoice}
                currency={currency}
                pendingTransactions={pendingTransactions}
                pendingTotal={pendingTotal}
              />
            ) : (
              <Card className="p-5">
                <h2 className="text-section-title font-semibold text-ink">
                  Nenhuma fatura aberta no ciclo atual
                </h2>
                <p className="mt-1 text-body-sm text-ink-muted">
                  {invoices.length > 0
                    ? 'O histórico continua disponível e não é reescrito.'
                    : account?.cardProfile
                      ? 'Compras efetivadas aparecem aqui quando o ciclo gerar a primeira fatura.'
                      : 'Configure o ciclo do cartão para gerar faturas.'}
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  {!account?.cardProfile ? (
                    <Link
                      to={`/cards/${accountId}/invoices?tab=settings`}
                      className={cn(buttonVariants({ variant: 'primary' }))}
                    >
                      Configurar ciclo
                    </Link>
                  ) : null}
                  {history.length > 0 ? (
                    <Link
                      to={`/cards/${accountId}/invoices?tab=history`}
                      className={cn(buttonVariants({ variant: 'secondary' }))}
                    >
                      Ver histórico
                    </Link>
                  ) : null}
                </div>
              </Card>
            )}
          </div>
        </TabPanel>

        <TabPanel value="history">
          <div className="space-y-3">
            <div>
              <h2 className="text-section-title font-semibold text-ink">Histórico de faturas</h2>
              <p className="text-caption text-ink-muted">
                Faturas já fechadas ou pagas não são reescritas.
              </p>
            </div>
            {history.length === 0 ? (
              <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                Nenhuma fatura no histórico.
              </p>
            ) : (
              <ul className="grid gap-3 sm:grid-cols-2">
                {history.map((invoice) => (
                  <li key={invoice.id}>
                    <InvoiceHistoryCard invoice={invoice} currency={currency} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </TabPanel>

        <TabPanel value="pending">
          <div className="space-y-5">
            <div>
              <h2 className="text-section-title font-semibold text-ink">Compras pendentes</h2>
              <p className="text-caption text-ink-muted">
                Pendentes ainda não entram no total nem no saldo devedor. Elas passam a compor a
                fatura quando forem efetivadas.
              </p>
            </div>

            {currentInvoice ? (
              <Card className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-body-sm font-semibold text-ink">
                      Ciclo {formatDate(currentInvoice.cycleStart)} a{' '}
                      {formatDate(currentInvoice.cycleEnd)}
                    </p>
                    <p className="text-caption text-ink-muted">
                      {pendingTransactions.length} compra(s) pendente(s)
                    </p>
                  </div>
                  <p className="numeric text-body-sm font-semibold text-expense">
                    {formatMoney(pendingTotal, currency)}
                  </p>
                </div>
                {pendingTransactions.length > 0 ? (
                  <ul className="mt-3 divide-y divide-line rounded-control border border-line">
                    {pendingTransactions.map((transaction) => (
                      <li
                        key={transaction.id}
                        className="flex items-baseline justify-between gap-3 px-3 py-2 text-body-sm"
                      >
                        <span className="min-w-0 truncate text-ink">
                          {formatDate(transaction.postedOn)} ·{' '}
                          {transaction.description ?? 'Sem descrição'}
                        </span>
                        <span className="numeric shrink-0 text-ink">
                          {formatMoney(transaction.amount, transaction.currency)}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-3 text-body-sm text-ink-muted">
                    Nenhuma compra pendente neste ciclo.
                  </p>
                )}
              </Card>
            ) : (
              <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                Sem fatura aberta, não há compras pendentes de ciclo para revisar.
              </p>
            )}

            <section className="space-y-3" aria-labelledby="unallocated-heading">
              <div>
                <h2 id="unallocated-heading" className="text-section-title font-semibold text-ink">
                  Não alocadas
                </h2>
                <p className="text-caption text-ink-muted">
                  Compras e créditos efetivados que ainda não pertencem a uma fatura gerada.
                </p>
              </div>

              <UnallocatedGroup
                title="Compras não alocadas"
                description={
                  account?.cardProfile
                    ? 'Compras ainda sem fatura. Verifique o ciclo ou a data da compra.'
                    : 'Sem ciclo configurado, as compras permanecem não alocadas.'
                }
                icon={<ShoppingCart aria-hidden="true" className="size-4" />}
                transactions={unallocatedPurchases}
                currency={currency}
                tone="expense"
              />

              <UnallocatedGroup
                title="Créditos não alocados"
                description="Estornos e créditos reduzirão faturas futuras; não são compras."
                icon={<CircleDollarSign aria-hidden="true" className="size-4" />}
                transactions={unallocatedCredits}
                currency={currency}
                tone="income"
              />
            </section>
          </div>
        </TabPanel>

        <TabPanel value="settings">
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Settings2 aria-hidden="true" className="size-4 text-ink-muted" />
              <h2 className="text-section-title font-semibold text-ink">Ciclo do cartão</h2>
            </div>
            <CardCycleSettings
              accountId={accountId}
              profile={account?.cardProfile}
              currency={currency}
            />
          </div>
        </TabPanel>
      </Tabs>
    </div>
  )
}

function CurrentInvoiceSection({
  invoice,
  currency,
  pendingTransactions,
  pendingTotal,
}: {
  readonly invoice: Invoice
  readonly currency: string
  readonly pendingTransactions: readonly Transaction[]
  readonly pendingTotal: string
}) {
  return (
    <Card className="space-y-4 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="overline text-ink-muted">Ciclo em andamento</p>
          <h2 className="mt-1 text-section-title font-semibold text-ink">Fatura atual</h2>
          <p className="mt-1 text-body-sm text-ink-muted">
            {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)} · fecha{' '}
            {formatDate(invoice.closingDate)} · vence {formatDate(invoice.dueDate)}
          </p>
        </div>
        <Badge variant={invoice.status === 'OVERDUE' ? 'expense' : 'info'}>
          {STATUS_LABELS[invoice.status] ?? invoice.status}
        </Badge>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Total efetivado" value={formatMoney(invoice.total, currency)} />
        <Metric label="Pago" value={formatMoney(invoice.paymentsTotal, currency)} />
        <Metric label="Saldo devedor" value={formatMoney(invoice.outstanding, currency)} />
        <Metric
          label={`Pendentes (${pendingTransactions.length})`}
          value={formatMoney(pendingTotal, currency)}
        />
        {invoice.minimumPayment ? (
          <Metric label="Pagamento mínimo" value={formatMoney(invoice.minimumPayment, currency)} />
        ) : null}
        {Number(invoice.appliedCredit) > 0 ? (
          <Metric label="Crédito aplicado" value={formatMoney(invoice.appliedCredit, currency)} />
        ) : null}
      </dl>

      <p className="text-caption text-ink-muted">
        Compras pendentes ainda não entram no total nem no saldo devedor.
      </p>

      {invoice.missingPurchases ? (
        <Alert variant="warning" title="Histórico do ciclo incompleto">
          Há pagamento registrado sem compras locais no ciclo. Concilie com o total do provedor ou
          registre um ajuste explícito.
        </Alert>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Link to={`/invoices/${invoice.id}`} className={cn(buttonVariants({ variant: 'primary' }))}>
          Abrir fatura atual
        </Link>
        <Link
          to={`/cards/${invoice.cardAccountId}/invoices?tab=pending`}
          className={cn(buttonVariants({ variant: 'secondary' }))}
        >
          <CalendarClock aria-hidden="true" className="size-4" />
          Compras pendentes
        </Link>
      </div>
    </Card>
  )
}

function InvoiceHistoryCard({ invoice, currency }: { invoice: Invoice; currency: string }) {
  return (
    <Link
      to={`/invoices/${invoice.id}`}
      className="block h-full rounded-card border border-line bg-surface p-4 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-body-sm font-semibold text-ink">
            {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)}
          </p>
          <p className="mt-1 text-caption text-ink-muted">Vence em {formatDate(invoice.dueDate)}</p>
        </div>
        <Badge
          variant={
            invoice.status === 'OVERDUE'
              ? 'expense'
              : invoice.status === 'PAID'
                ? 'income'
                : 'neutral'
          }
        >
          {STATUS_LABELS[invoice.status] ?? invoice.status}
        </Badge>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2 text-body-sm">
        <div>
          <dt className="text-caption text-ink-muted">Total</dt>
          <dd className="numeric font-semibold text-ink">{formatMoney(invoice.total, currency)}</dd>
        </div>
        <div>
          <dt className="text-caption text-ink-muted">Pago</dt>
          <dd className="numeric font-semibold text-ink">
            {formatMoney(invoice.paymentsTotal, currency)}
          </dd>
        </div>
        <div>
          <dt className="text-caption text-ink-muted">Em aberto</dt>
          <dd className="numeric font-semibold text-expense">
            {formatMoney(invoice.outstanding, currency)}
          </dd>
        </div>
        {invoice.minimumPayment ? (
          <div>
            <dt className="text-caption text-ink-muted">Mínimo</dt>
            <dd className="numeric font-semibold text-ink">
              {formatMoney(invoice.minimumPayment, currency)}
            </dd>
          </div>
        ) : null}
      </dl>
      <div className="mt-2 flex flex-wrap gap-1">
        {invoice.missingPurchases ? <Badge variant="warning">Histórico incompleto</Badge> : null}
        {invoice.providerBill ? (
          <Badge variant={invoice.providerBill.reconciled ? 'income' : 'warning'}>
            {invoice.providerBill.reconciled ? 'Conciliada' : 'Divergente'}
          </Badge>
        ) : null}
      </div>
    </Link>
  )
}

function UnallocatedGroup({
  title,
  description,
  icon,
  transactions,
  currency,
  tone,
}: {
  readonly title: string
  readonly description: string
  readonly icon: ReactNode
  readonly transactions: readonly Transaction[]
  readonly currency: string
  readonly tone: 'expense' | 'income'
}) {
  return (
    <Card variant={transactions.length > 0 ? 'muted' : 'default'} className="p-4">
      <div className="flex items-start gap-2">
        <span className={tone === 'expense' ? 'text-expense' : 'text-income'}>{icon}</span>
        <div>
          <h3 className="text-body-sm font-semibold text-ink">
            {title} <Badge variant="neutral">{transactions.length}</Badge>
          </h3>
          <p className="mt-1 text-caption text-ink-muted">{description}</p>
        </div>
      </div>
      {transactions.length > 0 ? (
        <ul className="mt-3 divide-y divide-line rounded-control border border-line bg-surface">
          {transactions.map((transaction) => (
            <li
              key={transaction.id}
              className="flex items-baseline justify-between gap-3 px-3 py-2 text-body-sm"
            >
              <span className="min-w-0 truncate text-ink">
                {formatDate(transaction.postedOn)} · {transaction.description ?? 'Sem descrição'}
              </span>
              <span
                className={cn(
                  'numeric shrink-0 font-semibold',
                  tone === 'income' ? 'text-income' : 'text-expense',
                )}
              >
                {formatMoney(
                  tone === 'income' ? absoluteAmount(transaction.amount) : transaction.amount,
                  currency,
                )}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-caption text-ink-muted">Nenhum item nesta situação.</p>
      )}
    </Card>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-control border border-line bg-surface-muted p-3">
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="numeric mt-1 text-body-sm font-semibold text-ink">{value}</dd>
    </div>
  )
}
