import { useState, type FormEvent, type ReactNode } from 'react'

import { CalendarDays, SlidersHorizontal, X } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import {
  UNCATEGORIZED_CATEGORY_ID,
  type DashboardFilters as DashboardFiltersValue,
  type DashboardPeriod,
} from '@/features/dashboard/api'
import type { Account, Category } from '@/features/integration/api'
import { formatDate, formatMoney } from '@/lib/format'

interface DashboardFiltersProps {
  readonly value: DashboardFiltersValue
  readonly period: DashboardPeriod
  readonly accounts: readonly Account[]
  readonly categories: readonly Category[]
  readonly onApply: (filters: DashboardFiltersValue) => void
}

const PERIOD_OPTIONS: ReadonlyArray<{ value: DashboardPeriod; label: string }> = [
  { value: 'WEEK', label: 'Semana' },
  { value: 'MONTH', label: 'Mês' },
  { value: 'YEAR', label: 'Ano' },
]

const TYPE_LABELS: Record<string, string> = {
  DEBIT: 'Despesa',
  CREDIT: 'Receita',
  REFUND: 'Reembolso',
  CHARGEBACK: 'Chargeback',
  ADJUSTMENT: 'Ajuste',
}

const ORIGIN_LABELS: Record<string, string> = {
  IMPORTED: 'Importada',
  MANUAL: 'Manual',
}

function normalizeAmount(value: string): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const withoutGrouping = trimmed.includes(',') ? trimmed.replace(/\./g, '') : trimmed
  const normalized = withoutGrouping.replace(',', '.')
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return undefined
  return normalized
}

function compareAmounts(left: string, right: string): number {
  return Number(left) - Number(right)
}

function dateRangeError(from?: string, to?: string): string | undefined {
  if (!from || !to) return undefined
  if (from > to) return 'A data inicial deve ser anterior à data final.'
  const start = Date.parse(`${from}T12:00:00Z`)
  const end = Date.parse(`${to}T12:00:00Z`)
  if (Number.isNaN(start) || Number.isNaN(end)) return 'Informe datas válidas.'
  if ((end - start) / 86_400_000 + 1 > 366) {
    return 'O intervalo máximo é de 366 dias. Estreite o período.'
  }
  return undefined
}

function amountRangeError(minAmount?: string, maxAmount?: string): string | undefined {
  if (!minAmount || !maxAmount) return undefined
  if (compareAmounts(minAmount, maxAmount) > 0) {
    return 'O valor mínimo não pode ser maior que o valor máximo.'
  }
  return undefined
}

function FilterChip({
  children,
  onRemove,
}: {
  readonly children: ReactNode
  readonly onRemove: () => void
}) {
  return (
    <span className="inline-flex min-h-11 items-center gap-1 rounded-pill border border-line bg-surface-muted py-1 pr-1 pl-3 text-caption font-medium text-ink sm:min-h-9">
      {children}
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Remover filtro ${typeof children === 'string' ? children : ''}`.trim()}
        className="inline-flex size-11 items-center justify-center rounded-pill text-ink-muted hover:bg-surface hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:size-7"
      >
        <X aria-hidden="true" className="size-3.5" />
      </button>
    </span>
  )
}

/**
 * Barra compacta de período e filtros do Dashboard.
 *
 * Período e intervalo ficam visíveis; conta, categoria, tipo, origem e faixa de
 * valor ficam em drawer. A URL é a fonte de verdade — aplicar/remover um filtro
 * navega, preservando voltar e recarregar.
 */
export function DashboardFilters({
  value,
  period,
  accounts,
  categories,
  onApply,
}: DashboardFiltersProps) {
  const [customOpen, setCustomOpen] = useState(Boolean(value.from || value.to))
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [fromDraft, setFromDraft] = useState(value.from ?? '')
  const [toDraft, setToDraft] = useState(value.to ?? '')
  const [advancedDraft, setAdvancedDraft] = useState(value)
  const [advancedError, setAdvancedError] = useState<string>()

  const dateError = dateRangeError(fromDraft || undefined, toDraft || undefined)
  const advancedDateError = dateRangeError(
    advancedDraft.from || undefined,
    advancedDraft.to || undefined,
  )
  const advancedAmountError = amountRangeError(
    advancedDraft.minAmount ? normalizeAmount(advancedDraft.minAmount) : undefined,
    advancedDraft.maxAmount ? normalizeAmount(advancedDraft.maxAmount) : undefined,
  )
  const visibleError = advancedAmountError ?? advancedDateError

  const accountNames = new Map(
    accounts.map((account) => [account.id, account.alias ?? account.institutionName ?? 'Conta']),
  )
  const categoryNames = new Map(categories.map((category) => [category.id, category.name] as const))
  categoryNames.set(UNCATEGORIZED_CATEGORY_ID, 'Sem categoria')

  const activeChips: Array<{ key: string; label: string; remove: () => void }> = []
  for (const accountId of value.accountIds ?? []) {
    activeChips.push({
      key: `account-${accountId}`,
      label: `Conta: ${accountNames.get(accountId) ?? 'Conta'}`,
      remove: () =>
        onApply({
          ...value,
          accountIds: (value.accountIds ?? []).filter((id) => id !== accountId),
        }),
    })
  }
  if (value.categoryId) {
    activeChips.push({
      key: 'category',
      label: `Categoria: ${categoryNames.get(value.categoryId) ?? 'Categoria'}`,
      remove: () => onApply({ ...value, categoryId: undefined }),
    })
  }
  if (value.type) {
    activeChips.push({
      key: 'type',
      label: `Tipo: ${TYPE_LABELS[value.type] ?? value.type}`,
      remove: () => onApply({ ...value, type: undefined }),
    })
  }
  if (value.origin) {
    activeChips.push({
      key: 'origin',
      label: `Origem: ${ORIGIN_LABELS[value.origin] ?? value.origin}`,
      remove: () => onApply({ ...value, origin: undefined }),
    })
  }
  if (value.minAmount) {
    activeChips.push({
      key: 'min',
      label: `Valor mínimo: ${formatMoney(value.minAmount, 'BRL')}`,
      remove: () => onApply({ ...value, minAmount: undefined }),
    })
  }
  if (value.maxAmount) {
    activeChips.push({
      key: 'max',
      label: `Valor máximo: ${formatMoney(value.maxAmount, 'BRL')}`,
      remove: () => onApply({ ...value, maxAmount: undefined }),
    })
  }
  if (value.from) {
    activeChips.push({
      key: 'from',
      label: `De: ${formatDate(value.from)}`,
      remove: () => {
        setFromDraft('')
        onApply({ ...value, from: undefined })
      },
    })
  }
  if (value.to) {
    activeChips.push({
      key: 'to',
      label: `Até: ${formatDate(value.to)}`,
      remove: () => {
        setToDraft('')
        onApply({ ...value, to: undefined })
      },
    })
  }

  const customRange = Boolean(value.from || value.to)

  function selectPeriod(next: DashboardPeriod) {
    setCustomOpen(false)
    setFromDraft('')
    setToDraft('')
    onApply({ ...value, period: next, from: undefined, to: undefined })
  }

  function applyCustomRange() {
    if (dateError) return
    onApply({
      ...value,
      from: fromDraft || undefined,
      to: toDraft || undefined,
    })
  }

  function openAdvanced() {
    setAdvancedDraft(value)
    setAdvancedError(undefined)
    setAdvancedOpen(true)
  }

  function submitAdvanced(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (visibleError) {
      setAdvancedError(visibleError)
      return
    }
    const minAmount = advancedDraft.minAmount ? normalizeAmount(advancedDraft.minAmount) : undefined
    const maxAmount = advancedDraft.maxAmount ? normalizeAmount(advancedDraft.maxAmount) : undefined
    if (advancedDraft.minAmount && !minAmount) {
      setAdvancedError('Informe um valor mínimo decimal válido.')
      return
    }
    if (advancedDraft.maxAmount && !maxAmount) {
      setAdvancedError('Informe um valor máximo decimal válido.')
      return
    }
    onApply({
      ...advancedDraft,
      minAmount,
      maxAmount,
      period: value.period,
      from: value.from,
      to: value.to,
    })
    setAdvancedOpen(false)
  }

  function toggleAccount(accountId: string) {
    const current = advancedDraft.accountIds ?? []
    setAdvancedDraft({
      ...advancedDraft,
      accountIds: current.includes(accountId)
        ? current.filter((id) => id !== accountId)
        : [...current, accountId],
    })
  }

  const advancedCount = [
    (value.accountIds ?? []).length > 0,
    Boolean(value.categoryId),
    Boolean(value.type),
    Boolean(value.origin),
    Boolean(value.minAmount),
    Boolean(value.maxAmount),
  ].filter(Boolean).length

  return (
    <section
      aria-labelledby="dashboard-filters-heading"
      className="rounded-panel border border-line bg-surface px-4 py-3 shadow-[var(--shadow-card)] sm:px-5"
    >
      <h2 id="dashboard-filters-heading" className="sr-only">
        Período e filtros do dashboard
      </h2>

      <div className="flex flex-wrap items-center gap-2 sm:gap-3">
        <div
          role="group"
          aria-label="Período"
          className="inline-flex rounded-control border border-line bg-surface-muted p-0.5"
        >
          {PERIOD_OPTIONS.map((option) => {
            const selected = !customRange && period === option.value
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={selected}
                onClick={() => selectPeriod(option.value)}
                className={[
                  'min-h-11 rounded-[8px] px-3 text-body-sm font-semibold transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                  selected
                    ? 'bg-surface text-ink shadow-[var(--shadow-card)]'
                    : 'text-ink-muted hover:text-ink',
                ].join(' ')}
              >
                {option.label}
              </button>
            )
          })}
        </div>

        <button
          type="button"
          aria-expanded={customOpen}
          onClick={() => setCustomOpen((current) => !current)}
          className={[
            'inline-flex min-h-11 items-center gap-2 rounded-control border px-3 text-body-sm font-semibold transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
            customRange
              ? 'border-primary/40 bg-primary-soft text-ink'
              : 'border-line bg-surface text-ink-muted hover:bg-surface-muted hover:text-ink',
          ].join(' ')}
        >
          <CalendarDays aria-hidden="true" className="size-4" />
          Personalizado
        </button>

        {customOpen ? (
          <div className="flex w-full flex-wrap items-end gap-2 sm:w-auto">
            <label className="flex flex-col gap-1 text-caption font-medium text-ink-muted">
              De
              <Input
                type="date"
                value={fromDraft}
                max={toDraft || undefined}
                onChange={(event) => setFromDraft(event.target.value)}
                className="w-full sm:w-40"
              />
            </label>
            <label className="flex flex-col gap-1 text-caption font-medium text-ink-muted">
              Até
              <Input
                type="date"
                value={toDraft}
                min={fromDraft || undefined}
                onChange={(event) => setToDraft(event.target.value)}
                className="w-full sm:w-40"
              />
            </label>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={applyCustomRange}
              disabled={Boolean(dateError)}
            >
              Aplicar
            </Button>
          </div>
        ) : null}

        <div className="ml-auto flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={openAdvanced}
            leadingIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
          >
            Filtros
            {advancedCount > 0 ? (
              <span className="rounded-pill bg-primary px-2 py-0.5 text-caption font-semibold text-on-primary">
                {advancedCount}
                <span className="sr-only"> filtros ativos</span>
              </span>
            ) : null}
          </Button>
          {activeChips.length > 0 ? (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setCustomOpen(false)
                setFromDraft('')
                setToDraft('')
                onApply({ period: 'MONTH' })
              }}
            >
              Limpar
            </Button>
          ) : null}
        </div>
      </div>

      {customOpen && dateError ? (
        <p role="alert" className="mt-2 text-body-sm text-expense">
          {dateError}
        </p>
      ) : null}

      {activeChips.length > 0 ? (
        <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-line pt-3">
          <span className="text-caption font-medium text-ink-muted">
            {activeChips.length} filtro(s) ativo(s)
          </span>
          {activeChips.map((chip) => (
            <FilterChip key={chip.key} onRemove={chip.remove}>
              {chip.label}
            </FilterChip>
          ))}
        </div>
      ) : null}

      <Drawer
        open={advancedOpen}
        onClose={() => setAdvancedOpen(false)}
        title="Filtros do dashboard"
        description="Conta, categoria, tipo, origem e faixa de valor. O período fica na barra superior."
        width="md"
        footer={
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setAdvancedOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" form="dashboard-advanced-filters">
              Aplicar filtros
            </Button>
          </div>
        }
      >
        <form
          id="dashboard-advanced-filters"
          onSubmit={submitAdvanced}
          className="space-y-5"
          aria-describedby={advancedError ? 'advanced-filters-error' : undefined}
        >
          {advancedError ? (
            <p
              id="advanced-filters-error"
              role="alert"
              className="rounded-card border border-expense/35 bg-expense-soft p-3 text-body-sm text-ink"
            >
              {advancedError}
            </p>
          ) : null}

          <fieldset>
            <legend className="text-body-sm font-semibold text-ink">
              Contas e cartões
              <span className="ml-1 font-normal text-ink-muted">(opcional)</span>
            </legend>
            <p className="mt-1 text-caption text-ink-muted">
              Nenhuma selecionada significa todas as contas e cartões.
            </p>
            <div className="mt-2 max-h-52 space-y-1 overflow-y-auto rounded-control border border-line p-2">
              {accounts.length === 0 ? (
                <p className="p-2 text-body-sm text-ink-muted">Nenhuma conta disponível.</p>
              ) : null}
              {accounts.map((account) => (
                <label
                  key={account.id}
                  className="flex min-h-11 items-center gap-2 rounded-control px-2 text-body-sm text-ink hover:bg-surface-muted"
                >
                  <input
                    type="checkbox"
                    checked={(advancedDraft.accountIds ?? []).includes(account.id)}
                    onChange={() => toggleAccount(account.id)}
                    className="size-4 rounded border-line"
                  />
                  <span className="truncate">
                    {account.alias ?? account.institutionName ?? 'Conta'}
                  </span>
                  <span className="ml-auto text-caption text-ink-muted">{account.currency}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field id="advanced-category" label="Categoria">
              <Select
                id="advanced-category"
                value={advancedDraft.categoryId ?? ''}
                onChange={(event) =>
                  setAdvancedDraft({
                    ...advancedDraft,
                    categoryId: event.target.value || undefined,
                  })
                }
              >
                <option value="">Todas</option>
                <option value={UNCATEGORIZED_CATEGORY_ID}>Sem categoria</option>
                {categories
                  .filter((category) => category.active && category.kind !== 'TRANSFER')
                  .map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
              </Select>
            </Field>

            <Field id="advanced-type" label="Tipo">
              <Select
                id="advanced-type"
                value={advancedDraft.type ?? ''}
                onChange={(event) =>
                  setAdvancedDraft({ ...advancedDraft, type: event.target.value || undefined })
                }
              >
                <option value="">Todos</option>
                {Object.entries(TYPE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>

            <Field id="advanced-origin" label="Origem">
              <Select
                id="advanced-origin"
                value={advancedDraft.origin ?? ''}
                onChange={(event) =>
                  setAdvancedDraft({ ...advancedDraft, origin: event.target.value || undefined })
                }
              >
                <option value="">Todas</option>
                {Object.entries(ORIGIN_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>

            <Field
              id="advanced-min"
              label="Valor mínimo"
              hint="Em reais; vírgula ou ponto decimal."
            >
              <Input
                id="advanced-min"
                inputMode="decimal"
                placeholder="0,00"
                value={advancedDraft.minAmount ?? ''}
                onChange={(event) =>
                  setAdvancedDraft({
                    ...advancedDraft,
                    minAmount: event.target.value || undefined,
                  })
                }
              />
            </Field>

            <Field id="advanced-max" label="Valor máximo" hint="Em reais.">
              <Input
                id="advanced-max"
                inputMode="decimal"
                placeholder="0,00"
                value={advancedDraft.maxAmount ?? ''}
                onChange={(event) =>
                  setAdvancedDraft({
                    ...advancedDraft,
                    maxAmount: event.target.value || undefined,
                  })
                }
              />
            </Field>
          </div>
        </form>
      </Drawer>
    </section>
  )
}
