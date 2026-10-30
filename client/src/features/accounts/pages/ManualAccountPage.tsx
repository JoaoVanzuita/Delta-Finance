import { useState } from 'react'

import { Link, useNavigate, useParams } from 'react-router'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type Account,
  type AccountUpdateInput,
  type ManualAccountInput,
} from '@/features/integration/api'

const TYPES = [
  { value: 'CASH', label: 'Dinheiro' },
  { value: 'WALLET', label: 'Carteira digital' },
  { value: 'BANK', label: 'Conta bancária' },
] as const

type ManualType = (typeof TYPES)[number]['value']

function todayReference(): string {
  return `${new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())}T12:00:00Z`
}

/**
 * Criação/edição de conta manual.
 *
 * O tipo é imutável após a criação; desativar impede novos lançamentos e preserva o histórico.
 */
export function ManualAccountPage() {
  const { id } = useParams<{ id: string }>()
  const editing = Boolean(id)
  const accountQuery = useQuery({
    queryKey: ['account', id],
    queryFn: () => financeApi.account(id ?? ''),
    enabled: editing,
  })

  if (editing && accountQuery.isLoading) {
    return (
      <p role="status" className="text-sm text-ink-muted">
        Carregando conta…
      </p>
    )
  }

  if (editing && accountQuery.isError) {
    return (
      <p
        role="alert"
        className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
      >
        {accountQuery.error instanceof ApiError
          ? accountQuery.error.message
          : 'Não foi possível carregar a conta.'}
      </p>
    )
  }

  if (editing && accountQuery.data && accountQuery.data.origin !== 'MANUAL') {
    return (
      <p
        role="alert"
        className="rounded-card border border-warning/40 bg-warning/5 p-4 text-sm text-ink"
      >
        Contas importadas não podem ser editadas manualmente. Use “Renomear” na tela de detalhes
        para alterar apenas o nome de exibição; os dados bancários continuam vindo do Pluggy.
      </p>
    )
  }

  return (
    <ManualAccountForm
      key={accountQuery.data?.id ?? 'new'}
      account={editing ? accountQuery.data : undefined}
    />
  )
}

function ManualAccountForm({ account }: { readonly account?: Account }) {
  const editing = Boolean(account)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [type, setType] = useState<ManualType>(() => {
    if (account?.type === 'CASH' || account?.type === 'WALLET' || account?.type === 'BANK') {
      return account.type
    }
    return 'CASH'
  })
  const [alias, setAlias] = useState(account?.alias ?? '')
  const [referenceBalance, setReferenceBalance] = useState(
    account?.referenceBalance ?? account?.currentBalance ?? '0.00',
  )
  const [referenceDate, setReferenceDate] = useState(
    account?.balanceReferenceAt ?? todayReference(),
  )
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE'>(
    account?.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
  )
  const [notes, setNotes] = useState(account?.notes ?? '')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (input: ManualAccountInput) => financeApi.createAccount(input),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      navigate(`/accounts/${created.id}`)
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível criar a conta.')
    },
  })

  const update = useMutation({
    mutationFn: (input: AccountUpdateInput) => financeApi.updateAccount(account?.id ?? '', input),
    onSuccess: (updated) => {
      setMessage('Conta atualizada.')
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['account', updated.id] })
    },
    onError: (caught) => {
      setMessage(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível atualizar a conta.')
    },
  })

  function submit() {
    setError(null)
    setMessage(null)
    if (!alias.trim()) {
      setError('Informe o apelido da conta.')
      return
    }
    const parsed = Number(referenceBalance.replace(',', '.'))
    if (Number.isNaN(parsed)) {
      setError('Saldo de referência inválido.')
      return
    }
    if (editing) {
      update.mutate({
        alias: alias.trim(),
        referenceBalance: referenceBalance.replace(',', '.'),
        referenceDate,
        status,
        notes,
      })
    } else {
      create.mutate({
        type,
        alias: alias.trim(),
        referenceBalance: referenceBalance.replace(',', '.'),
        referenceDate,
        currency: 'BRL',
        notes,
      })
    }
  }

  const busy = create.isPending || update.isPending

  return (
    <div className="mx-auto w-full max-w-2xl space-y-6">
      <Link to="/accounts" className="text-sm font-medium text-action underline underline-offset-2">
        ← Contas e cartões
      </Link>

      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Dinheiro e carteiras
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">
          {editing ? 'Editar conta manual' : 'Nova conta manual'}
        </h1>
        <p className="max-w-xl text-sm leading-relaxed text-ink-muted">
          Use o saldo e a data de referência informados por você. O saldo atual soma os lançamentos
          efetivados a partir dessa data. O tipo não pode ser alterado depois da criação.
        </p>
      </header>

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

      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
        className="space-y-4 rounded-card border border-line bg-surface p-5 sm:p-6"
      >
        <label className="block text-sm">
          <span className="font-medium text-ink">Tipo</span>
          <select
            value={type}
            onChange={(event) => setType(event.target.value as ManualType)}
            disabled={editing}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink disabled:opacity-60"
          >
            {TYPES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {editing ? (
            <span className="mt-1 block text-xs text-ink-muted">
              O tipo é imutável após a criação.
            </span>
          ) : null}
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Apelido</span>
          <input
            type="text"
            value={alias}
            onChange={(event) => setAlias(event.target.value)}
            maxLength={120}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="font-medium text-ink">Saldo de referência (R$)</span>
            <input
              type="text"
              inputMode="decimal"
              value={referenceBalance}
              onChange={(event) => setReferenceBalance(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>

          <label className="block text-sm">
            <span className="font-medium text-ink">Data de referência</span>
            <input
              type="date"
              value={referenceDate.slice(0, 10)}
              onChange={(event) =>
                setReferenceDate(
                  event.target.value ? `${event.target.value}T12:00:00Z` : todayReference(),
                )
              }
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        </div>

        <label className="block text-sm">
          <span className="font-medium text-ink">Observações</span>
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            maxLength={500}
            rows={3}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        {editing ? (
          <label className="block text-sm">
            <span className="font-medium text-ink">Estado</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value as 'ACTIVE' | 'INACTIVE')}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="ACTIVE">Ativa</option>
              <option value="INACTIVE">Inativa (não recebe novos lançamentos)</option>
            </select>
          </label>
        ) : null}

        <button
          type="submit"
          disabled={busy}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
        >
          {busy ? 'Salvando…' : editing ? 'Salvar alterações' : 'Criar conta manual'}
        </button>
      </form>
    </div>
  )
}
