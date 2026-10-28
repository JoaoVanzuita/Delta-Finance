import { useState } from 'react'

import { useMutation } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type InvestmentAssetClassInput,
  type InvestmentHolding,
} from '@/features/integration/api'

import { ASSET_CLASS_LABELS } from '../labels'

interface HoldingSettingsFormProps {
  readonly holding: InvestmentHolding
  readonly onChanged: () => void
}

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

/** Ajustes do usuário sobre uma posição importada: custo e classificação. */
export function HoldingSettingsForm({ holding, onChanged }: HoldingSettingsFormProps) {
  const [costBasis, setCostBasis] = useState(holding.manualCostBasis ?? '')
  const [assetClass, setAssetClass] = useState(holding.userAssetClass ?? '')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const costMutation = useMutation({
    mutationFn: () =>
      financeApi.updateInvestmentCostBasis(holding.id, {
        costBasis: costBasis.trim() === '' ? null : costBasis.trim(),
      }),
    onSuccess: () => {
      setError(null)
      setMessage('Custo informado atualizado.')
      onChanged()
    },
    onError: (caught) => {
      setMessage(null)
      setError(messageFrom(caught, 'Não foi possível salvar o custo informado.'))
    },
  })

  const classMutation = useMutation({
    mutationFn: () =>
      financeApi.updateInvestmentAssetClass(holding.id, {
        assetClass:
          assetClass === '' ? null : (assetClass as InvestmentAssetClassInput['assetClass']),
      }),
    onSuccess: () => {
      setError(null)
      setMessage('Classificação atualizada.')
      onChanged()
    },
    onError: (caught) => {
      setMessage(null)
      setError(messageFrom(caught, 'Não foi possível salvar a classificação.'))
    },
  })

  return (
    <div className="mt-3 space-y-3 rounded-card border border-line bg-surface p-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Custo informado (BRL)
          <input
            value={costBasis}
            onChange={(event) => setCostBasis(event.target.value)}
            inputMode="decimal"
            placeholder={holding.amountOriginal ?? 'Ex.: 1000.00'}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Classificação
          <select
            value={assetClass}
            onChange={(event) => setAssetClass(event.target.value)}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
          >
            <option value="">Automática (provedor/código)</option>
            {Object.entries(ASSET_CLASS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="text-xs leading-relaxed text-ink-muted">
        O custo informado não altera o valor do provedor. Deixe vazio para voltar ao custo original,
        quando existir. A classificação vazia volta à regra automática.
      </p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={costMutation.isPending}
          onClick={() => costMutation.mutate()}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
        >
          Salvar custo
        </button>
        <button
          type="button"
          disabled={classMutation.isPending}
          onClick={() => classMutation.mutate()}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted disabled:opacity-60"
        >
          Salvar classificação
        </button>
      </div>
      {message ? (
        <p role="status" aria-live="polite" className="text-sm text-income">
          {message}
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}
    </div>
  )
}
