import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type ManualTransactionInput } from '@/features/integration/api'

const TYPE_OPTIONS = [
  { value: 'CREDIT', label: 'Receita' },
  { value: 'DEBIT', label: 'Despesa' },
  { value: 'TRANSFER', label: 'Transferência entre contas' },
  { value: 'REFUND', label: 'Reembolso' },
  { value: 'CHARGEBACK', label: 'Chargeback' },
  { value: 'ADJUSTMENT', label: 'Ajuste' },
] as const

type ManualType = (typeof TYPE_OPTIONS)[number]['value']

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

interface ManualEntryFormProps {
  readonly initialType?: ManualType
  readonly initialAccountId?: string
  readonly onCreated?: () => void
}

/**
 * Formulário de lançamento manual.
 *
 * Receitas/despesas, transferência em duas pernas, reembolso/chargeback vinculado e ajuste com
 * motivo explícito. Contas inativas permanecem visíveis, mas não podem ser selecionadas.
 */
export function ManualEntryForm({
  initialType = 'DEBIT',
  initialAccountId,
  onCreated,
}: ManualEntryFormProps) {
  const queryClient = useQueryClient()
  const [type, setType] = useState<ManualType>(initialType)
  const [accountId, setAccountId] = useState(initialAccountId ?? '')
  const [transferAccountId, setTransferAccountId] = useState('')
  const [relatedTransactionId, setRelatedTransactionId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [amount, setAmount] = useState('')
  const [postedOn, setPostedOn] = useState(today())
  const [description, setDescription] = useState('')
  const [adjustmentReason, setAdjustmentReason] = useState('')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: financeApi.categories })
  const transactionsQuery = useQuery({
    queryKey: ['transactions', 'manual-origin'],
    queryFn: () => financeApi.transactions({ limit: 100, type: 'DEBIT', status: 'POSTED' }),
  })

  const accounts = accountsQuery.data?.items ?? []
  const categories = (categoriesQuery.data?.items ?? []).filter((category) => category.active)
  const originCandidates = useMemo(
    () => (transactionsQuery.data?.items ?? []).filter((item) => item.source !== undefined),
    [transactionsQuery.data],
  )

  const create = useMutation({
    mutationFn: (input: ManualTransactionInput) => financeApi.createTransaction(input),
    onSuccess: () => {
      setMessage('Lançamento registrado.')
      setError(null)
      setAmount('')
      setDescription('')
      setNotes('')
      setAdjustmentReason('')
      setRelatedTransactionId('')
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['internalMatches'] })
      onCreated?.()
    },
    onError: (caught) => {
      setMessage(null)
      setError(
        caught instanceof ApiError ? caught.message : 'Não foi possível registrar o lançamento.',
      )
    },
  })

  const isTransfer = type === 'TRANSFER'
  const needsOriginal = type === 'REFUND' || type === 'CHARGEBACK'
  const isAdjustment = type === 'ADJUSTMENT'

  function submit() {
    setError(null)
    setMessage(null)
    if (!accountId) {
      setError('Selecione a conta.')
      return
    }
    if (!description.trim()) {
      setError('Informe a descrição.')
      return
    }
    const parsed = Number(amount.replace(',', '.'))
    if (!amount.trim() || Number.isNaN(parsed) || parsed === 0) {
      setError('Informe um valor diferente de zero.')
      return
    }
    if (isTransfer && (!transferAccountId || transferAccountId === accountId)) {
      setError('Selecione uma conta de destino diferente da origem.')
      return
    }
    if (isAdjustment && !adjustmentReason.trim()) {
      setError('Informe o motivo do ajuste.')
      return
    }
    if (!postedOn) {
      setError('Informe a data.')
      return
    }

    create.mutate({
      accountId,
      type,
      postedOn,
      amount: amount.replace(',', '.'),
      description: description.trim(),
      categoryId: categoryId || undefined,
      transferAccountId: isTransfer ? transferAccountId : undefined,
      relatedTransactionId:
        needsOriginal && relatedTransactionId ? relatedTransactionId : undefined,
      adjustmentReason: isAdjustment ? adjustmentReason.trim() : undefined,
      notes: notes.trim() || undefined,
    })
  }

  return (
    <form
      noValidate
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {message ? (
        <p
          role="status"
          className="rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
        >
          {message}
        </p>
      ) : null}
      {error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
        >
          {error}
        </p>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="font-medium text-ink">Tipo</span>
          <select
            value={type}
            onChange={(event) => setType(event.target.value as ManualType)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            {TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Conta</span>
          <select
            value={accountId}
            onChange={(event) => setAccountId(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            <option value="">Selecione</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id} disabled={account.status !== 'ACTIVE'}>
                {account.alias ?? account.institutionName ?? account.id}
                {account.status !== 'ACTIVE' ? ' (inativa)' : ''}
              </option>
            ))}
          </select>
        </label>

        {isTransfer ? (
          <label className="block text-sm">
            <span className="font-medium text-ink">Conta de destino</span>
            <select
              value={transferAccountId}
              onChange={(event) => setTransferAccountId(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Selecione</option>
              {accounts
                .filter((account) => account.id !== accountId)
                .map((account) => (
                  <option
                    key={account.id}
                    value={account.id}
                    disabled={account.status !== 'ACTIVE'}
                  >
                    {account.alias ?? account.institutionName ?? account.id}
                    {account.status !== 'ACTIVE' ? ' (inativa)' : ''}
                  </option>
                ))}
            </select>
          </label>
        ) : null}

        {needsOriginal ? (
          <label className="block text-sm">
            <span className="font-medium text-ink">Transação original</span>
            <select
              value={relatedTransactionId}
              onChange={(event) => setRelatedTransactionId(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Correspondência automática</option>
              {originCandidates.map((transaction) => (
                <option key={transaction.id} value={transaction.id}>
                  {transaction.description ?? transaction.id} · {transaction.postedOn}
                </option>
              ))}
            </select>
            <span className="mt-1 block text-xs text-ink-muted">
              O vínculo permite reembolso parcial sem alterar a despesa original.
            </span>
          </label>
        ) : null}

        <label className="block text-sm">
          <span className="font-medium text-ink">Valor (R$)</span>
          <input
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Data</span>
          <input
            type="date"
            value={postedOn}
            onChange={(event) => setPostedOn(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm sm:col-span-2">
          <span className="font-medium text-ink">Descrição</span>
          <input
            type="text"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={500}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Categoria</span>
          <select
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            <option value="">Sem categoria</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>

        {isAdjustment ? (
          <label className="block text-sm">
            <span className="font-medium text-ink">Motivo do ajuste</span>
            <input
              type="text"
              value={adjustmentReason}
              onChange={(event) => setAdjustmentReason(event.target.value)}
              maxLength={500}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        ) : null}

        <label className="block text-sm sm:col-span-2">
          <span className="font-medium text-ink">Observações</span>
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            maxLength={2000}
            rows={3}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>
      </div>

      <button
        type="submit"
        disabled={create.isPending}
        className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
      >
        {create.isPending ? 'Registrando…' : 'Registrar lançamento'}
      </button>
    </form>
  )
}
