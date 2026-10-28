import { useId, useState, type FormEvent } from 'react'

import type { InvestmentPosition, InvestmentPositionInput } from '@/features/integration/api'

type Broker = InvestmentPosition['broker']
type ValueMode = 'price' | 'total'

const BROKERS: ReadonlyArray<{ value: Broker; label: string }> = [
  { value: 'XP', label: 'XP Investimentos' },
  { value: 'SANTANDER', label: 'Santander Corretora' },
  { value: 'OTHER', label: 'Outra corretora' },
]

interface PositionFormProps {
  readonly initial?: InvestmentPosition
  readonly pending: boolean
  readonly submitLabel: string
  readonly onSubmit: (input: InvestmentPositionInput) => void
  readonly onCancel?: () => void
}

function normalizeDecimal(value: string): string {
  return value.trim().replace(',', '.')
}

function isPositive(value: string): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}

/**
 * Formulário de posição manual.
 *
 * <p>O usuário informa o preço unitário OU o valor total; o servidor deriva o outro campo. Nada é
 * arredondado no cliente, e nenhum campo sugere cotação de mercado.
 */
export function PositionForm({
  initial,
  pending,
  submitLabel,
  onSubmit,
  onCancel,
}: PositionFormProps) {
  const baseId = useId()
  const [broker, setBroker] = useState<Broker>(initial?.broker ?? 'XP')
  const [assetName, setAssetName] = useState(initial?.assetName ?? '')
  const [ticker, setTicker] = useState(initial?.ticker ?? '')
  const [quantity, setQuantity] = useState(initial?.quantity ?? '')
  const [mode, setMode] = useState<ValueMode>('price')
  const [referencePrice, setReferencePrice] = useState(initial?.referencePrice ?? '')
  const [totalValue, setTotalValue] = useState('')
  const [referenceDate, setReferenceDate] = useState(initial?.referenceDate ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [excludeFromWealth, setExcludeFromWealth] = useState(initial?.excludeFromWealth ?? false)
  const [error, setError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const normalizedQuantity = normalizeDecimal(quantity)
    if (!assetName.trim()) {
      setError('Informe o nome do ativo.')
      return
    }
    if (!isPositive(normalizedQuantity)) {
      setError('Informe a quantidade.')
      return
    }
    if (mode === 'price' && !isPositive(normalizeDecimal(referencePrice))) {
      setError('Informe o preço unitário.')
      return
    }
    if (mode === 'total' && !isPositive(normalizeDecimal(totalValue))) {
      setError('Informe o valor total.')
      return
    }
    if (!referenceDate) {
      setError('Informe a data de referência.')
      return
    }

    const input: InvestmentPositionInput = {
      broker,
      assetName: assetName.trim(),
      quantity: normalizedQuantity,
      referenceDate,
      ...(mode === 'price'
        ? { referencePrice: normalizeDecimal(referencePrice) }
        : { totalValue: normalizeDecimal(totalValue) }),
      ...(ticker.trim() ? { ticker: ticker.trim() } : {}),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
      excludeFromWealth,
    }
    onSubmit(input)
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm" htmlFor={`${baseId}-broker`}>
          <span className="font-medium text-ink">Corretora</span>
          <select
            id={`${baseId}-broker`}
            value={broker}
            onChange={(event) => setBroker(event.target.value as Broker)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            {BROKERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-asset`}>
          <span className="font-medium text-ink">Ativo</span>
          <input
            id={`${baseId}-asset`}
            type="text"
            value={assetName}
            onChange={(event) => setAssetName(event.target.value)}
            maxLength={120}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-ticker`}>
          <span className="font-medium text-ink">Ticker</span>
          <input
            id={`${baseId}-ticker`}
            type="text"
            value={ticker}
            onChange={(event) => setTicker(event.target.value)}
            maxLength={20}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-quantity`}>
          <span className="font-medium text-ink">Quantidade</span>
          <input
            id={`${baseId}-quantity`}
            type="text"
            inputMode="decimal"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-ink">Forma de informar o valor</legend>
        <div className="flex flex-wrap gap-4 text-sm text-ink">
          <label className="flex items-center gap-2">
            <input
              type="radio"
              name={`${baseId}-mode`}
              checked={mode === 'price'}
              onChange={() => setMode('price')}
            />
            Preço unitário
          </label>
          <label className="flex items-center gap-2">
            <input
              type="radio"
              name={`${baseId}-mode`}
              checked={mode === 'total'}
              onChange={() => setMode('total')}
            />
            Valor total
          </label>
        </div>
      </fieldset>

      <div className="grid gap-3 sm:grid-cols-2">
        {mode === 'price' ? (
          <label className="block text-sm" htmlFor={`${baseId}-price`}>
            <span className="font-medium text-ink">Preço unitário informado</span>
            <input
              id={`${baseId}-price`}
              type="text"
              inputMode="decimal"
              value={referencePrice}
              onChange={(event) => setReferencePrice(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        ) : (
          <label className="block text-sm" htmlFor={`${baseId}-total`}>
            <span className="font-medium text-ink">Valor total informado</span>
            <input
              id={`${baseId}-total`}
              type="text"
              inputMode="decimal"
              value={totalValue}
              onChange={(event) => setTotalValue(event.target.value)}
              className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
            />
          </label>
        )}

        <label className="block text-sm" htmlFor={`${baseId}-date`}>
          <span className="font-medium text-ink">Data de referência</span>
          <input
            id={`${baseId}-date`}
            type="date"
            value={referenceDate}
            onChange={(event) => setReferenceDate(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>
      </div>

      <label className="flex items-start gap-3 rounded-card border border-line bg-surface-muted p-3 text-sm">
        <input
          type="checkbox"
          checked={excludeFromWealth}
          onChange={(event) => setExcludeFromWealth(event.target.checked)}
          className="mt-1 size-4"
        />
        <span>
          <span className="block font-medium text-ink">Não contar no patrimônio</span>
          <span className="mt-1 block text-xs leading-relaxed text-ink-muted">
            A posição continua visível, mas não entra no total patrimonial. Use quando ela já
            estiver representada por uma posição importada.
          </span>
        </span>
      </label>

      <label className="block text-sm" htmlFor={`${baseId}-notes`}>
        <span className="font-medium text-ink">Observações</span>
        <textarea
          id={`${baseId}-notes`}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          maxLength={2000}
          rows={2}
          className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
        />
      </label>

      {error ? (
        <p role="alert" className="text-sm text-expense">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={pending}
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover disabled:opacity-60"
        >
          {pending ? 'Salvando…' : submitLabel}
        </button>
        {onCancel ? (
          <button
            type="button"
            onClick={onCancel}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted"
          >
            Cancelar
          </button>
        ) : null}
      </div>
    </form>
  )
}
