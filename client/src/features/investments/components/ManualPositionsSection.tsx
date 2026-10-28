import { useState } from 'react'

import { useMutation, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type InvestmentPosition,
  type InvestmentPositionInput,
} from '@/features/integration/api'

import { formatMoney, formatQuantity, formatReferenceDate, formatReferencePrice } from '../format'
import { PositionForm } from './PositionForm'

const BROKER_LABELS: Record<string, string> = {
  XP: 'XP Investimentos',
  SANTANDER: 'Santander Corretora',
  OTHER: 'Outra corretora',
}

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

interface ManualPositionsSectionProps {
  readonly positions: readonly InvestmentPosition[]
  readonly loading: boolean
  readonly error: boolean
  readonly duplicateIds: ReadonlySet<string>
  readonly onChanged: () => void
}

/** Posições manuais complementares, mantidas separadas das importadas. */
export function ManualPositionsSection({
  positions,
  loading,
  error,
  duplicateIds,
  onChanged,
}: ManualPositionsSectionProps) {
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [formVersion, setFormVersion] = useState(0)

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['investments', 'positions'] })
    void queryClient.invalidateQueries({ queryKey: ['investments', 'overview'] })
    onChanged()
  }

  const create = useMutation({
    mutationFn: (input: InvestmentPositionInput) => financeApi.createPosition(input),
    onSuccess: () => {
      setFormError(null)
      setMessage('Posição manual adicionada.')
      setFormVersion((version) => version + 1)
      invalidate()
    },
    onError: (caught) => {
      setMessage(null)
      setFormError(messageFrom(caught, 'Não foi possível adicionar a posição.'))
    },
  })

  return (
    <div className="space-y-4">
      <p className="text-sm leading-relaxed text-ink-muted">
        Use esta seção apenas para o que não veio do provedor. O total é quantidade × preço unitário
        informado; não é cotação de mercado.
      </p>

      {message ? (
        <p
          role="status"
          aria-live="polite"
          className="rounded-card border border-income/30 bg-income/5 p-3 text-sm text-ink"
        >
          {message}
        </p>
      ) : null}
      {formError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-3 text-sm text-ink"
        >
          {formError}
        </p>
      ) : null}

      <div className="rounded-card border border-line bg-surface p-4">
        <h3 className="text-sm font-semibold text-ink">Nova posição manual</h3>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          Informe o preço unitário ou o valor total. Com apenas o valor total, o preço unitário é
          derivado pelo servidor com 8 casas decimais.
        </p>
        <div className="mt-3">
          <PositionForm
            key={formVersion}
            pending={create.isPending}
            submitLabel="Adicionar posição"
            onSubmit={(input) => create.mutate(input)}
          />
        </div>
      </div>

      {loading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando posições manuais…
        </p>
      ) : null}
      {error ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          Não foi possível carregar as posições manuais.
        </p>
      ) : null}
      {!loading && !error && positions.length === 0 ? (
        <div className="rounded-card border border-line bg-surface p-4">
          <p className="font-medium text-ink">Nenhuma posição manual</p>
          <p className="mt-1 text-sm text-ink-muted">
            As posições importadas continuam disponíveis na seção acima.
          </p>
        </div>
      ) : null}
      {positions.length > 0 ? (
        <ul className="space-y-3">
          {positions.map((position) => (
            <PositionRow
              key={position.id}
              position={position}
              duplicate={duplicateIds.has(position.id)}
              onChanged={invalidate}
            />
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function PositionRow({
  position,
  duplicate,
  onChanged,
}: {
  readonly position: InvestmentPosition
  readonly duplicate: boolean
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
          {position.excludeFromWealth ? (
            <p className="rounded-card border border-line bg-surface-muted p-2 text-xs leading-relaxed text-ink-muted">
              Esta posição não conta no patrimônio.
            </p>
          ) : null}
          {duplicate ? (
            <p className="rounded-card border border-warning/40 bg-warning/5 p-2 text-xs leading-relaxed text-ink">
              Já existe uma posição importada com este código. As duas podem entrar no total;
              confira se esta posição manual deve ser excluída.
            </p>
          ) : null}
        </div>

        <div className="flex flex-col items-start gap-2 sm:items-end">
          <p className="numeric text-lg font-semibold text-ink">{formatMoney(position.total)}</p>
          <p className="text-xs text-ink-muted">Valor informado</p>
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
