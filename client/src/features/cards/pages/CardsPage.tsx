import { Link } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type CardOverviewItem } from '@/features/integration/api'

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

/**
 * Painel de cartões de crédito.
 *
 * Separa o passivo de cartão das contas correntes: mostra fatura atual, saldo devedor, limite,
 * utilização e próximo vencimento. Cartões sem ciclo aparecem como pendência explícita, sem
 * inventar datas ou valores.
 */
export function CardsPage() {
  const overviewQuery = useQuery({
    queryKey: ['cards', 'overview'],
    queryFn: financeApi.cardOverview,
  })

  const overview = overviewQuery.data
  const cards = overview?.items ?? []
  const utilization = utilizationPercent(overview?.totalUsed, overview?.totalLimit)

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Passivo do dia a dia
          </p>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Cartões de crédito</h1>
          <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
            Acompanhe faturas, limites e vencimentos separadamente das contas correntes. Compras
            contam como despesa uma única vez; pagamentos reduzem o saldo devedor.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to="/accounts"
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Ver contas
          </Link>
          <Link
            to="/settings/integration"
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Gerenciar conexões
          </Link>
        </div>
      </header>

      {overviewQuery.isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando cartões…
        </p>
      ) : null}

      {overviewQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {overviewQuery.error instanceof ApiError
            ? overviewQuery.error.message
            : 'Não foi possível carregar os cartões.'}
        </p>
      ) : null}

      {overview && cards.length === 0 ? (
        <section className="rounded-card border border-line bg-surface p-6">
          <h2 className="text-lg font-semibold text-ink">Nenhum cartão de crédito</h2>
          <p className="mt-2 text-sm leading-relaxed text-ink-muted">
            Configure sua integração e adicione um item com cartões para acompanhar faturas e
            limites aqui.
          </p>
          <Link
            to="/settings/integration"
            className="mt-4 inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
          >
            Configurar integração
          </Link>
        </section>
      ) : null}

      {overview && cards.length > 0 ? (
        <>
          <section aria-labelledby="cards-summary-heading" className="space-y-3">
            <h2 id="cards-summary-heading" className="text-lg font-semibold text-ink">
              Resumo dos cartões
            </h2>
            <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <SummaryTile
                label="Saldo devedor"
                value={formatMoney(overview.totalOutstanding, 'BRL')}
                hint="Soma das faturas não pagas dos cartões em BRL."
              />
              <SummaryTile
                label="Limite total"
                value={formatMoney(overview.totalLimit, 'BRL')}
                hint="Soma dos limites conhecidos dos cartões em BRL."
              />
              <SummaryTile
                label="Utilização do limite"
                value={utilization == null ? 'Não informada' : `${formatPercent(utilization)}%`}
                hint={
                  utilization == null
                    ? 'Limite total não informado.'
                    : `${formatMoney(overview.totalUsed, 'BRL')} de ${formatMoney(overview.totalLimit, 'BRL')} utilizados. Pode incluir compras pendentes.`
                }
              />
              <SummaryTile
                label="Próximo vencimento"
                value={overview.nextDueDate ? formatDate(overview.nextDueDate) : 'Sem pendência'}
                hint="Vencimento da fatura pendente mais próxima ou do ciclo atual."
              />
            </dl>
            <p className="text-xs leading-relaxed text-ink-muted">
              Totais consolidados consideram apenas cartões em BRL. Cartões em outra moeda aparecem
              na lista, mas não entram na soma. O saldo devedor usa faturas com transações
              efetivadas; o valor utilizado informado pelo provedor pode incluir compras pendentes.
            </p>
          </section>

          <section aria-labelledby="cards-list-heading" className="space-y-3">
            <h2 id="cards-list-heading" className="text-lg font-semibold text-ink">
              Seus cartões
            </h2>
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

function CardTile({ card }: { readonly card: CardOverviewItem }) {
  const utilization = card.utilization == null ? null : Number(card.utilization)
  const hasOverdueInvoice = card.nextDueInvoice?.status === 'OVERDUE'

  return (
    <article className="flex h-full flex-col gap-4 rounded-card border border-line bg-surface p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-ink">
            {card.alias ?? 'Cartão sem apelido'}
          </h3>
          <p className="mt-1 truncate text-sm text-ink-muted">
            {card.institutionName ?? 'Instituição não informada'} · {card.currency}
          </p>
          {card.brand || card.level || card.last4 ? (
            <p className="mt-1 truncate text-xs text-ink-muted">
              {[card.brand, card.level].filter(Boolean).join(' · ')}
              {card.last4 ? `${card.brand || card.level ? ' · ' : ''}•••• ${card.last4}` : ''}
            </p>
          ) : null}
        </div>
        <span className="shrink-0 rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink">
          {ACCOUNT_STATUS_LABELS[card.status] ?? card.status}
        </span>
      </div>

      {card.hasCycle ? (
        <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
          <dt className="text-ink-muted">Saldo devedor</dt>
          <dd className="numeric text-right font-medium text-ink">
            {formatMoney(card.outstanding, card.currency)}
          </dd>
          <dt className="text-ink-muted">Valor utilizado</dt>
          <dd className="numeric text-right text-ink">
            {formatMoney(card.usedAmount, card.currency)}
          </dd>
          <dt className="text-ink-muted">
            {card.currentInvoice?.status === 'OPEN' ? 'Fatura atual' : 'Última fatura'}
          </dt>
          <dd className="numeric text-right text-ink">
            {card.currentInvoice
              ? formatMoney(card.currentInvoice.total, card.currency)
              : 'Nenhuma fatura gerada'}
          </dd>
          <dt className="text-ink-muted">Próximo vencimento</dt>
          <dd className="text-right text-ink">
            {card.nextDueDate
              ? formatDate(card.nextDueDate)
              : card.currentInvoice?.status === 'OPEN'
                ? formatDate(card.currentInvoice.dueDate)
                : 'Sem pendência'}
          </dd>
          <dt className="text-ink-muted">Limite</dt>
          <dd className="numeric text-right text-ink">
            {formatMoney(card.creditLimit, card.currency)}
          </dd>
          {card.availableCreditLimit ? (
            <>
              <dt className="text-ink-muted">Disponível</dt>
              <dd className="numeric text-right text-ink">
                {formatMoney(card.availableCreditLimit, card.currency)}
              </dd>
            </>
          ) : null}
        </dl>
      ) : (
        <div className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-3">
          <p className="text-sm font-medium text-ink">Ciclo não configurado</p>
          <p className="text-xs leading-relaxed text-ink-muted">
            Sem fechamento e vencimento, as compras ficam não alocadas e não geram faturas. Valor
            utilizado informado:{' '}
            <span className="numeric font-medium text-ink">
              {formatMoney(card.usedAmount, card.currency)}
            </span>
            .
          </p>
          <Link
            to={`/cards/${card.accountId}/invoices`}
            className="inline-flex min-h-11 items-center rounded-card border border-line bg-surface px-3 text-xs font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Configurar ciclo
          </Link>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between text-xs text-ink-muted">
          <span>Utilização do limite</span>
          <span>{utilization == null ? 'Não informada' : `${formatPercent(utilization)}%`}</span>
        </div>
        {utilization == null ? null : (
          <div
            role="progressbar"
            aria-valuenow={Math.min(100, Math.max(0, utilization))}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`Utilização do limite de ${card.alias ?? 'cartão'}`}
            className="mt-1 h-2 overflow-hidden rounded-full bg-surface-muted"
          >
            <div
              className={[
                'h-full rounded-full',
                utilization >= 90 ? 'bg-expense' : utilization >= 70 ? 'bg-warning' : 'bg-primary',
              ].join(' ')}
              style={{ width: `${Math.min(100, Math.max(0, utilization))}%` }}
            />
          </div>
        )}
      </div>

      {card.currentInvoice ? (
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="rounded-card border border-line bg-surface-muted px-2 py-0.5 text-ink">
            {formatDate(card.currentInvoice.cycleStart)} a{' '}
            {formatDate(card.currentInvoice.cycleEnd)}
          </span>
          <span
            className={[
              'rounded-card border px-2 py-0.5 font-medium',
              card.currentInvoice.status === 'OVERDUE'
                ? 'border-expense/50 bg-expense/5 text-expense'
                : 'border-line bg-surface text-ink',
            ].join(' ')}
          >
            {INVOICE_STATUS_LABELS[card.currentInvoice.status] ?? card.currentInvoice.status}
          </span>
        </div>
      ) : null}

      {hasOverdueInvoice ? (
        <p className="rounded-card border border-expense/40 bg-expense/5 p-3 text-xs leading-relaxed text-ink">
          Há fatura vencida com saldo devedor. O atraso prevalece sobre pagamentos parciais.
        </p>
      ) : null}

      {card.unallocatedCount > 0 ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-xs leading-relaxed text-ink">
          {card.unallocatedCount} transação(ões) sem fatura. Verifique o ciclo ou abra as transações
          do cartão.
        </p>
      ) : null}

      <div className="mt-auto flex flex-wrap gap-2">
        <Link
          to={`/cards/${card.accountId}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-3 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
        >
          Abrir cartão
        </Link>
        <Link
          to={`/cards/${card.accountId}/invoices`}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Faturas
        </Link>
        <Link
          to={`/transactions?accountId=${card.accountId}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Transações
        </Link>
      </div>
    </article>
  )
}

function utilizationPercent(used: string | undefined, limit: string | undefined): number | null {
  const usedNumber = Number(used)
  const limitNumber = Number(limit)
  if (!Number.isFinite(usedNumber) || !Number.isFinite(limitNumber) || limitNumber <= 0) {
    return null
  }
  return (usedNumber / limitNumber) * 100
}

function formatPercent(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)
}

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}
