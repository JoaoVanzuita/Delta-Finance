import type { ReactNode } from 'react'

import {
  ArrowDownRight,
  ArrowUpRight,
  CreditCard,
  Scale,
  TrendingDown,
  TrendingUp,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import type { DashboardSummary } from '@/features/dashboard/api'
import { formatMoney } from '@/lib/format'
import { cn } from '@/lib/cn'

import { BalanceReference } from './DashboardStates'

interface DashboardOverviewProps {
  readonly summary: DashboardSummary
  readonly periodLabel: string
}

/**
 * Hierarquia da primeira dobra: resultado como número-âncora e quatro
 * indicadores secundários. Nenhum valor é recalculado no frontend.
 */
export function DashboardOverview({ summary, periodLabel }: DashboardOverviewProps) {
  const result = Number(summary.periodResult)
  const positive = result > 0
  const negative = result < 0

  return (
    <div className="space-y-4">
      <section
        aria-labelledby="result-heading"
        className="relative overflow-hidden rounded-panel border border-primary/25 bg-primary-soft px-5 py-6 sm:px-7 sm:py-7"
      >
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 id="result-heading" className="text-body-sm font-semibold text-ink-muted">
                Resultado do período
              </h2>
              <Badge variant={positive ? 'income' : negative ? 'expense' : 'neutral'}>
                {positive ? (
                  <ArrowUpRight aria-hidden="true" className="size-3" />
                ) : negative ? (
                  <ArrowDownRight aria-hidden="true" className="size-3" />
                ) : (
                  <Scale aria-hidden="true" className="size-3" />
                )}
                {positive ? 'Positivo' : negative ? 'Negativo' : 'Equilibrado'}
              </Badge>
            </div>
            <p
              className={cn(
                'numeric mt-2 text-metric-xl font-bold tracking-tight',
                positive ? 'text-income' : negative ? 'text-expense' : 'text-ink',
              )}
            >
              {formatMoney(summary.periodResult, 'BRL')}
            </p>
            <p className="mt-1 text-body-sm text-ink-muted">
              Receitas menos despesas · {periodLabel} · BRL
            </p>
          </div>
        </div>
        <div aria-hidden="true" className="period-thread mt-6" />
      </section>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          icon={TrendingUp}
          label="Receitas"
          value={formatMoney(summary.income, 'BRL')}
          note="Entradas efetivadas ou reconciliadas."
          tone="income"
        />
        <MetricCard
          icon={TrendingDown}
          label="Despesas"
          value={formatMoney(summary.expenses, 'BRL')}
          note="O total que as categorias detalham abaixo."
          tone="expense"
        />
        <MetricCard
          icon={Wallet}
          label="Saldo em contas"
          value={formatMoney(summary.currentBalance, 'BRL')}
          note={<BalanceReference referenceAt={summary.currentBalanceReferenceAt} />}
          tone="neutral"
        />
        <MetricCard
          icon={CreditCard}
          label="Saldo devedor de cartões"
          value={formatMoney(summary.cardOutstandingBalance, 'BRL')}
          note={
            <span>
              Dívida separada do saldo.{' '}
              <Link
                to="/cards"
                className="font-semibold text-action underline underline-offset-4 hover:text-action-hover"
              >
                Acompanhar cartões
              </Link>
            </span>
          }
          tone="expense"
        />
      </div>
    </div>
  )
}

function MetricCard({
  icon: Icon,
  label,
  value,
  note,
  tone,
}: {
  readonly icon: LucideIcon
  readonly label: string
  readonly value: string
  readonly note: ReactNode
  readonly tone: 'neutral' | 'income' | 'expense'
}) {
  return (
    <article className="rounded-card border border-line bg-surface p-4 shadow-[var(--shadow-card)]">
      <div className="flex items-center gap-2">
        <span
          aria-hidden="true"
          className={cn(
            'flex size-8 items-center justify-center rounded-control',
            tone === 'income'
              ? 'bg-income-soft text-income'
              : tone === 'expense'
                ? 'bg-expense-soft text-expense'
                : 'bg-surface-muted text-ink-muted',
          )}
        >
          <Icon className="size-4" />
        </span>
        <h3 className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
          {label}
        </h3>
      </div>
      <p
        className={cn(
          'numeric mt-3 text-metric-md font-semibold',
          tone === 'income' ? 'text-income' : tone === 'expense' ? 'text-expense' : 'text-ink',
        )}
      >
        {value}
      </p>
      <div className="mt-2 text-caption leading-relaxed text-ink-muted">{note}</div>
    </article>
  )
}
