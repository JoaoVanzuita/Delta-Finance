import { Link, useParams } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi } from '@/features/integration/api'

import { CardCycleSettings } from '../components/CardCycleSettings'

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Aberta',
  CLOSED: 'Fechada',
  PARTIALLY_PAID: 'Parcialmente paga',
  PAID: 'Paga',
  OVERDUE: 'Vencida',
}

export function InvoicesPage() {
  const { accountId = '' } = useParams<{ accountId: string }>()
  const accountQuery = useQuery({
    queryKey: ['account', accountId],
    queryFn: () => financeApi.account(accountId),
    enabled: Boolean(accountId),
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
  const invoices = invoicesQuery.data?.items ?? []
  const unallocated = (transactionsQuery.data?.items ?? []).filter(
    (transaction) =>
      !transaction.invoiceId &&
      (transaction.status === 'POSTED' || transaction.status === 'RECONCILED'),
  )

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <Link to="/accounts" className="text-sm font-medium text-action underline underline-offset-2">
        ← Contas e cartões
      </Link>

      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">Cartões</p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          Faturas · {account?.alias ?? 'Cartão'}
        </h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Faturas organizadas por ciclo local. Compras no cartão contam como despesa uma vez;
          pagamentos apenas reduzem o saldo devedor.
        </p>
      </header>

      {accountQuery.isError || invoicesQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {(accountQuery.error ?? invoicesQuery.error) instanceof ApiError
            ? ((accountQuery.error ?? invoicesQuery.error) as ApiError).message
            : 'Não foi possível carregar as faturas.'}
        </p>
      ) : null}

      <CardCycleSettings accountId={accountId} profile={account?.cardProfile} />

      {invoicesQuery.isLoading ? (
        <p role="status" className="text-sm text-ink-muted">
          Carregando faturas…
        </p>
      ) : null}

      {invoicesQuery.isSuccess && invoices.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-6 text-sm text-ink-muted">
          {account?.cardProfile
            ? 'Nenhuma fatura gerada ainda. Compras efetivadas aparecem aqui automaticamente.'
            : 'Configure o ciclo do cartão para gerar faturas.'}
        </p>
      ) : null}

      {invoices.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2">
          {invoices.map((invoice) => (
            <li key={invoice.id}>
              <Link
                to={`/invoices/${invoice.id}`}
                className="block h-full rounded-card border border-line bg-surface p-4 transition-colors hover:bg-surface-muted"
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
                  <span className="rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink">
                    {STATUS_LABELS[invoice.status] ?? invoice.status}
                  </span>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <dt className="text-ink-muted">Total</dt>
                  <dd className="numeric text-right font-medium text-ink">
                    {formatMoney(invoice.total, 'BRL')}
                  </dd>
                  <dt className="text-ink-muted">Saldo devedor</dt>
                  <dd className="numeric text-right font-medium text-ink">
                    {formatMoney(invoice.outstanding, 'BRL')}
                  </dd>
                </dl>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}

      {unallocated.length > 0 ? (
        <section className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-4">
          <h2 className="text-sm font-semibold text-ink">Transações não alocadas</h2>
          <p className="text-xs text-ink-muted">
            {account?.cardProfile
              ? 'Estas compras ainda não pertencem a uma fatura gerada.'
              : 'Sem ciclo configurado, as compras permanecem não alocadas.'}
          </p>
          <ul className="space-y-1 text-sm">
            {unallocated.map((transaction) => (
              <li key={transaction.id} className="flex justify-between gap-2">
                <span className="truncate text-ink">
                  {transaction.postedOn} · {transaction.description ?? 'Sem descrição'}
                </span>
                <span className="numeric text-ink">{formatMoney(transaction.amount, 'BRL')}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
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
