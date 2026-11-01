import { useMemo, useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Drawer } from '@/components/ui/drawer'
import { PageHeader } from '@/components/ui/page-header'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api/problem'
import { invalidateWealthViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import { cn } from '@/lib/cn'

import {
  financeApi,
  type WealthManualItem,
  type WealthManualItemInput,
  type WealthOverview,
} from '@/features/integration/api'

import { ManualItemForm } from '../components/ManualItemForm'
import { WealthComposition } from '../components/WealthComposition'
import { COMPLETENESS_LABELS, formatDate, formatMoney } from '../format'

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/**
 * Patrimônio consolidado em ativos, passivos e patrimônio líquido.
 * Itens manuais são cadastrados/editados em drawer e nunca viram cotação.
 */
export function WealthPage() {
  const queryClient = useQueryClient()
  const overviewQuery = useQuery({
    queryKey: queryKeys.wealthOverview,
    queryFn: () => financeApi.wealthOverview(),
  })
  const manualQuery = useQuery({
    queryKey: queryKeys.wealthManualItems,
    queryFn: () => financeApi.wealthManualItems(),
  })

  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [form, setForm] = useState<'new' | WealthManualItem | null>(null)
  const [deleting, setDeleting] = useState<WealthManualItem | null>(null)

  const manualItems = useMemo(() => manualQuery.data?.items ?? [], [manualQuery.data])
  const assets = useMemo(() => manualItems.filter((item) => item.kind === 'ASSET'), [manualItems])
  const liabilities = useMemo(
    () => manualItems.filter((item) => item.kind === 'LIABILITY'),
    [manualItems],
  )

  const save = useMutation({
    mutationFn: (input: WealthManualItemInput) =>
      form && form !== 'new'
        ? financeApi.updateWealthManualItem(form.id, input)
        : financeApi.createWealthManualItem(input),
    onSuccess: async () => {
      setError(null)
      setFormError(null)
      setMessage(form === 'new' ? 'Item manual adicionado.' : 'Item manual atualizado.')
      setForm(null)
      await invalidateWealthViews(queryClient)
    },
    onError: (caught) => {
      setMessage(null)
      setFormError(messageFrom(caught, 'Não foi possível salvar o item manual.'))
    },
  })

  const remove = useMutation({
    mutationFn: (item: WealthManualItem) => financeApi.deleteWealthManualItem(item.id),
    onSuccess: async () => {
      setDeleting(null)
      setDeleteError(null)
      setError(null)
      setMessage('Item manual excluído.')
      await invalidateWealthViews(queryClient)
    },
    onError: (caught) => {
      setDeleteError(messageFrom(caught, 'Não foi possível excluir o item manual.'))
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Planejar"
        title="Patrimônio"
        description="Ativos, passivos e patrimônio líquido a partir de contas, cartões, investimentos e itens manuais, sem dupla contagem."
        actions={
          <Button
            type="button"
            onClick={() => {
              setFormError(null)
              setForm('new')
            }}
            leadingIcon={<Plus className="size-4" />}
          >
            Adicionar item manual
          </Button>
        }
      />

      {message ? (
        <Alert variant="success" role="status">
          {message}
        </Alert>
      ) : null}
      {error ? (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      ) : null}

      {overviewQuery.isLoading ? (
        <div className="space-y-4" role="status" aria-live="polite">
          <span className="sr-only">Carregando patrimônio…</span>
          <Skeleton className="h-44 rounded-panel" />
          <Skeleton className="h-64 rounded-card" />
        </div>
      ) : null}
      {overviewQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar o patrimônio">
          {messageFrom(overviewQuery.error, 'Tente novamente em instantes.')}
        </Alert>
      ) : null}
      {overviewQuery.data ? <WealthOverviewSections overview={overviewQuery.data} /> : null}

      <section aria-labelledby="wealth-manual-heading" className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="wealth-manual-heading" className="text-section-title font-semibold text-ink">
              Itens manuais
            </h2>
            <p className="text-caption text-ink-muted">
              Valores de referência informados por você. Não são cotação de mercado.
            </p>
          </div>
        </div>

        {manualQuery.isLoading ? (
          <p role="status" className="text-body-sm text-ink-muted">
            Carregando itens manuais…
          </p>
        ) : null}
        {manualQuery.isError ? (
          <Alert variant="danger" title="Itens manuais indisponíveis">
            {messageFrom(manualQuery.error, 'Não foi possível carregar os itens manuais.')}
          </Alert>
        ) : null}
        {manualQuery.isSuccess && manualItems.length === 0 ? (
          <p className="rounded-card border border-dashed border-line-strong bg-surface p-4 text-body-sm text-ink-muted">
            Nenhum ativo ou passivo manual registrado. Use “Novo item” para complementar a visão.
          </p>
        ) : null}

        {manualItems.length > 0 ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <ManualGroup
              title="Ativos manuais"
              items={assets}
              onEdit={(item) => {
                setFormError(null)
                setForm(item)
              }}
              onDelete={(item) => {
                setDeleteError(null)
                setDeleting(item)
              }}
            />
            <ManualGroup
              title="Passivos manuais"
              items={liabilities}
              onEdit={(item) => {
                setFormError(null)
                setForm(item)
              }}
              onDelete={(item) => {
                setDeleteError(null)
                setDeleting(item)
              }}
            />
          </div>
        ) : null}
      </section>

      <Drawer
        open={form !== null}
        onClose={() => setForm(null)}
        title={form === 'new' || form === null ? 'Novo item manual' : 'Editar item manual'}
        description="O valor é uma referência informada por você, não uma cotação de mercado."
        width="md"
        busy={save.isPending}
      >
        {formError ? (
          <Alert variant="danger" className="mb-4">
            {formError}
          </Alert>
        ) : null}
        {form ? (
          <ManualItemForm
            key={form === 'new' ? 'new' : form.id}
            initial={form === 'new' ? undefined : form}
            pending={save.isPending}
            submitLabel={form === 'new' ? 'Adicionar item manual' : 'Salvar item'}
            onSubmit={(input) => save.mutate(input)}
            onCancel={() => setForm(null)}
          />
        ) : null}
      </Drawer>

      <Dialog
        open={deleting !== null}
        onClose={() => {
          setDeleting(null)
          setDeleteError(null)
        }}
        title={`Excluir “${deleting?.description ?? 'item'}”`}
        description="O item deixará de compor o patrimônio. Esta ação não apaga transações."
        size="sm"
        busy={remove.isPending}
        footer={
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setDeleting(null)}>
              Cancelar
            </Button>
            <Button
              type="button"
              variant="danger"
              loading={remove.isPending}
              loadingLabel="Excluindo…"
              onClick={() => {
                if (deleting) remove.mutate(deleting)
              }}
            >
              Confirmar exclusão
            </Button>
          </div>
        }
      >
        {deleteError ? <Alert variant="danger">{deleteError}</Alert> : null}
        <p className="text-body-sm text-ink-muted">
          {deleting
            ? `${deleting.description} · ${deleting.kind === 'ASSET' ? 'Ativo' : 'Passivo'} · ${formatMoney(deleting.referenceValue)}.`
            : null}
        </p>
      </Dialog>
    </div>
  )
}

function WealthOverviewSections({ overview }: { readonly overview: WealthOverview }) {
  return (
    <div className="space-y-6">
      <section aria-labelledby="wealth-summary-heading" className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h2 id="wealth-summary-heading" className="text-section-title font-semibold text-ink">
              Resumo patrimonial
            </h2>
            <p className="text-caption text-ink-muted">
              Referência: {formatDate(overview.referenceDate)}
            </p>
          </div>
          <Badge
            variant={
              overview.completeness === 'COMPLETE'
                ? 'income'
                : overview.completeness === 'PARTIAL'
                  ? 'warning'
                  : 'info'
            }
          >
            {COMPLETENESS_LABELS[overview.completeness] ?? overview.completeness}
          </Badge>
        </div>

        <div className="rounded-panel border border-primary/25 bg-primary-soft p-5 sm:p-6">
          <p className="overline text-ink-muted">Patrimônio líquido</p>
          <p className="numeric mt-2 text-metric-xl font-bold tracking-tight text-ink">
            {overview.hasData ? formatMoney(overview.netWorth) : 'Sem dados suficientes'}
          </p>
          <p className="mt-1 text-body-sm text-ink-muted">
            Ativos brutos menos passivos. O valor de referência não é cotação de mercado.
          </p>
        </div>

        <dl className="grid gap-3 sm:grid-cols-2">
          <Metric
            label="Ativos brutos"
            value={overview.hasData ? formatMoney(overview.grossAssets) : 'Não consolidado'}
            tone="asset"
          />
          <Metric
            label="Passivos"
            value={overview.hasData ? formatMoney(overview.liabilities) : 'Não consolidado'}
            tone="liability"
          />
        </dl>

        {!overview.hasData ? (
          <Alert variant="warning" title="Visão não consolidada">
            Cadastre um item manual ou importe contas para consolidar o patrimônio.
          </Alert>
        ) : null}
        {overview.completeness === 'PARTIAL' ? (
          <Alert variant="warning" title="Visão parcial">
            Alguns itens não compõem os totais. Veja os motivos em “Fora do cálculo”.
          </Alert>
        ) : null}
        {overview.completeness === 'ESTIMATED' ? (
          <Alert variant="info" title="Visão estimada">
            Há valores de referência com mais de 30 dias. A visão é uma referência, não cotação.
          </Alert>
        ) : null}
      </section>

      <WealthComposition items={overview.items} />
    </div>
  )
}

function Metric({
  label,
  value,
  tone,
}: {
  readonly label: string
  readonly value: string
  readonly tone: 'asset' | 'liability'
}) {
  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <dt className="text-caption font-semibold tracking-[0.04em] text-ink-muted uppercase">
        {label}
      </dt>
      <dd
        className={cn(
          'numeric mt-2 text-metric-lg font-semibold',
          tone === 'asset' ? 'text-ink' : 'text-expense',
        )}
      >
        {value}
      </dd>
    </div>
  )
}

function ManualGroup({
  title,
  items,
  onEdit,
  onDelete,
}: {
  readonly title: string
  readonly items: readonly WealthManualItem[]
  readonly onEdit: (item: WealthManualItem) => void
  readonly onDelete: (item: WealthManualItem) => void
}) {
  return (
    <section className="rounded-card border border-line bg-surface p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-body-sm font-semibold text-ink">{title}</h3>
        <Badge variant="neutral">{items.length}</Badge>
      </div>
      {items.length === 0 ? (
        <p className="mt-2 text-caption text-ink-muted">Nenhum item nesta natureza.</p>
      ) : (
        <ul className="mt-3 divide-y divide-line">
          {items.map((item) => (
            <ManualItemRow key={item.id} item={item} onEdit={onEdit} onDelete={onDelete} />
          ))}
        </ul>
      )}
    </section>
  )
}

function ManualItemRow({
  item,
  onEdit,
  onDelete,
}: {
  readonly item: WealthManualItem
  readonly onEdit: (item: WealthManualItem) => void
  readonly onDelete: (item: WealthManualItem) => void
}) {
  return (
    <li className="space-y-2 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-body-sm font-medium text-ink">{item.description}</p>
          <p className="text-caption text-ink-muted">
            {item.type} · {formatDate(item.referenceDate)}
          </p>
        </div>
        <p
          className={cn(
            'numeric shrink-0 text-body-sm font-semibold',
            item.kind === 'LIABILITY' ? 'text-expense' : 'text-ink',
          )}
        >
          {formatMoney(item.referenceValue)}
        </p>
      </div>
      {item.notes ? <p className="text-caption text-ink-muted">{item.notes}</p> : null}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={() => onEdit(item)}
          leadingIcon={<Pencil className="size-3.5" />}
        >
          Editar
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onDelete(item)}
          leadingIcon={<Trash2 className="size-3.5" />}
        >
          Excluir
        </Button>
      </div>
    </li>
  )
}
