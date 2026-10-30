import { Link, useParams } from 'react-router'

import { useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { financeApi } from '@/features/integration/api'
import { connectionStatusLabels } from '@/features/integration/syncLabels'

import { formatDateTime, formatMoney } from '@/features/accounts/format'

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

export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
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
  const accountId = account.id

  async function renameAccount(displayName: string) {
    const updated = await financeApi.renameAccount(accountId, displayName)
    queryClient.setQueryData(['account', accountId], updated)
    void queryClient.invalidateQueries()
    return updated
  }

  async function resetAccountName() {
    const updated = await financeApi.resetAccountName(accountId)
    queryClient.setQueryData(['account', accountId], updated)
    void queryClient.invalidateQueries()
    return updated
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <Link to="/accounts" className="text-sm font-medium text-action underline underline-offset-2">
        ← Contas
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 className="text-2xl font-semibold tracking-tight text-ink">
            {account.alias ?? 'Sem apelido'}
          </h1>
          <p className="text-sm text-ink-muted">
            {account.institutionName ?? 'Instituição não informada'}
          </p>
          {account.nameCustomized && account.providerName ? (
            <p className="text-xs text-ink-muted">
              Nome original no Pluggy: {account.providerName}
            </p>
          ) : null}
        </div>
        {account.origin === 'IMPORTED' ? (
          <DisplayNameEditor
            currentName={account.alias ?? null}
            originalName={account.providerName ?? null}
            customized={Boolean(account.nameCustomized)}
            onRename={renameAccount}
            onReset={resetAccountName}
          />
        ) : null}
      </header>

      <section className="rounded-card border border-line bg-surface p-5">
        <dl className="grid gap-3 text-sm sm:grid-cols-[200px_1fr]">
          <dt className="text-ink-muted">Tipo</dt>
          <dd className="text-ink">{typeLabels[account.type] ?? account.type}</dd>
          <dt className="text-ink-muted">Saldo atual</dt>
          <dd className="numeric text-ink">
            {formatMoney(account.currentBalance, account.currency)}
          </dd>
          {account.origin === 'MANUAL' ? (
            <>
              <dt className="text-ink-muted">Saldo de referência</dt>
              <dd className="numeric text-ink">
                {formatMoney(account.referenceBalance, account.currency)}
              </dd>
            </>
          ) : null}
          <dt className="text-ink-muted">Moeda</dt>
          <dd className="text-ink">{account.currency}</dd>
          <dt className="text-ink-muted">Origem</dt>
          <dd className="text-ink">{account.origin === 'IMPORTED' ? 'Importada' : 'Manual'}</dd>
          <dt className="text-ink-muted">Estado</dt>
          <dd className="text-ink">
            {account.status ? (statusLabels[account.status] ?? account.status) : 'Não informado'}
          </dd>
          {account.connectionStatus ? (
            <>
              <dt className="text-ink-muted">Conexão</dt>
              <dd className="text-ink">
                {connectionStatusLabels[account.connectionStatus] ?? account.connectionStatus}
              </dd>
            </>
          ) : null}
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

      <div className="flex flex-wrap gap-3">
        <Link
          to={`/transactions?accountId=${account.id}`}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
        >
          Ver transações desta conta
        </Link>
        {account.type === 'CREDIT' ? (
          <>
            <Link
              to={`/cards/${account.id}/invoices`}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
            >
              Ver faturas
            </Link>
            <Link
              to="/cards"
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
            >
              Painel de cartões
            </Link>
          </>
        ) : null}
        {account.origin === 'MANUAL' ? (
          <Link
            to={`/accounts/${account.id}/edit`}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
          >
            Editar conta manual
          </Link>
        ) : null}
      </div>
    </div>
  )
}
