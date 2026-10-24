import { useState } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type InvestmentPosition,
  type InvestmentPositionInput,
} from '@/features/integration/api'

import { AutoImportNotice } from '../components/AutoImportNotice'
import { PositionForm } from '../components/PositionForm'
import { formatMoney, formatQuantity, formatReferenceDate, formatReferencePrice } from '../format'

const POSITIONS_KEY = ['investments', 'positions'] as const

const BROKER_LABELS: Record<string, string> = {
  XP: 'XP Investimentos',
  SANTANDER: 'Santander Corretora',
  OTHER: 'Outra corretora',
}

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/**
 * Posições manuais de investimento.
 *
 * <p>Não há importação automática, cotação de mercado ou recomendação. Cada total é
 * quantidade × preço unitário informado, com a data de referência escolhida pelo usuário.
 */
export function InvestmentsPage() {
  const queryClient = useQueryClient()
  const positionsQuery = useQuery({
    queryKey: POSITIONS_KEY,
    queryFn: () => financeApi.positions(),
  })
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formVersion, setFormVersion] = useState(0)

  const invalidate = () => queryClient.invalidateQueries({ queryKey: POSITIONS_KEY })

  const create = useMutation({
    mutationFn: (input: InvestmentPositionInput) => financeApi.createPosition(input),
    onSuccess: () => {
      setError(null)
      setMessage('Posição adicionada.')
      setFormVersion((version) => version + 1)
      void invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setError(messageFrom(caught, 'Não foi possível adicionar a posição.'))
    },
  })

  const positions = positionsQuery.data?.items ?? []
  const total = positions.reduce((sum, position) => sum + (Number(position.total) || 0), 0)
  const latestReferenceDate = positions.reduce<string | null>(
    (latest, position) =>
      latest === null || position.referenceDate > latest ? position.referenceDate : latest,
    null,
  )

  return (
    <div className="mx-auto w-full max-w-5xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Fase secundária
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Investimentos</h1>
        <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">
          Registre manualmente ações e fundos imobiliários. Os valores usam a data e a referência
          informadas por você, sem atualização automática de cotação.
        </p>
      </header>

      <AutoImportNotice />

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

      <section
        aria-labelledby="new-position-heading"
        className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <div className="space-y-1">
          <h2 id="new-position-heading" className="text-lg font-semibold text-ink">
            Nova posição
          </h2>
          <p className="text-sm leading-relaxed text-ink-muted">
            Informe o preço unitário ou o valor total. Com apenas o valor total, o preço unitário é
            derivado pelo servidor com 8 casas decimais.
          </p>
        </div>
        <PositionForm
          key={formVersion}
          pending={create.isPending}
          submitLabel="Adicionar posição"
          onSubmit={(input) => create.mutate(input)}
        />
      </section>

      <section
        aria-labelledby="positions-summary-heading"
        className="space-y-3 rounded-card border border-line bg-surface p-4 sm:p-5"
      >
        <h2 id="positions-summary-heading" className="text-lg font-semibold text-ink">
          Resumo
        </h2>
        <dl className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Posições</dt>
            <dd className="numeric mt-1 text-sm font-semibold text-ink">{positions.length}</dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Total informado</dt>
            <dd className="numeric mt-1 text-sm font-semibold text-ink">
              {formatMoney(total.toFixed(2))}
            </dd>
          </div>
          <div className="rounded-card border border-line bg-surface-muted p-3">
            <dt className="text-xs text-ink-muted">Data de referência mais recente</dt>
            <dd className="numeric mt-1 text-sm font-semibold text-ink">
              {formatReferenceDate(latestReferenceDate)}
            </dd>
          </div>
        </dl>
        <p className="text-xs leading-relaxed text-ink-muted">
          O total é quantidade × preço unitário informado e não é cotação de mercado. Posições
          excluídas não entram no resumo.
        </p>
      </section>

      <section aria-labelledby="positions-heading" className="space-y-3">
        <h2 id="positions-heading" className="text-lg font-semibold text-ink">
          Posições registradas
        </h2>

        {positionsQuery.isLoading ? (
          <p role="status" aria-live="polite" className="text-sm text-ink-muted">
            Carregando posições…
          </p>
        ) : null}

        {positionsQuery.isError ? (
          <p
            role="alert"
            className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
          >
            {messageFrom(positionsQuery.error, 'Não foi possível carregar as posições.')}
          </p>
        ) : null}

        {positionsQuery.isSuccess && positions.length === 0 ? (
          <div className="rounded-card border border-line bg-surface p-6">
            <p className="font-medium text-ink">Nenhuma posição registrada</p>
            <p className="mt-1 text-sm text-ink-muted">
              Use o formulário acima para registrar a primeira posição manual.
            </p>
          </div>
        ) : null}

        {positions.length > 0 ? (
          <ul className="space-y-3">
            {positions.map((position) => (
              <PositionRow
                key={position.id}
                position={position}
                onChanged={() => void invalidate()}
              />
            ))}
          </ul>
        ) : null}
      </section>
    </div>
  )
}

function PositionRow({
  position,
  onChanged,
}: {
  readonly position: InvestmentPosition
  readonly onChanged: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const update = useMutation({
    mutationFn: (input: InvestmentPositionInput) => financeApi.updatePosition(position.id, input),
    onSuccess: () => {
      setEditing(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível salvar a posição.'))
    },
  })

  const remove = useMutation({
    mutationFn: () => financeApi.deletePosition(position.id),
    onSuccess: () => {
      setConfirming(false)
      setError(null)
      onChanged()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível excluir a posição.'))
    },
  })

  return (
    <li className="space-y-3 rounded-card border border-line bg-surface p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <p className="font-medium text-ink">
            {position.assetName}
            {position.ticker ? (
              <span className="ml-2 text-xs font-semibold tracking-wide text-ink-muted">
                {position.ticker}
              </span>
            ) : null}
          </p>
          <p className="text-sm text-ink-muted">
            {BROKER_LABELS[position.broker] ?? position.broker} ·{' '}
            {formatQuantity(position.quantity)} × {formatReferencePrice(position.referencePrice)}
          </p>
          <p className="text-sm text-ink-muted">
            Data de referência: {formatReferenceDate(position.referenceDate)}
          </p>
          {position.notes ? <p className="text-sm text-ink">{position.notes}</p> : null}
        </div>

        <div className="flex flex-col items-start gap-2 sm:items-end">
          <p className="numeric text-lg font-semibold text-ink">{formatMoney(position.total)}</p>
          <p className="text-xs text-ink-muted">Valor da posição</p>
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
        <div className="rounded-card border border-line bg-surface-muted p-3">
          <PositionForm
            initial={position}
            pending={update.isPending}
            submitLabel="Salvar posição"
            onSubmit={(input) => update.mutate(input)}
            onCancel={() => setEditing(false)}
          />
        </div>
      ) : null}

      {confirming ? (
        <div className="space-y-2 rounded-card border border-warning/40 bg-warning/5 p-3">
          <p className="text-sm text-ink">
            Excluir esta posição? O registro manual será removido e não entrará mais no resumo.
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-expense/50 bg-expense/5 px-3 text-sm font-semibold text-expense disabled:opacity-60"
            >
              Confirmar exclusão
            </button>
          </div>
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
