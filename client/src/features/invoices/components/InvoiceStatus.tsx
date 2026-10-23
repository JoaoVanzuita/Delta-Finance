import { formatMoney } from '@/features/accounts/format'
import type { Invoice } from '@/features/integration/api'

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

interface InvoiceStatusProps {
  readonly invoice: Invoice
  readonly creditBalance?: string
}

/** Estado, atraso, progresso de pagamento, ajustes e crédito separado dos totais. */
export function InvoiceStatus({ invoice, creditBalance }: InvoiceStatusProps) {
  const total = Number(invoice.total)
  const paid = Number(invoice.paymentsTotal)
  const percent = total <= 0 ? 100 : Math.min(100, Math.round((paid / total) * 100))

  return (
    <section
      aria-labelledby="invoice-status-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="invoice-status-heading" className="text-lg font-semibold text-ink">
            Fatura {formatDate(invoice.cycleStart)} a {formatDate(invoice.cycleEnd)}
          </h2>
          <p className="mt-1 text-sm text-ink-muted">
            Fechamento {formatDate(invoice.closingDate)} · vencimento {formatDate(invoice.dueDate)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span
            className={[
              'rounded-card border px-3 py-1 text-sm font-semibold',
              invoice.status === 'OVERDUE'
                ? 'border-expense/50 bg-expense/5 text-expense'
                : invoice.status === 'PAID'
                  ? 'border-income/40 bg-income/5 text-ink'
                  : 'border-line bg-surface-muted text-ink',
            ].join(' ')}
          >
            {STATUS_LABELS[invoice.status] ?? invoice.status}
          </span>
          {invoice.hasAdjustments ? (
            <span className="rounded-card border border-warning/40 bg-warning/5 px-3 py-1 text-sm text-ink">
              {invoice.adjustmentCount} ajuste(s)
            </span>
          ) : null}
        </div>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
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
          <dt className="text-xs text-ink-muted">Crédito aplicado</dt>
          <dd className="numeric mt-1 text-sm font-semibold text-ink">
            {formatMoney(invoice.appliedCredit, 'BRL')}
          </dd>
        </div>
      </dl>

      <div>
        <div className="flex items-center justify-between text-xs text-ink-muted">
          <span>Progresso do pagamento</span>
          <span>{percent}%</span>
        </div>
        <div
          role="progressbar"
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Progresso do pagamento da fatura"
          className="mt-1 h-2 overflow-hidden rounded-full bg-surface-muted"
        >
          <div className="h-full rounded-full bg-primary" style={{ width: `${percent}%` }} />
        </div>
      </div>

      {creditBalance ? (
        <p className="text-sm text-ink-muted">
          Crédito acumulado do cartão para próximas faturas:{' '}
          <span className="numeric font-medium text-ink">{formatMoney(creditBalance, 'BRL')}</span>{' '}
          — não compõe receitas, despesas nem saldo atual.
        </p>
      ) : null}
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
