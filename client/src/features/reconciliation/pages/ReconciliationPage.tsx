import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Check, GitCompareArrows, X } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { EmptyState } from '@/components/ui/empty-state'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate, formatMoney, subtractDecimals } from '@/lib/format'
import { invalidateReconciliationViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'
import { ApiError } from '@/lib/api/problem'

import { financeApi, type InternalMatch, type Transaction } from '@/features/integration/api'

function absoluteAmount(value: string): string {
  return value.startsWith('-') ? value.slice(1) : value
}

/**
 * Reconciliação de movimentos internos.
 *
 * Mostra as duas pernas, conta, data, valor e diferença. Confirmação só é
 * liberada quando ambas as transações estão disponíveis e na mesma moeda.
 * Score e versão de critérios ficam em detalhes técnicos recolhidos.
 */
export function ReconciliationPage() {
  const queryClient = useQueryClient()
  const matchesQuery = useQuery({
    queryKey: [...queryKeys.internalMatches, 'PENDING'],
    queryFn: () => financeApi.internalMatches('PENDING'),
  })
  const transactionsQuery = useQuery({
    queryKey: [...queryKeys.transactions, 'reconciliation'],
    queryFn: () => financeApi.transactions({ limit: 100 }),
  })
  const accountsQuery = useQuery({ queryKey: queryKeys.accounts, queryFn: financeApi.accounts })

  const confirm = useMutation({
    mutationFn: (id: string) => financeApi.confirmMatch(id),
    onSuccess: () => invalidateReconciliationViews(queryClient),
  })
  const reject = useMutation({
    mutationFn: (id: string) => financeApi.rejectMatch(id),
    onSuccess: () => invalidateReconciliationViews(queryClient),
  })

  const transactions = new Map(
    (transactionsQuery.data?.items ?? []).map((transaction) => [transaction.id, transaction]),
  )
  const accountNames = new Map(
    (accountsQuery.data?.items ?? []).map((account) => [
      account.id,
      account.alias ?? account.institutionName ?? 'Conta',
    ]),
  )

  const matches = matchesQuery.data?.items ?? []
  const busy = confirm.isPending || reject.isPending
  const mutationError = confirm.error ?? reject.error

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Organizar"
        title="Reconciliação"
        description="Transferências entre contas próprias e pagamentos de fatura não são receitas nem despesas. Confirme apenas quando as duas pernas corresponderem."
      />

      {matchesQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <span className="sr-only">Carregando sugestões…</span>
          <Skeleton className="h-52 rounded-card" />
          <Skeleton className="h-52 rounded-card" />
        </div>
      ) : null}

      {matchesQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as sugestões">
          {matchesQuery.error instanceof ApiError
            ? matchesQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {mutationError ? (
        <Alert variant="danger" title="Não foi possível registrar a decisão">
          {mutationError instanceof ApiError
            ? mutationError.message
            : 'Tente novamente sem sair da página.'}
        </Alert>
      ) : null}

      {matchesQuery.isSuccess && matches.length === 0 ? (
        <EmptyState
          icon={<GitCompareArrows className="size-5" />}
          title="Nenhuma sugestão pendente"
          description="Transferências e pagamentos já confirmados continuam fora dos totais. Novas sugestões aparecem após a sincronização."
        />
      ) : null}

      <ul className="space-y-4">
        {matches.map((match) => {
          const debit = transactions.get(match.debitTransactionId)
          const credit = transactions.get(match.creditTransactionId)
          return (
            <li key={match.id}>
              <MatchCard
                match={match}
                debit={debit}
                credit={credit}
                accounts={accountNames}
                busy={busy}
                onConfirm={() => confirm.mutate(match.id)}
                onReject={() => reject.mutate(match.id)}
              />
            </li>
          )
        })}
      </ul>
    </div>
  )
}

function MatchCard({
  match,
  debit,
  credit,
  accounts,
  busy,
  onConfirm,
  onReject,
}: {
  readonly match: InternalMatch
  readonly debit: Transaction | undefined
  readonly credit: Transaction | undefined
  readonly accounts: ReadonlyMap<string, string>
  readonly busy: boolean
  readonly onConfirm: () => void
  readonly onReject: () => void
}) {
  const currency = debit?.currency ?? credit?.currency
  const sameCurrency = Boolean(debit && credit && debit.currency === credit.currency)
  const canConfirm = Boolean(debit && credit && sameCurrency)
  const difference =
    debit && credit && sameCurrency
      ? subtractDecimals(absoluteAmount(debit.amount), absoluteAmount(credit.amount))
      : undefined
  const hasDifference = difference !== undefined && Number(difference) !== 0

  return (
    <Card className="space-y-4 p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-section-title font-semibold text-ink">
            {match.kind === 'CARD_PAYMENT' ? 'Pagamento de fatura' : 'Transferência entre contas'}
          </h2>
          <p className="mt-1 text-caption text-ink-muted">
            Sugestão automática. Nada é alterado antes da sua decisão.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            onClick={onConfirm}
            disabled={busy || !canConfirm}
            leadingIcon={<Check aria-hidden="true" className="size-4" />}
          >
            Confirmar correspondência
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={onReject}
            disabled={busy}
            leadingIcon={<X aria-hidden="true" className="size-4" />}
          >
            Rejeitar
          </Button>
        </div>
      </div>

      {!canConfirm ? (
        <Alert variant="warning" title="Dados insuficientes para confirmar">
          {!debit || !credit
            ? 'Uma das transações não está disponível nos registros carregados.'
            : 'As transações estão em moedas diferentes.'}{' '}
          Rejeite ou atualize os dados antes de confirmar.
        </Alert>
      ) : null}

      <div className="grid items-stretch gap-2 md:grid-cols-[1fr_auto_1fr]">
        <TransactionSide label="Saída" transaction={debit} accounts={accounts} />
        <div className="flex items-center justify-center text-ink-muted" aria-hidden="true">
          <ArrowRight className="size-5 rotate-90 md:rotate-0" />
        </div>
        <TransactionSide label="Entrada" transaction={credit} accounts={accounts} />
      </div>

      {debit && credit ? (
        <div
          className={cn(
            'flex flex-wrap items-center justify-between gap-2 rounded-control border p-3 text-body-sm',
            hasDifference ? 'border-warning/40 bg-warning-soft' : 'border-income/35 bg-income-soft',
          )}
        >
          <span className="font-medium text-ink">
            Diferença entre as pernas {currency ? `(${currency})` : ''}
          </span>
          <span className="numeric font-semibold text-ink">
            {sameCurrency && difference !== undefined
              ? formatMoney(difference, currency ?? 'BRL')
              : 'Moedas diferentes'}
          </span>
        </div>
      ) : null}

      <details className="rounded-control border border-line">
        <summary className="min-h-11 cursor-pointer px-3 py-2 text-body-sm font-medium text-ink">
          Detalhes técnicos da sugestão
        </summary>
        <dl className="space-y-1 border-t border-line px-3 py-2 text-caption text-ink-muted">
          <div className="flex justify-between gap-2">
            <dt>Confiança</dt>
            <dd className="numeric">{Number(match.score).toFixed(0)}%</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt>Critérios</dt>
            <dd>{match.criteriaVersion}</dd>
          </div>
          {match.reason ? (
            <div>
              <dt>Motivo</dt>
              <dd>{match.reason}</dd>
            </div>
          ) : null}
        </dl>
      </details>
    </Card>
  )
}

function TransactionSide({
  label,
  transaction,
  accounts,
}: {
  readonly label: string
  readonly transaction: Transaction | undefined
  readonly accounts: ReadonlyMap<string, string>
}) {
  return (
    <div className="rounded-control border border-line bg-surface-muted p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
          {label}
        </p>
        {transaction ? (
          <Badge variant={transaction.status === 'PENDING' ? 'warning' : 'neutral'}>
            {transaction.status === 'PENDING' ? 'Pendente' : 'Efetivada'}
          </Badge>
        ) : null}
      </div>
      {transaction ? (
        <>
          <p className="mt-2 truncate text-body-sm font-medium text-ink">
            {transaction.description || 'Sem descrição'}
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            {formatDate(transaction.postedOn)} ·{' '}
            {accounts.get(transaction.accountId) ?? 'Conta não identificada'}
          </p>
          <p
            className={cn(
              'numeric mt-2 text-metric-sm font-semibold',
              Number(transaction.amount) < 0 ? 'text-expense' : 'text-income',
            )}
          >
            {formatMoney(transaction.amount, transaction.currency)}
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            {transaction.source === 'IMPORTED' ? 'Importada' : 'Manual'}
          </p>
        </>
      ) : (
        <p className="mt-2 text-body-sm text-ink-muted">Transação não disponível.</p>
      )}
    </div>
  )
}
