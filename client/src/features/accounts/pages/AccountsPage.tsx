import { useQuery } from '@tanstack/react-query'
import { ChevronDown, Landmark, Plus, Settings2, Wallet, WalletCards } from 'lucide-react'
import { Link } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button-variants'
import { Card } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatDateTime, formatMoney, addDecimals } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type Account } from '@/features/integration/api'

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

function isEligible(account: Account): boolean {
  return (
    account.type !== 'CREDIT' &&
    account.status === 'ACTIVE' &&
    account.currency === 'BRL' &&
    Boolean(account.currentBalance)
  )
}

function institutionName(account: Account): string {
  return account.institutionName?.trim() || 'Sem instituição informada'
}

/**
 * Contas bancárias, carteiras e dinheiro.
 *
 * O total consolidado considera apenas contas ativas em BRL (BANK/CASH/WALLET).
 * Contas inativas e em outra moeda continuam acessíveis em uma seção secundária.
 */
export function AccountsPage() {
  const accountsQuery = useQuery({ queryKey: queryKeys.accounts, queryFn: financeApi.accounts })
  const allAccounts = accountsQuery.data?.items ?? []
  const accounts = allAccounts.filter((account) => account.type !== 'CREDIT')
  const eligible = accounts.filter(isEligible)
  const otherAccounts = accounts.filter((account) => !isEligible(account))
  const total = addDecimals(
    eligible
      .map((account) => account.currentBalance)
      .filter((value): value is string => Boolean(value)),
  )

  const groups = new Map<string, Account[]>()
  for (const account of eligible) {
    const key = institutionName(account)
    const current = groups.get(key) ?? []
    current.push(account)
    groups.set(key, current)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Meu dinheiro"
        title="Contas"
        description="Saldos bancários, carteiras e dinheiro. Cartões de crédito ficam no painel de cartões."
        actions={
          <>
            <Link to="/accounts/new" className={cn(buttonVariants({ variant: 'primary' }))}>
              <Plus aria-hidden="true" className="size-4" />
              Nova conta manual
            </Link>
            <Link to="/cards" className={cn(buttonVariants({ variant: 'secondary' }))}>
              <WalletCards aria-hidden="true" className="size-4" />
              Ver cartões
            </Link>
          </>
        }
      />

      {accountsQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando contas…</span>
          <Skeleton className="h-36 rounded-panel" />
          <div className="grid gap-4 sm:grid-cols-2">
            <Skeleton className="h-52 rounded-card" />
            <Skeleton className="h-52 rounded-card" />
          </div>
        </div>
      ) : null}

      {accountsQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as contas">
          {accountsQuery.error instanceof ApiError
            ? accountsQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {accountsQuery.isSuccess && accounts.length === 0 ? (
        <EmptyState
          icon={<Wallet className="size-5" />}
          title="Nenhuma conta bancária"
          description="Adicione uma conta manual ou configure a integração para importar contas bancárias. Cartões têm um painel próprio."
          action={
            <div className="flex flex-wrap gap-2">
              <Link to="/accounts/new" className={cn(buttonVariants({ variant: 'primary' }))}>
                Criar conta manual
              </Link>
              <Link
                to="/settings/integration"
                className={cn(buttonVariants({ variant: 'secondary' }))}
              >
                Configurar integração
              </Link>
            </div>
          }
        />
      ) : null}

      {accounts.length > 0 ? (
        <>
          <Card variant="primary" className="p-5 sm:p-6">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="overline text-ink-muted">Saldo em contas</p>
                <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-ink">
                  {eligible.length === 0 ? 'Sem conta elegível' : formatMoney(total, 'BRL')}
                </p>
                <p className="mt-1 text-body-sm text-ink-muted">
                  {eligible.length} conta(s) ativa(s) em BRL · bancos, carteiras e dinheiro.
                </p>
              </div>
              <Link
                to="/settings/integration"
                className="inline-flex min-h-11 items-center gap-2 rounded-control px-2 text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
              >
                <Settings2 aria-hidden="true" className="size-4" />
                Gerenciar conexões
              </Link>
            </div>
            {otherAccounts.some((account) => account.currency !== 'BRL') ? (
              <p className="mt-4 rounded-control border border-warning/35 bg-warning-soft p-3 text-caption text-ink">
                Contas em outras moedas aparecem abaixo na moeda original e não entram no total em
                BRL.
              </p>
            ) : null}
          </Card>

          {groups.size > 0 ? (
            <section aria-labelledby="active-accounts-heading" className="space-y-4">
              <div>
                <h2
                  id="active-accounts-heading"
                  className="text-section-title font-semibold text-ink"
                >
                  Contas em uso
                </h2>
                <p className="text-caption text-ink-muted">
                  Agrupadas por instituição. Ordem alfabética.
                </p>
              </div>
              <div className="space-y-6">
                {Array.from(groups.entries())
                  .sort(([left], [right]) => left.localeCompare(right, 'pt-BR'))
                  .map(([institution, institutionAccounts]) => (
                    <div key={institution} className="space-y-3">
                      <h3 className="flex items-center gap-2 text-body-sm font-semibold text-ink">
                        <Landmark aria-hidden="true" className="size-4 text-ink-muted" />
                        {institution}
                      </h3>
                      <ul className="grid gap-4 sm:grid-cols-2">
                        {institutionAccounts.map((account) => (
                          <li key={account.id}>
                            <AccountCard account={account} />
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
              </div>
            </section>
          ) : null}

          {otherAccounts.length > 0 ? (
            <details className="group rounded-card border border-line bg-surface">
              <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-body-sm font-semibold text-ink marker:content-none">
                <span className="inline-flex items-center gap-2">
                  Outras contas
                  <Badge variant="neutral">{otherAccounts.length}</Badge>
                </span>
                <ChevronDown
                  aria-hidden="true"
                  className="size-4 text-ink-muted transition-transform group-open:rotate-180"
                />
              </summary>
              <div className="border-t border-line p-4">
                <p className="mb-3 text-caption text-ink-muted">
                  Inativas, indisponíveis ou em moeda diferente de BRL. Continuam acessíveis sem
                  competir com o saldo principal.
                </p>
                <ul className="grid gap-3 sm:grid-cols-2">
                  {otherAccounts.map((account) => (
                    <li key={account.id}>
                      <AccountCard account={account} muted />
                    </li>
                  ))}
                </ul>
              </div>
            </details>
          ) : null}
        </>
      ) : null}
    </div>
  )
}

function AccountCard({ account, muted = false }: { account: Account; muted?: boolean }) {
  const inactive = account.status !== 'ACTIVE'
  const foreign = account.currency !== 'BRL'

  return (
    <article
      className={cn(
        'flex h-full flex-col gap-4 rounded-card border p-4',
        muted
          ? 'border-line bg-surface-muted/60'
          : 'border-line bg-surface shadow-[var(--shadow-card)]',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h4 className="truncate text-body-sm font-semibold text-ink">
            {account.alias ?? 'Sem apelido'}
          </h4>
          <p className="mt-0.5 truncate text-caption text-ink-muted">
            {TYPE_LABELS[account.type] ?? account.type}
            {account.origin === 'IMPORTED' ? ' · Importada' : ' · Manual'}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap justify-end gap-1">
          <Badge variant={inactive ? 'warning' : 'income'}>
            {STATUS_LABELS[account.status ?? ''] ?? '—'}
          </Badge>
          {foreign ? <Badge variant="warning">Moeda {account.currency}</Badge> : null}
        </div>
      </div>

      <div>
        <p className="text-caption text-ink-muted">Saldo atual</p>
        <p className="numeric mt-1 text-metric-md font-semibold text-ink">
          {formatMoney(account.currentBalance, account.currency)}
        </p>
        {account.origin === 'MANUAL' && account.balanceReferenceAt ? (
          <p className="mt-1 text-caption text-ink-muted">
            Referência em {formatDate(account.balanceReferenceAt)}
          </p>
        ) : null}
      </div>

      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 text-caption">
        <dt className="text-ink-muted">
          {account.origin === 'MANUAL' ? 'Atualização' : 'Última sincronização'}
        </dt>
        <dd className="text-right text-ink-muted">
          {account.origin === 'MANUAL' ? 'Manual' : formatDateTime(account.lastSyncAt)}
        </dd>
      </dl>

      <div className="mt-auto flex flex-wrap gap-2">
        <Link
          to={`/accounts/${account.id}`}
          className={cn(buttonVariants({ variant: 'secondary', size: 'sm' }))}
        >
          Ver detalhes
        </Link>
        <Link
          to={`/transactions?accountId=${account.id}`}
          className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }))}
        >
          Transações
        </Link>
      </div>
    </article>
  )
}
