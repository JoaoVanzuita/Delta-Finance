import { Link } from 'react-router'

import { useQuery } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Account } from '@/features/integration/api'

import { formatDateTime, formatMoney } from '../format'

const typeLabels: Record<string, string> = {
  BANK: 'Conta bancária',
  CREDIT: 'Cartão de crédito',
  CASH: 'Dinheiro',
  WALLET: 'Carteira digital',
  UNKNOWN: 'Tipo não informado',
}

const statusLabels: Record<string, string> = {
  ACTIVE: 'Ativa',
  INACTIVE: 'Inativa',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrada',
}

export function AccountsPage() {
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const allAccounts = accountsQuery.data?.items ?? []
  const accounts = allAccounts.filter((account) => account.type !== 'CREDIT')
  const hasCards = allAccounts.some((account) => account.type === 'CREDIT')

  return (
    <div className="mx-auto w-full max-w-5xl space-y-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
            Patrimônio do dia a dia
          </p>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">Contas</h1>
          <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
            Contas bancárias, carteiras e dinheiro. Cartões de crédito têm um painel próprio, com
            faturas, limites e vencimentos. Campos não fornecidos pela origem são sinalizados.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to="/accounts/new"
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
          >
            Nova conta manual
          </Link>
          <Link
            to="/cards"
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Ver cartões
          </Link>
          <Link
            to="/settings/integration"
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Gerenciar conexões
          </Link>
        </div>
      </header>

      {accountsQuery.isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando contas…
        </p>
      ) : null}

      {accountsQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {accountsQuery.error instanceof ApiError
            ? accountsQuery.error.message
            : 'Não foi possível carregar as contas.'}
        </p>
      ) : null}

      {accountsQuery.data && accounts.length === 0 ? (
        <section className="rounded-card border border-line bg-surface p-6">
          <h2 className="text-lg font-semibold text-ink">Nenhuma conta bancária</h2>
          <p className="mt-2 text-sm text-ink-muted">
            Configure sua integração pessoal e adicione um itemId para importar contas. Cartões de
            crédito aparecem em uma área separada.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link
              to="/settings/integration"
              className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
            >
              Configurar Meu Pluggy
            </Link>
            {hasCards ? (
              <Link
                to="/cards"
                className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
              >
                Ver cartões
              </Link>
            ) : null}
          </div>
        </section>
      ) : null}

      {accounts.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2">
          {accounts.map((account) => (
            <li key={account.id}>
              <AccountCard account={account} />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function AccountCard({ account }: { account: Account }) {
  return (
    <article className="flex h-full flex-col gap-4 rounded-card border border-line bg-surface p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate text-base font-semibold text-ink">
            {account.alias ?? 'Sem apelido'}
          </h2>
          <p className="mt-1 text-sm text-ink-muted">
            {account.institutionName ?? 'Instituição não informada'}
          </p>
        </div>
        <span className="shrink-0 rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink">
          {typeLabels[account.type] ?? account.type}
        </span>
      </div>

      <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
        <dt className="text-ink-muted">Saldo atual</dt>
        <dd className="numeric text-right font-medium text-ink">
          {formatMoney(account.currentBalance, account.currency)}
        </dd>
        <dt className="text-ink-muted">Origem</dt>
        <dd className="text-right text-ink">
          {account.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
        </dd>
        <dt className="text-ink-muted">Estado</dt>
        <dd className="text-right text-ink">
          {statusLabels[account.status ?? ''] ?? account.status ?? 'Não informado'}
        </dd>
        <dt className="text-ink-muted">Última sincronização</dt>
        <dd className="text-right text-ink">{formatDateTime(account.lastSyncAt)}</dd>
      </dl>

      {account.currency !== 'BRL' ? (
        <p className="rounded-card border border-warning/40 bg-warning/5 p-3 text-xs text-ink">
          Moeda {account.currency}: valores não entram nos totais consolidados em BRL.
        </p>
      ) : null}

      <div className="mt-auto flex flex-wrap gap-3">
        <Link
          to={`/accounts/${account.id}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Ver detalhes
        </Link>
        <Link
          to={`/transactions?accountId=${account.id}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Transações
        </Link>
      </div>
    </article>
  )
}
