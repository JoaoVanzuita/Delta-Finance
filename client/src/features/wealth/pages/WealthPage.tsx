import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type WealthItem,
  type WealthManualItem,
  type WealthManualItemInput,
  type WealthOverview,
} from '@/features/integration/api'

import { ManualItemForm } from '../components/ManualItemForm'
import {
  COMPLETENESS_LABELS,
  ORIGIN_LABELS,
  SOURCE_LABELS,
  formatDate,
  formatMoney,
} from '../format'

const OVERVIEW_KEY = ['wealth', 'overview'] as const
const MANUAL_KEY = ['wealth', 'manual-items'] as const

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/**
 * Visão patrimonial consolidada.
 *
 * <p>Mostra a composição com lacunas explícitas e valores de referência. Nunca apresenta valor
 * manual como cotação de mercado.
 */
export function WealthPage() {
  const queryClient = useQueryClient()
  const overviewQuery = useQuery({
    queryKey: OVERVIEW_KEY,
    queryFn: () => financeApi.wealthOverview(),
  })
  const manualQuery = useQuery({
    queryKey: MANUAL_KEY,
    queryFn: () => financeApi.wealthManualItems(),
  })

  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formVersion, setFormVersion] = useState(0)

  const invalidateWealth = () => {
    void queryClient.invalidateQueries({ queryKey: OVERVIEW_KEY })
    void queryClient.invalidateQueries({ queryKey: MANUAL_KEY })
  }

  const createManual = useMutation({
    mutationFn: (input: WealthManualItemInput) => financeApi.createWealthManualItem(input),
    onSuccess: () => {
      setError(null)
      setMessage('Item manual adicionado.')
      setFormVersion((version) => version + 1)
      invalidateWealth()
    },
    onError: (caught) => {
      setMessage(null)
      setError(messageFrom(caught, 'Não foi possível adicionar o item manual.'))
    },
  })

  const manualItems = manualQuery.data?.items ?? []

  return (
    <div className="mx-auto w-full max-w-5xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Fase posterior
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Patrimônio</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Patrimônio bruto, passivos e patrimônio líquido consolidados a partir de contas, cartões,
          investimentos e itens manuais, sem dupla contagem.
        </p>
      </header>

      {message ? (
        <p
          role="status"
          aria-live="polite"
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

      {overviewQuery.isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando patrimônio…
        </p>
      ) : null}
      {overviewQuery.isError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {messageFrom(overviewQuery.error, 'Não foi possível carregar o patrimônio.')}
        </p>
      ) : null}
      {overviewQuery.data ? <OverviewSections overview={overviewQuery.data} /> : null}

      <section
        aria-labelledby="wealth-manual-heading"
        className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <div className="space-y-1">
          <h2 id="wealth-manual-heading" className="text-lg font-semibold text-ink">
            Ativos e passivos manuais
          </h2>
          <p className="text-sm leading-relaxed text-ink-muted">
            Informe o valor e a data de referência. O valor é uma referência do usuário e não uma
            cotação de mercado.
          </p>
        </div>

        <ManualItemForm
          key={formVersion}
          pending={createManual.isPending}
          submitLabel="Adicionar item manual"
          onSubmit={(input) => createManual.mutate(input)}
        />

        {manualQuery.isLoading ? (
          <p role="status" aria-live="polite" className="text-sm text-ink-muted">
            Carregando itens manuais…
          </p>
        ) : null}
        {manualQuery.isError ? (
          <p role="alert" className="text-sm text-expense">
            {messageFrom(manualQuery.error, 'Não foi possível carregar os itens manuais.')}
          </p>
        ) : null}
        {manualQuery.isSuccess && manualItems.length === 0 ? (
          <p className="text-sm text-ink-muted">Nenhum ativo ou passivo manual registrado.</p>
        ) : null}
        {manualItems.length > 0 ? (
          <ul className="space-y-3">
            {manualItems.map((item) => (
              <ManualItemRow key={item.id} item={item} onChanged={invalidateWealth} />
            ))}
          </ul>
        ) : null}
      </section>
    </div>
  )
}

function OverviewSections({ overview }: { readonly overview: WealthOverview }) {
  return (
    <>
      <section
        aria-labelledby="wealth-summary-heading"
        className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 id="wealth-summary-heading" className="text-lg font-semibold text-ink">
              Resumo patrimonial
            </h2>
            <p className="mt-1 text-sm text-ink-muted">
              Data de referência: {formatDate(overview.referenceDate)}
            </p>
          </div>
          <span className="rounded-card border border-line bg-surface-muted px-3 py-1 text-sm font-semibold text-ink">
            {COMPLETENESS_LABELS[overview.completeness] ?? overview.completeness}
          </span>
        </div>

        <dl className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Patrimônio bruto</dt>
            <dd className="numeric mt-1 text-lg font-semibold text-ink">
              {formatMoney(overview.grossAssets)}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Passivos</dt>
            <dd className="numeric mt-1 text-lg font-semibold text-ink">
              {formatMoney(overview.liabilities)}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Patrimônio líquido</dt>
            <dd className="numeric mt-1 text-lg font-semibold text-ink">
              {formatMoney(overview.netWorth)}
            </dd>
          </div>
        </dl>

        {!overview.hasData ? (
          <p className="text-sm text-ink-muted">
            Sem dados suficientes para consolidar o patrimônio. Cadastre itens manuais ou importe
            contas.
          </p>
        ) : null}
        {overview.completeness === 'PARTIAL' ? (
          <p className="text-sm text-ink">
            Alguns itens não compõem os totais. Veja os motivos na composição abaixo.
          </p>
        ) : null}
        {overview.completeness === 'ESTIMATED' ? (
          <p className="text-sm text-ink">
            Há valores de referência com mais de 30 dias; a visão é estimada.
          </p>
        ) : null}
        <p className="text-xs leading-relaxed text-ink-muted">
          Cada valor é uma referência informada por você e não é cotação de mercado. Saldos de
          cartão entram como passivos e não são contados novamente como despesa.
        </p>
      </section>

      <section
        aria-labelledby="wealth-composition-heading"
        className="space-y-3 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <h2 id="wealth-composition-heading" className="text-lg font-semibold text-ink">
          Composição
        </h2>
        {overview.items.length === 0 ? (
          <p className="text-sm text-ink-muted">Nenhuma fonte patrimonial disponível.</p>
        ) : (
          <ul className="divide-y divide-line">
            {overview.items.map((item) => (
              <CompositionRow key={`${item.sourceType}-${item.sourceId}`} item={item} />
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

function CompositionRow({ item }: { readonly item: WealthItem }) {
  return (
    <li className="flex flex-col gap-2 py-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="space-y-1">
        <p className="font-medium text-ink">
          {item.description}
          <span className="ml-2 text-xs font-normal text-ink-muted">
            {SOURCE_LABELS[item.sourceType] ?? item.sourceType} ·{' '}
            {ORIGIN_LABELS[item.origin] ?? item.origin}
          </span>
        </p>
        <p className="text-sm text-ink-muted">
          Data de referência: {formatDate(item.referenceDate)}
          {item.stale ? ' · referência antiga' : ''}
        </p>
        {!item.included ? (
          <p className="text-sm text-expense">Não incluído: {item.exclusionReason}</p>
        ) : null}
      </div>
      <p className="numeric text-sm font-semibold text-ink">{formatMoney(item.value)}</p>
    </li>
  )
}

function ManualItemRow({
  item,
  onChanged,
}: {
  readonly item: WealthManualItem
  readonly onChanged: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const update = useMutation({
    mutationFn: (input: WealthManualItemInput) => financeApi.updateWealthManualItem(item.id, input),
    onSuccess: () => {
      setEditing(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível salvar o item manual.'))
    },
  })

  const remove = useMutation({
    mutationFn: () => financeApi.deleteWealthManualItem(item.id),
    onSuccess: () => {
      setConfirming(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível excluir o item manual.'))
    },
  })

  return (
    <li className="space-y-2 rounded-card border border-line bg-surface-muted p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <p className="font-medium text-ink">
            {item.description}
            <span className="ml-2 text-xs font-normal text-ink-muted">
              {item.kind === 'ASSET' ? 'Ativo' : 'Passivo'} · {item.type}
            </span>
          </p>
          <p className="text-sm text-ink-muted">
            Data de referência: {formatDate(item.referenceDate)}
          </p>
          {item.notes ? <p className="text-sm text-ink">{item.notes}</p> : null}
        </div>
        <div className="flex flex-col items-start gap-2 sm:items-end">
          <p className="numeric text-sm font-semibold text-ink">
            {formatMoney(item.referenceValue)}
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                setEditing((value) => !value)
                setConfirming(false)
              }}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
            >
              {editing ? 'Cancelar edição' : 'Editar'}
            </button>
            <button
              type="button"
              onClick={() => {
                setConfirming((value) => !value)
                setEditing(false)
              }}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
            >
              {confirming ? 'Cancelar exclusão' : 'Excluir'}
            </button>
          </div>
        </div>
      </div>

      {editing ? (
        <ManualItemForm
          initial={item}
          pending={update.isPending}
          submitLabel="Salvar item"
          onSubmit={(input) => update.mutate(input)}
          onCancel={() => setEditing(false)}
        />
      ) : null}

      {confirming ? (
        <div className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-3">
          <p className="text-sm text-ink">
            Excluir este item manual? Ele deixará de compor o patrimônio.
          </p>
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => remove.mutate()}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-sm font-semibold text-expense disabled:opacity-60"
          >
            Confirmar exclusão
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
