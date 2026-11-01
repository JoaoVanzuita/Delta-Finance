import { useRef, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  amountRangeError,
  dateRangeError,
  normalizeAmountInput,
  type TransactionFilterState,
} from '@/features/transactions/filters'
import type { Account, Category } from '@/features/integration/api'

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pendente' },
  { value: 'POSTED', label: 'Efetivada' },
  { value: 'RECONCILED', label: 'Reconciliada' },
  { value: 'SOURCE_REMOVED', label: 'Removida na origem' },
]

const TYPE_OPTIONS = [
  { value: 'DEBIT', label: 'Despesa' },
  { value: 'CREDIT', label: 'Receita' },
  { value: 'TRANSFER', label: 'Transferência' },
  { value: 'REFUND', label: 'Reembolso' },
  { value: 'CHARGEBACK', label: 'Chargeback' },
  { value: 'ADJUSTMENT', label: 'Ajuste' },
]

const ORIGIN_OPTIONS = [
  { value: 'IMPORTED', label: 'Importada' },
  { value: 'MANUAL', label: 'Manual' },
]

export function TransactionFiltersDrawer({
  open,
  onClose,
  value,
  accounts,
  categories,
  onApply,
}: {
  readonly open: boolean
  readonly onClose: () => void
  readonly value: TransactionFilterState
  readonly accounts: readonly Account[]
  readonly categories: readonly Category[]
  readonly onApply: (filters: TransactionFilterState) => void
}) {
  const [draft, setDraft] = useState(value)
  const [error, setError] = useState<string | undefined>(undefined)
  const accountRef = useRef<HTMLSelectElement>(null)

  function update<K extends keyof TransactionFilterState>(key: K, next: TransactionFilterState[K]) {
    setDraft((current) => ({ ...current, [key]: next }))
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const amountError = amountRangeError(draft.minAmount, draft.maxAmount)
    const rangeError = dateRangeError(draft.from, draft.to)
    if (amountError || rangeError) {
      setError(amountError ?? rangeError)
      return
    }
    onApply({
      ...draft,
      minAmount: draft.minAmount ? normalizeAmountInput(draft.minAmount) : undefined,
      maxAmount: draft.maxAmount ? normalizeAmountInput(draft.maxAmount) : undefined,
    })
    onClose()
  }

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Filtros de transações"
      description="Todos os filtros são aplicados ao carregar a lista e preservados na URL."
      width="md"
      initialFocusRef={accountRef}
      footer={
        <div className="flex flex-wrap justify-between gap-2">
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              onApply({})
              onClose()
            }}
          >
            Limpar filtros
          </Button>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="submit" form="transaction-filters-form">
              Aplicar filtros
            </Button>
          </div>
        </div>
      }
    >
      <form id="transaction-filters-form" noValidate className="space-y-4" onSubmit={submit}>
        {error ? (
          <p
            role="alert"
            className="rounded-card border border-expense/35 bg-expense-soft p-3 text-body-sm text-ink"
          >
            {error}
          </p>
        ) : null}

        <Field id="filter-account" label="Conta">
          <Select
            ref={accountRef}
            id="filter-account"
            value={draft.accountId ?? ''}
            onChange={(event) => update('accountId', event.target.value || undefined)}
          >
            <option value="">Todas</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.alias ?? account.institutionName ?? account.id}
              </option>
            ))}
          </Select>
        </Field>

        <Field id="filter-category" label="Categoria">
          <Select
            id="filter-category"
            value={draft.categoryId ?? ''}
            onChange={(event) => update('categoryId', event.target.value || undefined)}
          >
            <option value="">Todas</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="filter-status" label="Estado">
            <Select
              id="filter-status"
              value={draft.status ?? ''}
              onChange={(event) => update('status', event.target.value || undefined)}
            >
              <option value="">Todos</option>
              {STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field id="filter-type" label="Tipo">
            <Select
              id="filter-type"
              value={draft.type ?? ''}
              onChange={(event) => update('type', event.target.value || undefined)}
            >
              <option value="">Todos</option>
              {TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>

          <Field id="filter-origin" label="Origem">
            <Select
              id="filter-origin"
              value={draft.origin ?? ''}
              onChange={(event) => update('origin', event.target.value || undefined)}
            >
              <option value="">Todas</option>
              {ORIGIN_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="filter-from" label="De">
            <Input
              id="filter-from"
              type="date"
              value={draft.from ?? ''}
              max={draft.to || undefined}
              onChange={(event) => update('from', event.target.value || undefined)}
            />
          </Field>
          <Field id="filter-to" label="Até">
            <Input
              id="filter-to"
              type="date"
              value={draft.to ?? ''}
              min={draft.from || undefined}
              onChange={(event) => update('to', event.target.value || undefined)}
            />
          </Field>
          <Field id="filter-min" label="Valor mínimo (R$)">
            <Input
              id="filter-min"
              inputMode="decimal"
              value={draft.minAmount ?? ''}
              onChange={(event) => update('minAmount', event.target.value || undefined)}
            />
          </Field>
          <Field id="filter-max" label="Valor máximo (R$)">
            <Input
              id="filter-max"
              inputMode="decimal"
              value={draft.maxAmount ?? ''}
              onChange={(event) => update('maxAmount', event.target.value || undefined)}
            />
          </Field>
        </div>
      </form>
    </Drawer>
  )
}
