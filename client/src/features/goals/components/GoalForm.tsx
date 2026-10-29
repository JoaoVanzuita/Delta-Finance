import { useId, useState, type FormEvent } from 'react'

import { useQuery } from '@tanstack/react-query'

import { financeApi } from '@/features/integration/api'
import type { Goal, GoalAllocationInput, GoalInput } from '@/features/integration/api'

type GoalType = Goal['type']
type ProgressRule = Goal['progressRule']
type SourceType = GoalAllocationInput['sourceType']
type AllocationMode = GoalAllocationInput['allocationMode']

interface AllocationDraft {
  readonly key: number
  readonly sourceType: SourceType
  readonly sourceId: string
  readonly mode: AllocationMode
  readonly value: string
}

interface GoalFormProps {
  readonly initial?: Goal
  readonly pending: boolean
  readonly submitLabel: string
  readonly onSubmit: (input: GoalInput) => void
  readonly onCancel?: () => void
}

function normalizeDecimal(value: string): string {
  return value.trim().replace(',', '.')
}

function isPositive(value: string): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number > 0
}

function isNonNegative(value: string): boolean {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0
}

/** Formulário de meta com alocações explícitas; não oferece qualquer ação financeira. */
export function GoalForm({ initial, pending, submitLabel, onSubmit, onCancel }: GoalFormProps) {
  const baseId = useId()
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: financeApi.accounts })
  const investmentsQuery = useQuery({
    queryKey: ['investments', 'goal-sources'],
    queryFn: () => financeApi.investmentHoldings('ACTIVE'),
  })
  const wealthQuery = useQuery({
    queryKey: ['wealth', 'manual', 'goal-sources'],
    queryFn: financeApi.wealthManualItems,
  })
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [notes, setNotes] = useState(initial?.notes ?? '')
  const [type, setType] = useState<GoalType>(initial?.type ?? 'EMERGENCY_FUND')
  const [targetAmount, setTargetAmount] = useState(initial?.targetAmount ?? '')
  const [targetDate, setTargetDate] = useState(initial?.targetDate ?? '')
  const [progressRule, setProgressRule] = useState<ProgressRule>(
    initial?.progressRule ?? 'ALLOCATIONS',
  )
  const [allocations, setAllocations] = useState<AllocationDraft[]>(() =>
    (initial?.allocations ?? []).map((allocation, index) => ({
      key: index,
      sourceType: allocation.sourceType,
      sourceId: allocation.sourceId ?? '',
      mode: allocation.allocationMode,
      value:
        allocation.allocationMode === 'FIXED_AMOUNT'
          ? (allocation.allocatedAmount ?? '')
          : (allocation.percentage ?? ''),
    })),
  )
  const [nextKey, setNextKey] = useState((initial?.allocations?.length ?? 0) + 1)
  const [error, setError] = useState<string | null>(null)
  const hasNonBrlAccounts = (accountsQuery.data?.items ?? []).some(
    (account) => account.currency !== 'BRL',
  )

  function updateAllocation(key: number, patch: Partial<AllocationDraft>) {
    setAllocations((current) =>
      current.map((allocation) =>
        allocation.key === key ? { ...allocation, ...patch } : allocation,
      ),
    )
  }

  function sourceOptions(sourceType: SourceType): { id: string; label: string }[] {
    if (sourceType === 'ACCOUNT') {
      return (accountsQuery.data?.items ?? [])
        .filter((account) => account.currency === 'BRL')
        .map((account) => ({
          id: account.id,
          label: account.alias ?? account.institutionName ?? account.id,
        }))
    }
    if (sourceType === 'INVESTMENT') {
      return (investmentsQuery.data?.items ?? []).map((holding) => ({
        id: holding.id,
        label: holding.code
          ? `${holding.code} · ${holding.name ?? 'Posição importada'}`
          : (holding.name ?? 'Posição importada'),
      }))
    }
    if (sourceType === 'MANUAL_ASSET') {
      return (wealthQuery.data?.items ?? [])
        .filter((item) => item.kind === 'ASSET')
        .map((item) => ({ id: item.id, label: item.description }))
    }
    return []
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!name.trim()) {
      setError('Informe o nome da meta.')
      return
    }
    const target = normalizeDecimal(targetAmount)
    if (!isPositive(target)) {
      setError('Informe um valor-alvo maior que zero.')
      return
    }

    const allocationInputs: GoalAllocationInput[] = []
    if (progressRule !== 'NET_WORTH') {
      for (const allocation of allocations) {
        const value = normalizeDecimal(allocation.value)
        const sourceId = allocation.sourceId.trim()
        if (allocation.sourceType !== 'MANUAL' && !sourceId) {
          setError('Informe a fonte de cada alocação.')
          return
        }
        if (
          (allocation.mode === 'FIXED_AMOUNT' && !isNonNegative(value)) ||
          (allocation.mode === 'PERCENTAGE' && !isNonNegative(value))
        ) {
          setError('Informe o valor de cada alocação.')
          return
        }
        allocationInputs.push({
          sourceType: allocation.sourceType,
          ...(allocation.sourceType === 'MANUAL' ? {} : { sourceId }),
          allocationMode: allocation.mode,
          ...(allocation.mode === 'FIXED_AMOUNT'
            ? { allocatedAmount: value }
            : { percentage: value }),
        })
      }
    }

    onSubmit({
      name: name.trim(),
      type,
      targetAmount: target,
      progressRule,
      ...(description.trim() ? { description: description.trim() } : {}),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
      ...(targetDate ? { targetDate } : {}),
      ...(allocationInputs.length > 0 ? { allocations: allocationInputs } : {}),
    })
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm" htmlFor={`${baseId}-name`}>
          <span className="font-medium text-ink">Nome da meta</span>
          <input
            id={`${baseId}-name`}
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={120}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-type`}>
          <span className="font-medium text-ink">Tipo</span>
          <select
            id={`${baseId}-type`}
            value={type}
            onChange={(event) => setType(event.target.value as GoalType)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            <option value="EMERGENCY_FUND">Reserva de emergência</option>
            <option value="TRAVEL">Viagem</option>
            <option value="PLANNED_PURCHASE">Compra planejada</option>
            <option value="NET_WORTH">Patrimônio líquido</option>
          </select>
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-target`}>
          <span className="font-medium text-ink">Valor-alvo</span>
          <input
            id={`${baseId}-target`}
            type="text"
            inputMode="decimal"
            value={targetAmount}
            onChange={(event) => setTargetAmount(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-date`}>
          <span className="font-medium text-ink">Data-alvo</span>
          <input
            id={`${baseId}-date`}
            type="date"
            value={targetDate}
            onChange={(event) => setTargetDate(event.target.value)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          />
        </label>

        <label className="block text-sm" htmlFor={`${baseId}-rule`}>
          <span className="font-medium text-ink">Regra de progresso</span>
          <select
            id={`${baseId}-rule`}
            value={progressRule}
            onChange={(event) => setProgressRule(event.target.value as ProgressRule)}
            className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
          >
            <option value="ALLOCATIONS">Alocações explícitas</option>
            <option value="NET_WORTH">Patrimônio líquido</option>
          </select>
        </label>
      </div>

      <label className="block text-sm" htmlFor={`${baseId}-description`}>
        <span className="font-medium text-ink">Descrição</span>
        <textarea
          id={`${baseId}-description`}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          maxLength={500}
          rows={2}
          className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
        />
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

      {progressRule !== 'NET_WORTH' ? (
        <fieldset className="space-y-3">
          <legend className="text-sm font-medium text-ink">Alocações explícitas</legend>
          <p className="text-xs leading-relaxed text-ink-muted">
            Percentuais usam o valor-alvo, com arredondamento para baixo e resíduo na última
            alocação. Percentuais abaixo de 100% deixam saldo não alocado visível.
          </p>
          {hasNonBrlAccounts ? (
            <p className="text-xs leading-relaxed text-ink-muted">
              Contas em moeda diferente de BRL não são oferecidas como fonte e ficam fora do cálculo
              do progresso.
            </p>
          ) : null}
          {allocations.length === 0 ? (
            <p className="text-sm text-ink-muted">Nenhuma alocação adicionada.</p>
          ) : (
            <ul className="space-y-3">
              {allocations.map((allocation, index) => (
                <li
                  key={allocation.key}
                  className="grid gap-2 rounded-card border border-line bg-surface-muted p-3 sm:grid-cols-2"
                >
                  <label className="block text-sm" htmlFor={`${baseId}-source-${index}`}>
                    <span className="font-medium text-ink">Origem da alocação</span>
                    <select
                      id={`${baseId}-source-${index}`}
                      value={allocation.sourceType}
                      onChange={(event) =>
                        updateAllocation(allocation.key, {
                          sourceType: event.target.value as SourceType,
                          sourceId: '',
                        })
                      }
                      className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                    >
                      <option value="MANUAL">Manual</option>
                      <option value="ACCOUNT">Conta</option>
                      <option value="INVESTMENT">Investimento</option>
                      <option value="MANUAL_ASSET">Ativo manual</option>
                    </select>
                  </label>

                  {allocation.sourceType !== 'MANUAL' ? (
                    <label className="block text-sm" htmlFor={`${baseId}-sourceid-${index}`}>
                      <span className="font-medium text-ink">Fonte</span>
                      <select
                        id={`${baseId}-sourceid-${index}`}
                        value={allocation.sourceId}
                        onChange={(event) =>
                          updateAllocation(allocation.key, { sourceId: event.target.value })
                        }
                        className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                      >
                        <option value="">Selecione…</option>
                        {sourceOptions(allocation.sourceType).map((option) => (
                          <option key={option.id} value={option.id}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : null}

                  <label className="block text-sm" htmlFor={`${baseId}-mode-${index}`}>
                    <span className="font-medium text-ink">Modo da alocação</span>
                    <select
                      id={`${baseId}-mode-${index}`}
                      value={allocation.mode}
                      onChange={(event) =>
                        updateAllocation(allocation.key, {
                          mode: event.target.value as AllocationMode,
                          value: '',
                        })
                      }
                      className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                    >
                      <option value="FIXED_AMOUNT">Valor fixo</option>
                      <option value="PERCENTAGE">Percentual</option>
                    </select>
                  </label>

                  <label className="block text-sm" htmlFor={`${baseId}-value-${index}`}>
                    <span className="font-medium text-ink">
                      {allocation.mode === 'FIXED_AMOUNT' ? 'Valor fixo' : 'Percentual da alocação'}
                    </span>
                    <input
                      id={`${baseId}-value-${index}`}
                      type="text"
                      inputMode="decimal"
                      value={allocation.value}
                      onChange={(event) =>
                        updateAllocation(allocation.key, { value: event.target.value })
                      }
                      className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink"
                    />
                  </label>

                  <div className="sm:col-span-2">
                    <button
                      type="button"
                      onClick={() =>
                        setAllocations((current) =>
                          current.filter((item) => item.key !== allocation.key),
                        )
                      }
                      className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink hover:bg-surface-muted"
                    >
                      Remover alocação
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <button
            type="button"
            onClick={() => {
              setAllocations((current) => [
                ...current,
                {
                  key: nextKey,
                  sourceType: 'MANUAL',
                  sourceId: '',
                  mode: 'FIXED_AMOUNT',
                  value: '',
                },
              ])
              setNextKey((value) => value + 1)
            }}
            className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted"
          >
            Adicionar alocação
          </button>
        </fieldset>
      ) : (
        <p className="text-sm text-ink-muted">
          O progresso acompanha o patrimônio líquido atual. Nenhuma alocação explícita é usada.
        </p>
      )}

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
