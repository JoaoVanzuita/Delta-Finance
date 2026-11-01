import { useMemo } from 'react'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftRight, IdCard, Settings2, Wallet } from 'lucide-react'
import { Link, Navigate, useParams, useSearchParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'
import { DisplayNameEditor } from '@/components/DisplayNameEditor'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { invalidateAccountViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi } from '@/features/integration/api'
import { connectionStatusLabels } from '@/features/integration/syncLabels'

const TYPE_LABELS: Record<string, string> = {
  BANK: 'Conta bancária',
  CREDIT: 'Cartão de crédito',
  CASH: 'Dinheiro',
  WALLET: 'Carteira digital',
  UNKNOWN: 'Tipo não informado',
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Ativa',
  INACTIVE: 'Inativa',
  UNAVAILABLE: 'Indisponível',
  CLOSED: 'Encerrada',
}

const SUBTYPE_LABELS: Record<string, string> = {
  CHECKING_ACCOUNT: 'Conta corrente',
  SAVINGS_ACCOUNT: 'Poupança',
  PAYMENT_ACCOUNT: 'Conta de pagamento',
  CREDIT_CARD: 'Cartão de crédito',
  OTHER: 'Outro',
}

type AccountTab = 'overview' | 'transactions' | 'settings'

function parseTab(value: string | null): AccountTab {
  return value === 'transactions' || value === 'settings' ? value : 'overview'
}

export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const tab = parseTab(searchParams.get('tab'))

  const accountQuery = useQuery({
    queryKey: queryKeys.account(id ?? ''),
    queryFn: () => financeApi.account(id ?? ''),
    enabled: Boolean(id),
  })
  const transactionsQuery = useQuery({
    queryKey: queryKeys.transactionFilters({ accountId: id, limit: 20 }),
    queryFn: () => financeApi.transactions({ accountId: id ?? '', limit: 20 }),
    enabled: Boolean(id) && tab === 'transactions',
  })

  const account = accountQuery.data
  const accountId = account?.id ?? id ?? ''

  const rename = useMemo(
    () => async (displayName: string) => {
      const updated = await financeApi.renameAccount(accountId, displayName)
      queryClient.setQueryData(queryKeys.account(accountId), updated)
      await invalidateAccountViews(queryClient)
      return updated
    },
    [accountId, queryClient],
  )

  const resetName = useMemo(
    () => async () => {
      const updated = await financeApi.resetAccountName(accountId)
      queryClient.setQueryData(queryKeys.account(accountId), updated)
      await invalidateAccountViews(queryClient)
      return updated
    },
    [accountId, queryClient],
  )

  function selectTab(next: string) {
    const params = new URLSearchParams(searchParams)
    if (next === 'overview') params.delete('tab')
    else params.set('tab', next)
    setSearchParams(params, { replace: false })
  }

  if (accountQuery.isLoading) {
    return (
      <div className="space-y-4" role="status" aria-live="polite">
        <span className="sr-only">Carregando conta…</span>
        <Skeleton className="h-24 w-full max-w-2xl rounded-panel" />
        <Skeleton className="h-64 rounded-card" />
      </div>
    )
  }

  if (accountQuery.isError || !account) {
    return (
      <Alert variant="danger" title="Conta não encontrada">
        {accountQuery.error instanceof ApiError
          ? accountQuery.error.message
          : 'Não foi possível carregar a conta.'}{' '}
        <Link to="/accounts" className="font-semibold text-action underline underline-offset-4">
          Voltar para contas
        </Link>
      </Alert>
    )
  }

  if (account.type === 'CREDIT') {
    return <Navigate to={`/cards/${account.id}`} replace />
  }

  const transactions = transactionsQuery.data?.items ?? []

  return (
    <div className="space-y-6">
      <Link
        to="/accounts"
        className="inline-flex min-h-11 items-center gap-1 text-body-sm font-medium text-action underline underline-offset-4 hover:text-action-hover"
      >
        ← Contas
      </Link>

      <PageHeader
        overline={TYPE_LABELS[account.type] ?? account.type}
        title={account.alias ?? 'Sem apelido'}
        description={[
          account.institutionName ?? 'Instituição não informada',
          account.origin === 'IMPORTED' ? 'Importada' : 'Manual',
        ].join(' · ')}
        meta={
          <>
            <Badge variant={account.status === 'ACTIVE' ? 'income' : 'warning'}>
              {STATUS_LABELS[account.status ?? ''] ?? 'Não informado'}
            </Badge>
            {account.currency !== 'BRL' ? (
              <Badge variant="warning">Moeda {account.currency}</Badge>
            ) : null}
            {account.nameCustomized && account.providerName ? (
              <span className="text-caption text-ink-muted">
                Nome original: {account.providerName}
              </span>
            ) : null}
          </>
        }
      />

      <Tabs value={tab} onValueChange={selectTab}>
        <TabList label="Seções da conta">
          <Tab value="overview">Visão geral</Tab>
          <Tab value="transactions">Movimentações</Tab>
          <Tab value="settings">Configurações</Tab>
        </TabList>

        <TabPanel value="overview">
          <div className="space-y-4">
            <Card className="p-5 sm:p-6">
              <p className="overline text-ink-muted">Saldo atual</p>
              <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-ink">
                {formatMoney(account.currentBalance, account.currency)}
              </p>
              <p className="mt-2 text-body-sm text-ink-muted">
                {account.origin === 'MANUAL'
                  ? account.balanceReferenceAt
                    ? `Saldo de referência em ${formatDate(account.balanceReferenceAt)}.`
                    : 'Data de referência não informada.'
                  : 'Valor informado pela instituição na última sincronização.'}
              </p>
            </Card>

            <Card className="p-5">
              <h2 className="text-section-title font-semibold text-ink">Dados da conta</h2>
              <dl className="mt-4 grid gap-x-6 gap-y-3 text-body-sm sm:grid-cols-[180px_1fr]">
                <dt className="text-ink-muted">Tipo</dt>
                <dd className="text-ink">{TYPE_LABELS[account.type] ?? account.type}</dd>
                <dt className="text-ink-muted">Moeda</dt>
                <dd className="text-ink">{account.currency}</dd>
                {account.origin === 'MANUAL' ? (
                  <>
                    <dt className="text-ink-muted">Saldo de referência</dt>
                    <dd className="numeric text-ink">
                      {formatMoney(account.referenceBalance, account.currency)}
                    </dd>
                    <dt className="text-ink-muted">Data de referência</dt>
                    <dd className="text-ink">{formatDate(account.balanceReferenceAt)}</dd>
                  </>
                ) : null}
                <dt className="text-ink-muted">Origem</dt>
                <dd className="text-ink">
                  {account.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                </dd>
                <dt className="text-ink-muted">Estado</dt>
                <dd className="text-ink">
                  {STATUS_LABELS[account.status ?? ''] ?? 'Não informado'}
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
                <dd className="text-ink">
                  {account.subtype ? (SUBTYPE_LABELS[account.subtype] ?? 'Outro') : 'Não informado'}
                </dd>
              </dl>
            </Card>

            {account.currency !== 'BRL' ? (
              <Alert variant="warning" title={`Conta em ${account.currency}`}>
                O saldo é exibido na moeda original. Ele não entra nos totais consolidados em BRL.
              </Alert>
            ) : null}
          </div>
        </TabPanel>

        <TabPanel value="transactions">
          <div className="space-y-3">
            <div className="flex flex-wrap items-end justify-between gap-2">
              <div>
                <h2 className="text-section-title font-semibold text-ink">
                  Movimentações recentes
                </h2>
                <p className="text-caption text-ink-muted">
                  Últimas 20 transações da conta, importadas e manuais.
                </p>
              </div>
              <Link
                to={`/transactions?accountId=${account.id}`}
                className="inline-flex min-h-11 items-center gap-2 text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
              >
                <ArrowLeftRight aria-hidden="true" className="size-4" />
                Abrir transações
              </Link>
            </div>

            {transactionsQuery.isLoading ? (
              <p role="status" className="text-body-sm text-ink-muted">
                Carregando movimentações…
              </p>
            ) : null}
            {transactionsQuery.isError ? (
              <Alert variant="danger" title="Movimentações indisponíveis">
                {transactionsQuery.error instanceof ApiError
                  ? transactionsQuery.error.message
                  : 'Não foi possível carregar as movimentações desta conta.'}
              </Alert>
            ) : null}
            {transactionsQuery.isSuccess && transactions.length === 0 ? (
              <p className="rounded-card border border-line bg-surface p-4 text-body-sm text-ink-muted">
                Nenhuma movimentação registrada nesta conta.
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
                <h2 className="text-section-title font-semibold text-ink">Nome e identificação</h2>
              </div>
              {account.origin === 'IMPORTED' ? (
                <div className="mt-4">
                  <p className="text-body-sm text-ink-muted">
                    O nome de exibição é local. Os dados bancários continuam vindo da instituição.
                  </p>
                  <div className="mt-3">
                    <DisplayNameEditor
                      currentName={account.alias ?? null}
                      originalName={account.providerName ?? null}
                      customized={Boolean(account.nameCustomized)}
                      onRename={rename}
                      onReset={resetName}
                    />
                  </div>
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  <p className="text-body-sm text-ink-muted">
                    Conta manual: edite saldo de referência, observações e estado.
                  </p>
                  <Link
                    to={`/accounts/${account.id}/edit`}
                    className={cn(buttonVariants({ variant: 'secondary' }))}
                  >
                    <IdCard aria-hidden="true" className="size-4" />
                    Editar conta manual
                  </Link>
                </div>
              )}
            </Card>

            <Card className="p-5">
              <div className="flex items-center gap-2">
                <Wallet aria-hidden="true" className="size-4 text-ink-muted" />
                <h2 className="text-section-title font-semibold text-ink">Origem dos dados</h2>
              </div>
              <dl className="mt-4 grid gap-x-6 gap-y-2 text-body-sm sm:grid-cols-[180px_1fr]">
                <dt className="text-ink-muted">Origem</dt>
                <dd className="text-ink">
                  {account.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                </dd>
                <dt className="text-ink-muted">Identificador da conta</dt>
                <dd className="break-all text-ink-muted">{account.id}</dd>
                {account.providerName ? (
                  <>
                    <dt className="text-ink-muted">Nome no provedor</dt>
                    <dd className="text-ink">{account.providerName}</dd>
                  </>
                ) : null}
              </dl>
            </Card>
          </div>
        </TabPanel>
      </Tabs>
    </div>
  )
}
