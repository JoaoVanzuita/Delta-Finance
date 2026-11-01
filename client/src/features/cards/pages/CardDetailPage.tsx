import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCard, ReceiptText, Settings2, Wallet } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'
import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { formatDate, formatMoney } from '@/lib/format'
import { invalidateCardViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

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

type CardTab = 'summary' | 'invoices' | 'transactions' | 'settings'

function parseTab(value: string | null): CardTab {
  return value === 'invoices' || value === 'transactions' || value === 'settings'
    ? value
    : 'summary'
}

function isOpenInvoice(invoice: Invoice | null | undefined): invoice is Invoice {
  return invoice?.status === 'OPEN'
}

/**
 * Cartão de crédito em abas: resumo, faturas, transações e configurações.
 * “Fatura atual” só é usada para invoice OPEN; fechada/paga aparece como última.
 */
export function CardDetailPage() {
  const { accountId = '' } = useParams<{ accountId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const tab = parseTab(searchParams.get('tab'))

  const accountQuery = useQuery({
    queryKey: queryKeys.account(accountId),
    queryFn: () => financeApi.account(accountId),
    enabled: Boolean(accountId),
  })
  const overviewQuery = useQuery({
    queryKey: queryKeys.cardOverview,
    queryFn: financeApi.cardOverview,
  })
  const invoicesQuery = useQuery({
    queryKey: queryKeys.invoicesByCard(accountId),
    queryFn: () => financeApi.invoices(accountId),
    enabled: Boolean(accountId),
  })
  const transactionsQuery = useQuery({
    queryKey: queryKeys.transactionFilters({ accountId, scope: 'card' }),
    queryFn: () => financeApi.transactions({ limit: 100, accountId }),
    enabled: Boolean(accountId),
  })

  const account = accountQuery.data
  const card = overviewQuery.data?.items.find((item) => item.accountId === accountId)
  const profile = account?.cardProfile
  const invoices = invoicesQuery.data?.items ?? []
  const openInvoice =
    invoices.find((invoice) => isOpenInvoice(invoice)) ??
    (isOpenInvoice(card?.currentInvoice) ? card.currentInvoice : null)
  const latestInvoice =
    [...invoices].sort((left, right) => right.cycleStart.localeCompare(left.cycleStart))[0] ??
    card?.currentInvoice ??
    null
  const otherInvoices = invoices.filter((invoice) => invoice.id !== openInvoice?.id)
  const transactions = transactionsQuery.data?.items ?? []
  const pendingTransactions = openInvoice
    ? transactions.filter(
        (transaction) =>
          transaction.status === 'PENDING' &&
          transaction.type === 'DEBIT' &&
          transaction.postedOn >= openInvoice.cycleStart &&
          transaction.postedOn <= openInvoice.cycleEnd,
      )
    : []
  const queryError = accountQuery.error ?? overviewQuery.error ?? invoicesQuery.error

  async function renameCard(displayName: string) {
    if (!account) return
    const updated = await financeApi.renameAccount(account.id, displayName)
    queryClient.setQueryData(queryKeys.account(account.id), updated)
    await invalidateCardViews(queryClient)
    return updated
  }

  async function resetCardName() {
    if (!account) return
    const updated = await financeApi.resetAccountName(account.id)
    queryClient.setQueryData(queryKeys.account(account.id), updated)
    await invalidateCardViews(queryClient)
    return updated
  }

  function selectTab(next: string) {
    const params = new URLSearchParams(searchParams)
    if (next === 'summary') params.delete('tab')
    else params.set('tab', next)
    setSearchParams(params, { replace: false })
  }

  if (accountQuery.isLoading || overviewQuery.isLoading) {
    return (
      <div className="space-y-4" role="status" aria-live="polite">
        <span className="sr-only">Carregando cartão…</span>
        <Skeleton className="h-24 max-w-2xl rounded-panel" />
        <Skeleton className="h-64 rounded-card" />
      </div>
    )
  }

  if (!account || !card) {
    return (
      <Alert variant="danger" title="Cartão não encontrado">
        {queryError instanceof ApiError
          ? queryError.message
          : 'Não foi possível carregar o cartão.'}{' '}
        <Link to="/cards" className="font-semibold text-action underline underline-offset-4">
          Voltar para cartões
        </Link>
      </Alert>
    )
  }

  const currency = account.currency

  return (
    <div className="space-y-6">
      <Link
        to="/cards"
        className="inline-flex min-h-11 items-center text-body-sm font-medium text-action underline underline-offset-4 hover:text-action-hover"
      >
        ← Cartões
      </Link>

      <PageHeader
        overline="Cartão de crédito"
        title={account.alias ?? card.alias ?? 'Cartão'}
        description={[
          account.institutionName ?? card.institutionName ?? 'Instituição não informada',
          profile?.brand,
          profile?.level,
          profile?.last4 ? `•••• ${profile.last4}` : null,
        ]
          .filter(Boolean)
          .join(' · ')}
        meta={
          <>
            <Badge variant={account.status === 'ACTIVE' ? 'income' : 'warning'}>
              {STATUS_LABELS[account.status ?? card.status] ?? '—'}
            </Badge>
            <Badge variant="warning">{currency}</Badge>
            {account.nameCustomized && account.providerName ? (
              <span className="text-caption text-ink-muted">
                Nome original: {account.providerName}
              </span>
            ) : null}
          </>
        }
        actions={
          <>
            <Link
              to={`/cards/${accountId}/invoices`}
              className={cn(buttonVariants({ variant: 'secondary' }))}
            >
              <ReceiptText aria-hidden="true" className="size-4" />
              Faturas
            </Link>
            <Link
              to={`/transactions?accountId=${accountId}`}
              className={cn(buttonVariants({ variant: 'secondary' }))}
            >
              <Wallet aria-hidden="true" className="size-4" />
              Transações
            </Link>
            {account.origin === 'IMPORTED' ? (
              <DisplayNameEditor
                currentName={account.alias ?? null}
                originalName={account.providerName ?? null}
                customized={Boolean(account.nameCustomized)}
                onRename={renameCard}
                onReset={resetCardName}
              />
            ) : null}
          </>
        }
      />

      {queryError ? (
        <Alert variant="danger" title="Parte dos dados do cartão não carregou">
          {queryError instanceof ApiError
            ? queryError.message
            : 'Os valores disponíveis continuam visíveis; tente novamente.'}
        </Alert>
      ) : null}

      <Tabs value={tab} onValueChange={selectTab}>
        <TabList label="Seções do cartão">
          <Tab value="summary">Resumo</Tab>
          <Tab value="invoices">Faturas</Tab>
          <Tab value="transactions">Transações</Tab>
          <Tab value="settings">Configurações</Tab>
        </TabList>

        <TabPanel value="summary">
          <div className="space-y-4">
            <Card variant="danger" className="p-5 sm:p-6">
              <p className="overline text-ink-muted">Saldo devedor</p>
              <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-expense">
                {formatMoney(card.outstanding, currency)}
              </p>
              <p className="mt-1 text-body-sm text-ink-muted">
                Soma das faturas não pagas deste cartão. Não é saldo em conta.
              </p>
            </Card>

            <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <Metric
                label="Limite total"
                value={formatMoney(profile?.creditLimit, currency)}
                hint="Limite informado pelo provedor ou configurado manualmente."
              />
              <Metric
                label="Limite disponível"
                value={formatMoney(profile?.availableCreditLimit, currency)}
                hint="Valor disponível informado oficialmente pelo provedor."
              />
              <Metric
                label="Utilizado"
                value={formatMoney(card.usedAmount, currency)}
                hint="Pode incluir compras ainda pendentes."
              />
              <Metric
                label="Próximo vencimento"
                value={card.nextDueDate ? formatDate(card.nextDueDate) : 'Sem pendência'}
                hint="Vencimento da fatura pendente mais próxima."
              />
              <Metric
                label="Crédito acumulado"
                value={formatMoney(profile?.creditBalance, currency)}
                hint="Crédito para próximas faturas; não é receita nem saldo em conta."
              />
              <Metric
                label="Compras não alocadas"
                value={String(card.unallocatedCount ?? 0)}
                hint="Compras efetivadas ainda sem fatura. Verifique o ciclo."
              />
            </dl>

            {openInvoice ? (
              <InvoiceStrip invoice={openInvoice} currency={currency} current />
            ) : latestInvoice ? (
              <InvoiceStrip invoice={latestInvoice} currency={currency} />
            ) : (
              <Card className="p-5">
                <h2 className="text-section-title font-semibold text-ink">Nenhuma fatura gerada</h2>
                <p className="mt-1 text-body-sm text-ink-muted">
                  {profile
                    ? 'Compras efetivadas aparecem aqui quando o ciclo gerar a primeira fatura.'
                    : 'Configure o ciclo para que as compras sejam alocadas em faturas.'}
                </p>
              </Card>
            )}

            {card.unallocatedCount > 0 ? (
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-card border border-warning/40 bg-warning-soft p-4">
                <p className="text-body-sm text-ink">
                  <strong className="font-semibold">
                    {card.unallocatedCount} compra(s) sem fatura
                  </strong>{' '}
                  — verifique o ciclo ou revise as compras não alocadas.
                </p>
                <Link
                  to={`/cards/${accountId}/invoices?tab=pending`}
                  className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }))}
                >
                  Revisar compras
                </Link>
              </div>
            ) : null}

            {pendingTransactions.length > 0 && openInvoice ? (
              <Alert
                variant="warning"
                title={`${pendingTransactions.length} compra(s) pendente(s)`}
              >
                Elas ainda não compõem o total nem o saldo devedor.{' '}
                <Link
                  to={`/invoices/${openInvoice.id}`}
                  className="font-semibold text-action underline underline-offset-4"
                >
                  Abrir fatura atual
                </Link>
              </Alert>
            ) : null}
          </div>
        </TabPanel>

        <TabPanel value="invoices">
          <div className="space-y-5">
            {invoicesQuery.isLoading ? (
              <p role="status" className="text-body-sm text-ink-muted">
                Carregando faturas…
              </p>
            ) : null}
            {invoicesQuery.isError ? (
              <Alert variant="danger" title="Faturas indisponíveis">
                {invoicesQuery.error instanceof ApiError
                  ? invoicesQuery.error.message
                  : 'Não foi possível carregar as faturas deste cartão.'}
              </Alert>
            ) : null}

            {openInvoice ? (
              <section className="space-y-3">
                <h2 className="text-section-title font-semibold text-ink">Fatura atual</h2>
                <InvoiceStrip invoice={openInvoice} currency={currency} current detailed />
              </section>
            ) : (
              <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                Nenhuma fatura aberta no ciclo atual. O histórico abaixo permanece disponível.
              </p>
            )}

            <section className="space-y-3">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <div>
                  <h2 className="text-section-title font-semibold text-ink">Histórico</h2>
                  <p className="text-caption text-ink-muted">
                    Faturas fechadas e pagas não são reescritas.
                  </p>
                </div>
                <Link
                  to={`/cards/${accountId}/invoices`}
                  className="inline-flex min-h-11 items-center text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
                >
                  Abrir faturas
                </Link>
              </div>
              {otherInvoices.length === 0 ? (
                <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                  Nenhuma fatura anterior.
                </p>
              ) : (
                <ul className="grid gap-3 sm:grid-cols-2">
                  {otherInvoices.map((invoice) => (
                    <li key={invoice.id}>
                      <InvoiceStrip invoice={invoice} currency={currency} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </TabPanel>

        <TabPanel value="transactions">
          <div className="space-y-3">
            <div className="flex flex-wrap items-end justify-between gap-2">
              <div>
                <h2 className="text-section-title font-semibold text-ink">Transações recentes</h2>
                <p className="text-caption text-ink-muted">
                  Últimas transações do cartão, incluindo pendentes.
                </p>
              </div>
              <Link
                to={`/transactions?accountId=${accountId}`}
                className="inline-flex min-h-11 items-center text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
              >
                Ver todas
              </Link>
            </div>

            {transactionsQuery.isLoading ? (
              <p role="status" className="text-body-sm text-ink-muted">
                Carregando transações…
              </p>
            ) : null}
            {transactionsQuery.isError ? (
              <Alert variant="danger" title="Transações indisponíveis">
                {transactionsQuery.error instanceof ApiError
                  ? transactionsQuery.error.message
                  : 'Não foi possível carregar as transações do cartão.'}
              </Alert>
            ) : null}
            {transactionsQuery.isSuccess && transactions.length === 0 ? (
              <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                Nenhuma transação encontrada.
              </p>
            ) : null}
            {transactions.length > 0 ? (
              <ul className="divide-y divide-line rounded-card border border-line bg-surface">
                {transactions.map((transaction) => (
                  <li
                    key={transaction.id}
                    className="flex min-h-14 items-center justify-between gap-3 px-4 py-2.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-body-sm font-medium text-ink">
                        {transaction.description || 'Sem descrição'}
                      </p>
                      <p className="text-caption text-ink-muted">
                        {formatDate(transaction.postedOn)}
                        {transaction.categoryName ? ` · ${transaction.categoryName}` : ''}
                        {transaction.status === 'PENDING' ? ' · Pendente' : ''}
                      </p>
                    </div>
                    <span
                      className={cn(
                        'numeric shrink-0 text-body-sm font-semibold',
                        Number(transaction.amount) < 0 ? 'text-expense' : 'text-income',
                      )}
                    >
                      {formatMoney(transaction.amount, transaction.currency)}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </TabPanel>

        <TabPanel value="settings">
          <div className="space-y-4">
            <Card className="p-5">
              <div className="flex items-center gap-2">
                <Settings2 aria-hidden="true" className="size-4 text-ink-muted" />
                <h2 className="text-section-title font-semibold text-ink">Identificação</h2>
              </div>
              {account.origin === 'IMPORTED' ? (
                <div className="mt-3">
                  <p className="text-body-sm text-ink-muted">
                    O nome de exibição é local. O nome original do provedor permanece preservado.
                  </p>
                  <div className="mt-3">
                    <DisplayNameEditor
                      currentName={account.alias ?? null}
                      originalName={account.providerName ?? null}
                      customized={Boolean(account.nameCustomized)}
                      onRename={renameCard}
                      onReset={resetCardName}
                    />
                  </div>
                </div>
              ) : (
                <p className="mt-3 text-body-sm text-ink-muted">
                  Cartão manual: o nome é editado nos dados da conta.
                </p>
              )}
            </Card>

            <CardCycleSettings accountId={accountId} profile={profile} currency={currency} />
          </div>
        </TabPanel>
      </Tabs>
    </div>
  )
}

function Metric({
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
      <dt className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
        {label}
      </dt>
      <dd className="numeric mt-2 text-metric-sm font-semibold text-ink">{value}</dd>
      <dd className="mt-2 text-caption leading-relaxed text-ink-muted">{hint}</dd>
    </div>
  )
}

function InvoiceStrip({
  invoice,
  currency,
  current = false,
  detailed = false,
}: {
  readonly invoice: Invoice
  readonly currency: string
  readonly current?: boolean
  readonly detailed?: boolean
}) {
  return (
    <Link
      to={`/invoices/${invoice.id}`}
      className="block rounded-card border border-line bg-surface p-4 transition-colors hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-body-sm font-semibold text-ink">
            {current ? 'Fatura atual' : 'Fatura'} · {formatDate(invoice.cycleStart)} a{' '}
            {formatDate(invoice.cycleEnd)}
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            Fecha {formatDate(invoice.closingDate)} · vence {formatDate(invoice.dueDate)}
          </p>
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
          {INVOICE_STATUS_LABELS[invoice.status] ?? invoice.status}
        </Badge>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-2 text-body-sm sm:grid-cols-4">
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
        {detailed ? (
          <div>
            <dt className="text-caption text-ink-muted">Crédito aplicado</dt>
            <dd className="numeric font-semibold text-ink">
              {formatMoney(invoice.appliedCredit, currency)}
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
        {invoice.status === 'PARTIALLY_PAID' ? (
          <span className="inline-flex items-center gap-1 text-caption text-ink-muted">
            <CreditCard aria-hidden="true" className="size-3" />
            Pagamento parcial
          </span>
        ) : null}
      </div>
    </Link>
  )
}
