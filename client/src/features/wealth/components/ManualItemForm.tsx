import { useId, useState, type FormEvent } from 'react'

import type { WealthManualItem, WealthManualItemInput } from '@/features/integration/api'

type Kind = WealthManualItem['kind']

interface ManualItemFormProps {
  readonly initial?: WealthManualItem
  readonly pending: boolean
  readonly submitLabel: string
  readonly onSubmit: (input: WealthManualItemInput) => void
  readonly onCancel?: () => void
}

function normalizeDecimal(value: string): string {
  return value.trim().replace(',', '.')
}

function isNonNegative(value: string): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0
}

/** Formulário de ativo/passivo manual; o valor é sempre magnitude positiva. */
export function ManualItemForm({
  initial,
  pending,
  submitLabel,
  onSubmit,
  onCancel,
}: ManualItemFormProps) {
  const baseId = useId()
  const [kind, setKind] = useState<Kind>(initial?.kind ?? 'ASSET')
  const [type, setType] = useState(initial?.type ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [referenceValue, setReferenceValue] = useState(initial?.referenceValue ?? '')
  const [referenceDate, setReferenceDate] = useState(initial?.referenceDate ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!type.trim()) {
      setError('Informe o tipo do item.')
      return
    }
    if (!description.trim()) {
      setError('Informe a descrição.')
      return
    }
    const value = normalizeDecimal(referenceValue)
    if (!isNonNegative(value)) {
      setError('Informe o valor de referência.')
      return
    }
    if (!referenceDate) {
      setError('Informe a data de referência.')
      return
    }

    onSubmit({
      kind,
      type: type.trim(),
      description: description.trim(),
      referenceValue: value,
      referenceDate,
      ...(notes.trim() ? { notes: notes.trim() } : {}),
    })
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm" htmlFor={`${baseId}-kind`}>
          <span className="font-medium text-ink">Natureza</span>
          <select
            id={`${baseId}-kind`}
            value={kind}
            onChange={(event) => setKind(event.target.value as Kind)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            <option value="ASSET">Ativo</option>
            <option value="LIABILITY">Passivo</option>
          </select>
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-type`}>
          <span className="font-medium text-ink">Tipo do item</span>
          <input
            id={`${baseId}-type`}
            type="text"
            value={type}
            onChange={(event) => setType(event.target.value)}
            maxLength={60}
            placeholder="Ex.: IMOVEL, VEICULO, EMPRESTIMO"
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-description`}>
          <span className="font-medium text-ink">Descrição</span>
          <input
            id={`${baseId}-description`}
            type="text"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={200}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-value`}>
          <span className="font-medium text-ink">Valor de referência</span>
          <input
            id={`${baseId}-value`}
            type="text"
            inputMode="decimal"
            value={referenceValue}
            onChange={(event) => setReferenceValue(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

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
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-on-primary transition-colors hover:bg-primary-hover disabled:opacity-60"
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
