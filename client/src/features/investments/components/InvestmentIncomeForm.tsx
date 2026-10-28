import { useId, useState, type FormEvent } from 'react'

import { useMutation } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import {
  financeApi,
  type InvestmentHolding,
  type InvestmentIncomeInput,
  type InvestmentIncomeItem,
  type InvestmentPosition,
} from '@/features/integration/api'

const INCOME_TYPES = [
  { value: 'DIVIDEND', label: 'Dividendo' },
  { value: 'INTEREST', label: 'Juro/rendimento' },
  { value: 'JCP', label: 'JCP' },
  { value: 'RENT', label: 'Aluguel de FII' },
  { value: 'COUPON', label: 'Cupom' },
  { value: 'OTHER', label: 'Outro' },
] as const

function messageFrom(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date())
}

interface InvestmentIncomeFormProps {
  readonly holdings: readonly InvestmentHolding[]
  readonly positions: readonly InvestmentPosition[]
  readonly initial?: InvestmentIncomeItem
  readonly onSaved: () => void
  readonly onCancel?: () => void
}

/** Formulário de provento manual vinculado a uma posição importada ou manual. */
export function InvestmentIncomeForm({
  holdings,
  positions,
  initial,
  onSaved,
  onCancel,
}: InvestmentIncomeFormProps) {
  const baseId = useId()
  const [targetKey, setTargetKey] = useState(
    initial ? `${initial.targetType}:${initial.investmentId}` : '',
  )
  const [incomeType, setIncomeType] = useState<string>(initial?.incomeType ?? 'DIVIDEND')
  const [incomeDate, setIncomeDate] = useState(initial?.date ?? today())
  const [amount, setAmount] = useState(initial?.amount ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [error, setError] = useState<string | null>(null)

  const targets = [
    ...holdings.map((holding) => ({
      key: `PROVIDER:${holding.id}`,
      label: `${holding.name ?? 'Posição importada'}${holding.code ? ` (${holding.code})` : ''} · importado`,
    })),
    ...positions.map((position) => ({
      key: `MANUAL:${position.id}`,
      label: `${position.assetName}${position.ticker ? ` (${position.ticker})` : ''} · manual`,
    })),
  ]

  const save = useMutation({
    mutationFn: (input: InvestmentIncomeInput) =>
      initial
        ? financeApi.updateInvestmentIncome(initial.id ?? '', input)
        : financeApi.createInvestmentIncome(input),
    onSuccess: () => {
      setError(null)
      if (!initial) {
        setTargetKey('')
        setAmount('')
        setDescription('')
        setIncomeDate(today())
      }
      onSaved()
    },
    onError: (caught) => {
      setError(messageFrom(caught, 'Não foi possível salvar o provento.'))
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const separator = targetKey.indexOf(':')
    if (separator <= 0) {
      setError('Selecione a posição do provento.')
      return
    }
    const targetType = targetKey.slice(0, separator)
    const targetId = targetKey.slice(separator + 1)
    save.mutate({
      providerInvestmentId: targetType === 'PROVIDER' ? targetId : undefined,
      investmentPositionId: targetType === 'MANUAL' ? targetId : undefined,
      incomeType: incomeType as InvestmentIncomeInput['incomeType'],
      incomeDate,
      amount: amount.trim(),
      description: description.trim() || undefined,
    })
  }

  if (targets.length === 0) {
    return (
      <p className="rounded-card border border-line bg-surface-muted p-3 text-sm text-ink-muted">
        Nenhuma posição disponível para receber provento. Importe ou cadastre uma posição primeiro.
      </p>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-3 rounded-card border border-line bg-surface p-4"
      aria-labelledby={`${baseId}-title`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 id={`${baseId}-title`} className="text-sm font-semibold text-ink">
            {initial ? 'Editar provento manual' : 'Registrar provento manual'}
          </h3>
          <p className="mt-1 text-xs leading-relaxed text-ink-muted">
            Use quando o provedor não informar dividendos, JCP, aluguel de FII ou cupons.
          </p>
        </div>
        {initial && onCancel ? (
          <button
            type="button"
            onClick={onCancel}
            className="inline-flex min-h-11 items-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
          >
            Cancelar edição
          </button>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted sm:col-span-2">
          Posição
          <select
            value={targetKey}
            onChange={(event) => setTargetKey(event.target.value)}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
            required
          >
            <option value="">Selecione</option>
            {targets.map((target) => (
              <option key={target.key} value={target.key}>
                {target.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Tipo
          <select
            value={incomeType}
            onChange={(event) => setIncomeType(event.target.value)}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
          >
            {INCOME_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Data
          <input
            type="date"
            value={incomeDate}
            max={today()}
            onChange={(event) => setIncomeDate(event.target.value)}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
            required
          />
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Valor (BRL)
          <input
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            inputMode="decimal"
            placeholder="Ex.: 12.50"
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
            required
          />
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
          Descrição (opcional)
          <input
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={500}
            className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
          />
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={save.isPending}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface hover:bg-primary-hover disabled:opacity-60"
        >
          {initial ? 'Salvar provento' : 'Registrar provento'}
        </button>
      </div>
      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}
    </form>
  )
}
