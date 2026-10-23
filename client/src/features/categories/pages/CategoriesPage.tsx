import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { financeApi, type Category } from '@/features/integration/api'

const KIND_OPTIONS = [
  { value: 'EXPENSE', label: 'Despesa' },
  { value: 'INCOME', label: 'Receita' },
  { value: 'TRANSFER', label: 'Transferência' },
  { value: 'OTHER', label: 'Outra' },
] as const

type Kind = (typeof KIND_OPTIONS)[number]['value']

export function CategoriesPage() {
  const queryClient = useQueryClient()
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: financeApi.categories })
  const [name, setName] = useState('')
  const [kind, setKind] = useState<Kind>('EXPENSE')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['categories'] })
    void queryClient.invalidateQueries({ queryKey: ['transactions'] })
  }

  const create = useMutation({
    mutationFn: () => financeApi.createCategory({ name: name.trim(), kind }),
    onSuccess: () => {
      setName('')
      setError(null)
      setMessage('Categoria criada.')
      invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível criar a categoria.')
    },
  })

  const categories = categoriesQuery.data?.items ?? []

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Organização
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Categorias</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Categorias predefinidas e personalizadas. Desativar não apaga transações: elas são
          reatribuídas ou ficam sem categoria.
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
        className="grid gap-3 rounded-card border border-line bg-surface p-4 sm:grid-cols-[1fr_180px_auto] sm:items-end"
        onSubmit={(event) => {
          event.preventDefault()
          setError(null)
          setMessage(null)
          if (!name.trim()) {
            setError('Informe o nome da categoria.')
            return
          }
          create.mutate()
        }}
      >
        <label className="block text-sm">
          <span className="font-medium text-ink">Nova categoria</span>
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={80}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>
        <label className="block text-sm">
          <span className="font-medium text-ink">Tipo</span>
          <select
            value={kind}
            onChange={(event) => setKind(event.target.value as Kind)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            {KIND_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={create.isPending}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
        >
          {create.isPending ? 'Criando…' : 'Criar'}
        </button>
      </form>

      {categoriesQuery.isLoading ? (
        <p role="status" className="text-sm text-ink-muted">
          Carregando categorias…
        </p>
      ) : null}
      {categoriesQuery.isError ? (
        <p role="alert" className="text-sm text-expense">
          {categoriesQuery.error instanceof ApiError
            ? categoriesQuery.error.message
            : 'Não foi possível carregar as categorias.'}
        </p>
      ) : null}

      {categoriesQuery.isSuccess ? (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {categories.map((category) => (
            <CategoryRow
              key={category.id}
              category={category}
              categories={categories}
              onChanged={invalidate}
            />
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function CategoryRow({
  category,
  categories,
  onChanged,
}: {
  readonly category: Category
  readonly categories: readonly Category[]
  readonly onChanged: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name)
  const [kind, setKind] = useState<Kind>(category.kind)
  const [confirming, setConfirming] = useState(false)
  const [reassignTo, setReassignTo] = useState('')
  const [error, setError] = useState<string | null>(null)

  const update = useMutation({
    mutationFn: () => financeApi.updateCategory(category.id, { name: name.trim(), kind }),
    onSuccess: () => {
      setEditing(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível editar.')
    },
  })

  const deactivate = useMutation({
    mutationFn: () => financeApi.deactivateCategory(category.id, reassignTo || undefined),
    onSuccess: () => {
      setConfirming(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível desativar.')
    },
  })

  return (
    <li className="space-y-2 p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="font-medium text-ink">
            {category.name}{' '}
            {!category.active ? (
              <span className="text-xs font-normal text-ink-muted">(inativa)</span>
            ) : null}
          </p>
          <p className="text-xs text-ink-muted">
            {KIND_OPTIONS.find((option) => option.value === category.kind)?.label ?? category.kind}
            {category.predefined ? ' · predefinida' : ' · personalizada'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              setEditing((value) => !value)
              setConfirming(false)
            }}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
          >
            {editing ? 'Cancelar' : 'Editar'}
          </button>
          {category.active ? (
            <button
              type="button"
              onClick={() => {
                setConfirming((value) => !value)
                setEditing(false)
              }}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
            >
              {confirming ? 'Cancelar' : 'Desativar'}
            </button>
          ) : null}
        </div>
      </div>

      {editing ? (
        <form
          noValidate
          className="flex flex-col gap-2 rounded-card border border-line bg-surface-muted p-3 sm:flex-row sm:items-end"
          onSubmit={(event) => {
            event.preventDefault()
            if (!name.trim()) {
              setError('Informe o nome.')
              return
            }
            update.mutate()
          }}
        >
          <label className="flex-1 text-sm">
            <span className="font-medium text-ink">Nome</span>
            <input
              type="text"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={80}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
          <label className="text-sm">
            <span className="font-medium text-ink">Tipo</span>
            <select
              value={kind}
              onChange={(event) => setKind(event.target.value as Kind)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              {KIND_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="submit"
            disabled={update.isPending}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface disabled:opacity-60"
          >
            Salvar
          </button>
        </form>
      ) : null}

      {confirming ? (
        <div className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-3">
          <label className="block text-sm">
            <span className="font-medium text-ink">Reatribuir transações para</span>
            <select
              value={reassignTo}
              onChange={(event) => setReassignTo(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            >
              <option value="">Sem categoria</option>
              {categories
                .filter((item) => item.id !== category.id && item.active)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
            </select>
          </label>
          <button
            type="button"
            disabled={deactivate.isPending}
            onClick={() => deactivate.mutate()}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-sm font-semibold text-expense disabled:opacity-60"
          >
            Confirmar desativação
          </button>
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}
    </li>
  )
}
