import { Link, useParams } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi } from '@/features/integration/api'

import { formatDateTime, formatMoney } from '@/features/accounts/format'

export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const accountQuery = useQuery({
    queryKey: ['account', id],
    queryFn: () => financeApi.account(id ?? ''),
    enabled: Boolean(id),
  })

  if (accountQuery.isLoading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-ink-muted">
        Carregando conta…
      </p>
    )
  }

  if (accountQuery.isError) {
    return (
      <section
        role="alert"
        className="mx-auto max-w-2xl rounded-card border border-expense/30 bg-expense/5 p-5"
      >
        <h1 className="text-lg font-semibold text-ink">Conta não encontrada</h1>
        <p className="mt-2 text-sm text-ink-muted">
          {accountQuery.error instanceof ApiError
            ? accountQuery.error.message
            : 'Não foi possível carregar a conta.'}
        </p>
        <Link
          to="/accounts"
          className="mt-4 inline-block text-sm font-medium text-action underline"
        >
          Voltar para contas
        </Link>
      </section>
    )
  }

  const account = accountQuery.data
  if (!account) {
    return null
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <Link to="/accounts" className="text-sm font-medium text-action underline underline-offset-2">
        ← Contas e cartões
      </Link>

      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          {account.alias ?? 'Sem apelido'}
        </h1>
        <p className="text-sm text-ink-muted">
          {account.institutionName ?? 'Instituição não informada'}
        </p>
      </header>

      <section className="rounded-card border border-line bg-surface p-5">
        <dl className="grid gap-3 text-sm sm:grid-cols-[200px_1fr]">
          <dt className="text-ink-muted">Tipo</dt>
          <dd className="text-ink">{account.type}</dd>
          <dt className="text-ink-muted">Saldo atual</dt>
          <dd className="numeric text-ink">
            {formatMoney(account.currentBalance, account.currency)}
          </dd>
          <dt className="text-ink-muted">Moeda</dt>
          <dd className="text-ink">{account.currency}</dd>
          <dt className="text-ink-muted">Origem</dt>
          <dd className="text-ink">{account.origin === 'IMPORTED' ? 'Importada' : 'Manual'}</dd>
          <dt className="text-ink-muted">Estado</dt>
          <dd className="text-ink">{account.status}</dd>
          <dt className="text-ink-muted">Última sincronização</dt>
          <dd className="text-ink">{formatDateTime(account.lastSyncAt)}</dd>
          <dt className="text-ink-muted">Subtipo</dt>
          <dd className="text-ink">{account.subtype ?? 'Não informado'}</dd>
        </dl>
      </section>

      {account.cardProfile ? (
        <section className="rounded-card border border-line bg-surface p-5">
          <h2 className="text-lg font-semibold text-ink">Cartão</h2>
          <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-[200px_1fr]">
            <dt className="text-ink-muted">Fechamento</dt>
            <dd className="text-ink">Dia {account.cardProfile.closingDay}</dd>
            <dt className="text-ink-muted">Vencimento</dt>
            <dd className="text-ink">Dia {account.cardProfile.dueDay}</dd>
            <dt className="text-ink-muted">Limite</dt>
            <dd className="numeric text-ink">
              {formatMoney(account.cardProfile.creditLimit, account.currency)}
            </dd>
            <dt className="text-ink-muted">Crédito acumulado</dt>
            <dd className="numeric text-ink">
              {formatMoney(account.cardProfile.creditBalance, account.currency)}
            </dd>
          </dl>
        </section>
      ) : null}

      <Link
        to={`/transactions?accountId=${account.id}`}
        className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
      >
        Ver transações desta conta
      </Link>
    </div>
  )
}
