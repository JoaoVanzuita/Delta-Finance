import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatMoney } from '@/features/accounts/format'
import { financeApi, type Transaction } from '@/features/integration/api'

/** Sugestões de transferência/pagamento de fatura aguardando decisão explícita. */
export function ReconciliationPage() {
  const queryClient = useQueryClient()
  const matchesQuery = useQuery({
    queryKey: ['internalMatches', 'PENDING'],
    queryFn: () => financeApi.internalMatches('PENDING'),
  })
  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'reconciliation'],
    queryFn: () => financeApi.transactions({ limit: 100 }),
  })
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['internalMatches'] })
    void queryClient.invalidateQueries({ queryKey: ['transactions'] })
  }

  const confirm = useMutation({
    mutationFn: (id: string) => financeApi.confirmMatch(id),
    onSuccess: invalidate,
  })
  const reject = useMutation({
    mutationFn: (id: string) => financeApi.rejectMatch(id),
    onSuccess: invalidate,
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
    <div className="mx-auto w-full max-w-4xl space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Reconciliação
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Movimentos internos</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Transferências entre contas próprias e pagamentos de fatura não são receitas nem despesas.
          O sistema sugere correspondências; somente a sua confirmação as exclui dos totais.
        </p>
      </header>

      {matchesQuery.isLoading ? (
        <p role="status" className="text-sm text-ink-muted">
          Carregando sugestões…
        </p>
      ) : null}

      {matchesQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {matchesQuery.error instanceof ApiError
            ? matchesQuery.error.message
            : 'Não foi possível carregar as sugestões.'}
        </p>
      ) : null}

      {mutationError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {mutationError instanceof ApiError
            ? mutationError.message
            : 'Não foi possível registrar a decisão.'}
        </p>
      ) : null}

      {matchesQuery.isSuccess && matches.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-6 text-sm text-ink-muted">
          Nenhuma sugestão pendente. Transferências e pagamentos já confirmados continuam fora dos
          totais.
        </p>
      ) : null}

      {matches.length > 0 ? (
        <ul className="space-y-4">
          {matches.map((match) => {
            const debit = transactions.get(match.debitTransactionId)
            const credit = transactions.get(match.creditTransactionId)
            return (
              <li
                key={match.id}
                className="space-y-3 rounded-card border border-line bg-surface p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-ink">
                      {match.kind === 'CARD_PAYMENT'
                        ? 'Pagamento de fatura'
                        : 'Transferência entre contas'}
                    </p>
                    <p className="text-xs text-ink-muted">
                      Confiança {Number(match.score).toFixed(0)} · critérios {match.criteriaVersion}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => confirm.mutate(match.id)}
                      className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-3 text-sm font-semibold text-surface disabled:opacity-60"
                    >
                      Confirmar
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => reject.mutate(match.id)}
                      className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
                    >
                      Rejeitar
                    </button>
                  </div>
                </div>

                <div className="grid gap-2 sm:grid-cols-2">
                  <TransactionSide label="Saída" transaction={debit} accounts={accountNames} />
                  <TransactionSide label="Entrada" transaction={credit} accounts={accountNames} />
                </div>
                {match.reason ? <p className="text-xs text-ink-muted">{match.reason}</p> : null}
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
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
    <div className="rounded-card border border-line bg-surface-muted p-3">
      <p className="text-xs font-medium text-ink-muted">{label}</p>
      {transaction ? (
        <>
          <p className="mt-1 truncate text-sm font-medium text-ink">
            {transaction.description ?? 'Sem descrição'}
          </p>
          <p className="mt-1 text-xs text-ink-muted">
            {transaction.postedOn} · {accounts.get(transaction.accountId) ?? 'Conta'}
          </p>
          <p
            className={[
              'numeric mt-1 text-sm font-semibold',
              Number(transaction.amount) < 0 ? 'text-expense' : 'text-income',
            ].join(' ')}
          >
            {formatMoney(transaction.amount, transaction.currency)}
          </p>
        </>
      ) : (
        <p className="mt-1 text-sm text-ink-muted">Transação não disponível.</p>
      )}
    </div>
  )
}
