import { useMemo, useRef, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, Tags } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState } from '@/components/ui/empty-state'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { CategoryIcon } from '@/features/categories/CategoryIcon'
import { ApiError } from '@/lib/api/problem'
import { invalidateCategoryViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'

import { financeApi, type Category } from '@/features/integration/api'

const KIND_OPTIONS = [
  { value: 'EXPENSE', label: 'Despesa' },
  { value: 'INCOME', label: 'Receita' },
  { value: 'TRANSFER', label: 'Transferência' },
  { value: 'OTHER', label: 'Outra' },
] as const

type Kind = (typeof KIND_OPTIONS)[number]['value']

const GROUP_ORDER: readonly Kind[] = ['EXPENSE', 'INCOME', 'TRANSFER', 'OTHER']

const GROUP_LABELS: Record<Kind, string> = {
  EXPENSE: 'Despesas',
  INCOME: 'Receitas',
  TRANSFER: 'Transferências',
  OTHER: 'Outras',
}

function kindLabel(kind: string): string {
  return KIND_OPTIONS.find((option) => option.value === kind)?.label ?? kind
}

/**
 * Categorias predefinidas e personalizadas, agrupadas por tipo.
 * A cor/ícone é apresentação determinística; nenhuma migration é necessária.
 */
export function CategoriesPage() {
  const queryClient = useQueryClient()
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: financeApi.categories,
  })
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [deactivating, setDeactivating] = useState<Category | null>(null)

  const categories = useMemo(() => categoriesQuery.data?.items ?? [], [categoriesQuery.data])
  const visible = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('pt-BR')
    if (!term) return categories
    return categories.filter((category) => category.name.toLocaleLowerCase('pt-BR').includes(term))
  }, [categories, search])

  const groups = GROUP_ORDER.map((kind) => ({
    kind,
    categories: visible.filter((category) => category.kind === kind),
  })).filter((group) => group.categories.length > 0)

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Organizar"
        title="Categorias"
        description="Predefinidas e personalizadas. Desativar não apaga transações: elas são reatribuídas ou ficam sem categoria."
        actions={
          <Button
            type="button"
            onClick={() => setCreating(true)}
            leadingIcon={<Plus className="size-4" />}
          >
            Nova categoria
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-3 rounded-card border border-line bg-surface p-4">
        <label className="min-w-0 flex-1 text-body-sm">
          <span className="font-medium text-ink">Buscar categoria</span>
          <span className="relative mt-1 block">
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted"
            />
            <Input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Nome da categoria"
              className="pl-9"
            />
          </span>
        </label>
        <p role="status" className="text-caption text-ink-muted">
          {visible.length} de {categories.length} categoria(s)
        </p>
      </div>

      {categoriesQuery.isLoading ? (
        <div className="space-y-2" role="status" aria-live="polite">
          <span className="sr-only">Carregando categorias…</span>
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-16 rounded-card" />
          ))}
        </div>
      ) : null}

      {categoriesQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar as categorias">
          {categoriesQuery.error instanceof ApiError
            ? categoriesQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {categoriesQuery.isSuccess && categories.length === 0 ? (
        <EmptyState
          icon={<Tags className="size-5" />}
          title="Nenhuma categoria cadastrada"
          description="Crie uma categoria para classificar receitas e despesas. As categorias predefinidas aparecem automaticamente."
          action={
            <Button type="button" onClick={() => setCreating(true)}>
              Criar categoria
            </Button>
          }
        />
      ) : null}

      {categoriesQuery.isSuccess && categories.length > 0 && groups.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-5 text-body-sm text-ink-muted">
          Nenhuma categoria corresponde a “{search.trim()}”. Limpe a busca para ver todas.
        </p>
      ) : null}

      {groups.map((group) => (
        <section
          key={group.kind}
          aria-labelledby={`categories-${group.kind}`}
          className="space-y-3"
        >
          <div className="flex items-center justify-between gap-2">
            <h2
              id={`categories-${group.kind}`}
              className="text-section-title font-semibold text-ink"
            >
              {GROUP_LABELS[group.kind]}
            </h2>
            <Badge variant="neutral">{group.categories.length}</Badge>
          </div>
          <ul className="divide-y divide-line rounded-card border border-line bg-surface">
            {group.categories.map((category) => (
              <li key={category.id}>
                <CategoryRow
                  category={category}
                  onEdit={() => setEditing(category)}
                  onDeactivate={() => setDeactivating(category)}
                />
              </li>
            ))}
          </ul>
        </section>
      ))}

      {creating ? (
        <CategoryFormDialog
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false)
            void invalidateCategoryViews(queryClient)
          }}
        />
      ) : null}

      {editing ? (
        <CategoryFormDialog
          category={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void invalidateCategoryViews(queryClient)
          }}
        />
      ) : null}

      {deactivating ? (
        <DeactivateCategoryDialog
          category={deactivating}
          categories={categories}
          onClose={() => setDeactivating(null)}
          onDeactivated={() => {
            setDeactivating(null)
            void invalidateCategoryViews(queryClient)
          }}
        />
      ) : null}
    </div>
  )
}

function CategoryRow({
  category,
  onEdit,
  onDeactivate,
}: {
  readonly category: Category
  readonly onEdit: () => void
  readonly onDeactivate: () => void
}) {
  return (
    <div className="flex flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center">
      <CategoryIcon id={category.id} name={category.name} />
      <div className="min-w-0 flex-1">
        <p
          className={cn(
            'text-body-sm font-medium',
            category.active ? 'text-ink' : 'text-ink-muted',
          )}
        >
          {category.name}
        </p>
        <div className="mt-1 flex flex-wrap items-center gap-1">
          <span className="text-caption text-ink-muted">{kindLabel(category.kind)}</span>
          <Badge variant="neutral">{category.predefined ? 'Predefinida' : 'Personalizada'}</Badge>
          {!category.active ? <Badge variant="warning">Inativa</Badge> : null}
        </div>
      </div>
      <div className="flex flex-wrap gap-2 sm:ml-auto">
        <Button type="button" variant="secondary" size="sm" onClick={onEdit}>
          Editar
        </Button>
        {category.active ? (
          <Button type="button" variant="ghost" size="sm" onClick={onDeactivate}>
            Desativar
          </Button>
        ) : null}
      </div>
    </div>
  )
}

function CategoryFormDialog({
  category,
  onClose,
  onSaved,
}: {
  readonly category?: Category
  readonly onClose: () => void
  readonly onSaved: () => void
}) {
  const editing = Boolean(category)
  const [name, setName] = useState(category?.name ?? '')
  const [kind, setKind] = useState<Kind>((category?.kind as Kind) ?? 'EXPENSE')
  const [error, setError] = useState<string | null>(null)
  const nameRef = useRef<HTMLInputElement>(null)

  const save = useMutation({
    mutationFn: () =>
      category
        ? financeApi.updateCategory(category.id, { name: name.trim(), kind })
        : financeApi.createCategory({ name: name.trim(), kind }),
    onSuccess: onSaved,
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível salvar a categoria.')
    },
  })

  function submit() {
    setError(null)
    if (!name.trim()) {
      setError('Informe o nome da categoria.')
      return
    }
    save.mutate()
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={editing ? 'Editar categoria' : 'Nova categoria'}
      description={
        editing
          ? 'Renomear ou mudar o tipo afeta a forma como a categoria aparece nos relatórios.'
          : 'Categorias personalizadas complementam as predefinidas.'
      }
      size="sm"
      busy={save.isPending}
      initialFocusRef={nameRef}
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose} disabled={save.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            form="category-form"
            loading={save.isPending}
            loadingLabel="Salvando…"
          >
            {editing ? 'Salvar categoria' : 'Criar categoria'}
          </Button>
        </div>
      }
    >
      <form
        id="category-form"
        noValidate
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
      >
        {error ? <Alert variant="danger">{error}</Alert> : null}
        <Field id="category-name" label="Nome" required>
          <Input
            ref={nameRef}
            id="category-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={80}
            autoFocus
          />
        </Field>
        <Field id="category-kind" label="Tipo">
          <Select
            id="category-kind"
            value={kind}
            onChange={(event) => setKind(event.target.value as Kind)}
          >
            {KIND_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </Field>
      </form>
    </Dialog>
  )
}

function DeactivateCategoryDialog({
  category,
  categories,
  onClose,
  onDeactivated,
}: {
  readonly category: Category
  readonly categories: readonly Category[]
  readonly onClose: () => void
  readonly onDeactivated: () => void
}) {
  const [reassignTo, setReassignTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const reassignRef = useRef<HTMLSelectElement>(null)

  const deactivate = useMutation({
    mutationFn: () => financeApi.deactivateCategory(category.id, reassignTo || undefined),
    onSuccess: onDeactivated,
    onError: (caught) => {
      setError(caught instanceof ApiError ? caught.message : 'Não foi possível desativar.')
    },
  })

  return (
    <Dialog
      open
      onClose={onClose}
      title={`Desativar “${category.name}”`}
      description="A categoria deixa de aceitar novos lançamentos. Nenhuma transação é apagada."
      size="sm"
      busy={deactivate.isPending}
      initialFocusRef={reassignRef}
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={onClose}
            disabled={deactivate.isPending}
          >
            Cancelar
          </Button>
          <Button
            type="button"
            variant="danger"
            loading={deactivate.isPending}
            loadingLabel="Desativando…"
            onClick={() => deactivate.mutate()}
          >
            Confirmar desativação
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {error ? <Alert variant="danger">{error}</Alert> : null}
        <p className="text-body-sm text-ink-muted">
          Transações existentes desta categoria serão reatribuídas para a opção abaixo. Se você
          escolher “Sem categoria”, elas permanecem visíveis e editáveis, mas sem classificação.
        </p>
        <Field id="category-reassign" label="Reatribuir transações para">
          <Select
            ref={reassignRef}
            id="category-reassign"
            value={reassignTo}
            onChange={(event) => setReassignTo(event.target.value)}
          >
            <option value="">Sem categoria</option>
            {categories
              .filter((item) => item.id !== category.id && item.active)
              .map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
          </Select>
        </Field>
      </div>
    </Dialog>
  )
}
