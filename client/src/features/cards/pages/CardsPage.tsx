import { useQuery } from '@tanstack/react-query'
import { CreditCard, Plus, Wallet } from 'lucide-react'
import { Link } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatMoney, formatPercent } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type CardOverviewItem, type Invoice } from '@/features/integration/api'

const ACCOUNT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativa',
  INACTIVE: 'Inativa',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrada',
}

const INVOICE_STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

function invoiceIsCurrent(invoice: Invoice | null | undefined): invoice is Invoice {
  return invoice?.status === 'OPEN'
}

/**
 * Painel de cartões.
 *
 * O total consolidado usa apenas cartões em BRL. Cada cartão prioriza saldo
 * devedor, limite, utilização, disponível e próximo vencimento; a fatura é
 * rotulada como atual somente quando está OPEN.
 */
export function CardsPage() {
  const overviewQuery = useQuery({
    queryKey: queryKeys.cardOverview,
    queryFn: financeApi.cardOverview,
  })

  const overview = overviewQuery.data
  const cards = overview?.items ?? []
  const foreignCards = cards.filter((card) => card.currency !== 'BRL')
  const brlCards = cards.filter((card) => card.currency === 'BRL')

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Meu dinheiro"
        title="Cartões de crédito"
        description="Faturas, limites e vencimentos separados das contas correntes. Compras entram uma única vez; pagamentos reduzem a dívida."
        actions={
          <>
            <Link to="/accounts" className={cn(buttonVariants({ variant: 'secondary' }))}>
              <Wallet aria-hidden="true" className="size-4" />
              Ver contas
            </Link>
            <Link
              to="/settings/integration"
              className={cn(buttonVariants({ variant: 'secondary' }))}
            >
              <Plus aria-hidden="true" className="size-4" />
              Adicionar conexão
            </Link>
          </>
        }
      />

      {overviewQuery.isLoading ? (
        <div className="space-y-4" role="status" aria-live="polite">
          <span className="sr-only">Carregando cartões…</span>
          <Skeleton className="h-36 rounded-panel" />
          <div className="grid gap-4 lg:grid-cols-2">
            <Skeleton className="h-72 rounded-card" />
            <Skeleton className="h-72 rounded-card" />
          </div>
        </div>
      ) : null}

      {overviewQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar os cartões">
          {overviewQuery.error instanceof ApiError
            ? overviewQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {overview && cards.length === 0 ? (
        <EmptyState
          icon={<CreditCard className="size-5" />}
          title="Nenhum cartão de crédito"
          description="Adicione uma conexão com cartões para acompanhar faturas, limites e vencimentos. Os dados aparecem separados das contas correntes."
          action={
            <Link to="/settings/integration" className={cn(buttonVariants({ variant: 'primary' }))}>
              Configurar integração
            </Link>
          }
        />
      ) : null}

      {overview && cards.length > 0 ? (
        <>
          <section aria-labelledby="cards-summary-heading" className="space-y-3">
            <h2 id="cards-summary-heading" className="sr-only">
              Resumo dos cartões
            </h2>
            <Card variant="danger" className="p-5 sm:p-6">
              <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                  <p className="overline text-ink-muted">Saldo devedor total</p>
                  <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-expense">
                    {brlCards.length === 0
                      ? 'Sem cartões em BRL'
                      : formatMoney(overview.totalOutstanding, 'BRL')}
                  </p>
                  <p className="mt-1 text-body-sm text-ink-muted">
                    Soma das faturas não pagas dos cartões em BRL. Não é somado ao saldo em contas.
                  </p>
                </div>
                <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-body-sm">
                  <div>
                    <p className="text-caption text-ink-muted">Limite total</p>
                    <p className="numeric font-semibold text-ink">
                      {formatMoney(overview.totalLimit, 'BRL')}
                    </p>
                  </div>
                  <div>
                    <p className="text-caption text-ink-muted">Cartões em BRL</p>
                    <p className="numeric font-semibold text-ink">{brlCards.length}</p>
                  </div>
                  <div className="col-span-2">
                    <p className="text-caption text-ink-muted">Próximo vencimento</p>
                    <p className="font-semibold text-ink">
                      {overview.nextDueDate ? formatDate(overview.nextDueDate) : 'Sem pendência'}
                    </p>
                  </div>
                </div>
              </div>
              <p className="mt-4 text-caption text-ink-muted">
                A utilização é exibida por cartão, na base informada pelo provedor.
              </p>
              {foreignCards.length > 0 ? (
                <p className="mt-4 rounded-control border border-warning/35 bg-warning-soft p-3 text-caption text-ink">
                  {foreignCards.length} cartão(ões) em outra moeda aparecem abaixo na moeda original
                  e não entram no total em BRL.
                </p>
              ) : null}
            </Card>
          </section>

          <section aria-labelledby="cards-list-heading" className="space-y-3">
            <div>
              <h2 id="cards-list-heading" className="text-section-title font-semibold text-ink">
                Seus cartões
              </h2>
              <p className="text-caption text-ink-muted">
                Ordenados pela API. Cada cartão mantém a moeda original.
              </p>
            </div>
            <ul className="grid gap-4 lg:grid-cols-2">
              {cards.map((card) => (
                <li key={card.accountId}>
                  <CardTile card={card} />
                </li>
              ))}
            </ul>
          </section>
        </>
      ) : null}
    </div>
  )
}

function CardTile({ card }: { readonly card: CardOverviewItem }) {
  const utilization = card.utilization == null ? null : Number(card.utilization)
  const hasOverdueInvoice = card.nextDueInvoice?.status === 'OVERDUE'
  const current = invoiceIsCurrent(card.currentInvoice) ? card.currentInvoice : null
  const lastInvoice = current ? null : card.currentInvoice

  return (
    <article className="flex h-full flex-col gap-4 rounded-card border border-line bg-surface p-4 shadow-[var(--shadow-card)]">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-body font-semibold text-ink">
            {card.alias ?? 'Cartão sem apelido'}
          </h3>
          <p className="mt-0.5 truncate text-caption text-ink-muted">
            {card.institutionName ?? 'Instituição não informada'} · {card.currency}
          </p>
          {card.brand || card.level || card.last4 ? (
            <p className="mt-1 truncate text-caption text-ink-muted">
              {[card.brand, card.level].filter(Boolean).join(' · ')}
              {card.last4 ? `${card.brand || card.level ? ' · ' : ''}•••• ${card.last4}` : ''}
            </p>
          ) : null}
        </div>
        <Badge variant={card.status === 'ACTIVE' ? 'neutral' : 'warning'}>
          {ACCOUNT_STATUS_LABELS[card.status] ?? card.status}
        </Badge>
      </div>

      {card.hasCycle ? (
        <div>
          <p className="text-caption text-ink-muted">Saldo devedor</p>
          <p className="numeric mt-1 text-metric-md font-semibold text-expense">
            {formatMoney(card.outstanding, card.currency)}
          </p>
        </div>
      ) : (
        <div className="space-y-2 rounded-control border border-warning/40 bg-warning-soft p-3">
          <p className="text-body-sm font-semibold text-ink">Ciclo não configurado</p>
          <p className="text-caption text-ink-muted">
            Sem fechamento e vencimento, as compras ficam não alocadas e não geram faturas.
          </p>
          <Link
            to={`/cards/${card.accountId}/invoices?tab=settings`}
            className="inline-flex min-h-11 items-center rounded-control border border-line bg-surface px-3 text-caption font-semibold text-ink hover:bg-surface-muted"
          >
            Configurar ciclo
          </Link>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between text-caption text-ink-muted">
          <span>Utilização do limite</span>
          <span className="numeric">
            {utilization == null ? 'Não informada' : formatPercent(utilization)}
          </span>
        </div>
        {utilization == null ? null : (
          <div
            role="progressbar"
            aria-valuenow={Math.min(100, Math.max(0, utilization))}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`Utilização do limite de ${card.alias ?? 'cartão'}`}
            className="mt-1 h-2 overflow-hidden rounded-pill bg-surface-muted"
          >
            <div
              className={cn(
                'h-full rounded-pill',
                utilization >= 90 ? 'bg-expense' : utilization >= 70 ? 'bg-warning' : 'bg-primary',
              )}
              style={{ width: `${Math.min(100, Math.max(0, utilization))}%` }}
            />
          </div>
        )}
        {card.hasCycle ? (
          <dl className="mt-3 grid grid-cols-3 gap-2 text-caption">
            <div>
              <dt className="text-ink-muted">Limite</dt>
              <dd className="numeric mt-0.5 font-semibold text-ink">
                {formatMoney(card.creditLimit, card.currency)}
              </dd>
            </div>
            <div>
              <dt className="text-ink-muted">Disponível</dt>
              <dd className="numeric mt-0.5 font-semibold text-ink">
                {card.availableCreditLimit
                  ? formatMoney(card.availableCreditLimit, card.currency)
                  : 'Não informado'}
              </dd>
            </div>
            <div>
              <dt className="text-ink-muted">Vencimento</dt>
              <dd className="mt-0.5 font-semibold text-ink">
                {card.nextDueDate ? formatDate(card.nextDueDate) : 'Sem pendência'}
              </dd>
            </div>
          </dl>
        ) : null}
      </div>

      {current || lastInvoice ? (
        <div className="rounded-control border border-line bg-surface-muted p-3">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="text-caption font-semibold text-ink">
                {current ? 'Fatura atual' : 'Última fatura'}
              </p>
              <p className="text-caption text-ink-muted">
                {formatDate((current ?? lastInvoice)?.cycleStart ?? '')} a{' '}
                {formatDate((current ?? lastInvoice)?.cycleEnd ?? '')} · vence{' '}
                {formatDate((current ?? lastInvoice)?.dueDate ?? '')}
              </p>
            </div>
            <div className="text-right">
              <p className="numeric text-body-sm font-semibold text-ink">
                {formatMoney((current ?? lastInvoice)?.total, card.currency)}
              </p>
              <Badge
                variant={
                  (current ?? lastInvoice)?.status === 'OVERDUE'
                    ? 'expense'
                    : (current ?? lastInvoice)?.status === 'PAID'
                      ? 'income'
                      : 'neutral'
                }
              >
                {INVOICE_STATUS_LABELS[(current ?? lastInvoice)?.status ?? ''] ?? '—'}
              </Badge>
            </div>
          </div>
        </div>
      ) : null}

      {hasOverdueInvoice ? (
        <p className="rounded-control border border-expense/40 bg-expense-soft p-3 text-caption text-ink">
          Há fatura vencida com saldo devedor. O atraso prevalece sobre pagamentos parciais.
        </p>
      ) : null}

      {card.unallocatedCount > 0 ? (
        <p className="rounded-control border border-warning/40 bg-warning-soft p-3 text-caption text-ink">
          {card.unallocatedCount} transação(ões) sem fatura. Verifique o ciclo ou abra as compras
          pendentes.
        </p>
      ) : null}

      <div className="mt-auto flex flex-wrap gap-2">
        <Link
          to={`/cards/${card.accountId}`}
          className={cn(buttonVariants({ variant: 'primary', size: 'sm' }))}
        >
          Abrir cartão
        </Link>
        <Link
          to={`/cards/${card.accountId}/invoices`}
          className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }))}
        >
          Faturas
        </Link>
        <Link
          to={`/transactions?accountId=${card.accountId}`}
          className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }))}
        >
          Transações
        </Link>
      </div>
    </article>
  )
}
