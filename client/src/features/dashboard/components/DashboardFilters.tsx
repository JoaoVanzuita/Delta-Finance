import { useState, type FormEvent } from 'react'

import type { Account, Category } from '@/features/integration/api'
import {
  UNCATEGORIZED_CATEGORY_ID,
  type DashboardFilters as DashboardFiltersValue,
} from '@/features/dashboard/api'

interface DashboardFiltersProps {
  readonly value: DashboardFiltersValue
  readonly accounts: readonly Account[]
  readonly categories: readonly Category[]
  readonly onApply: (filters: DashboardFiltersValue) => void
}

const fieldClass =
  'mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink'

/**
 * Filtros do dashboard e da distribuição de despesas.
 *
 * O formulário só altera os totais ao ser enviado, evitando recálculo a cada
 * tecla. Todos os campos têm rótulo associado e o botão de limpar devolve o
 * período calendário padrão (mês atual).
 */
export function DashboardFilters({ value, accounts, categories, onApply }: DashboardFiltersProps) {
  const [draft, setDraft] = useState<DashboardFiltersValue>(value)

  function update<K extends keyof DashboardFiltersValue>(key: K, next: DashboardFiltersValue[K]) {
    setDraft((current) => ({ ...current, [key]: next }))
  }

  function toggleAccount(accountId: string) {
    const current = draft.accountIds ?? []
    update(
      'accountIds',
      current.includes(accountId)
        ? current.filter((id) => id !== accountId)
        : [...current, accountId],
    )
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onApply(draft)
  }

  function reset() {
    const cleared: DashboardFiltersValue = { period: 'MONTH' }
    setDraft(cleared)
    onApply(cleared)
  }

  return (
    <form
      onSubmit={submit}
      aria-labelledby="dashboard-filters-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="dashboard-filters-heading" className="text-base font-semibold text-ink">
          Filtros
        </h2>
        <p className="text-xs text-ink-muted">Períodos no fuso America/Sao_Paulo</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="block text-sm">
          <span className="font-medium text-ink">Período</span>
          <select
            value={draft.period ?? 'MONTH'}
            onChange={(event) =>
              update('period', event.target.value as DashboardFiltersValue['period'])
            }
            className={fieldClass}
          >
            <option value="WEEK">Semana</option>
            <option value="MONTH">Mês</option>
            <option value="YEAR">Ano</option>
          </select>
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">De</span>
          <input
            type="date"
            value={draft.from ?? ''}
            onChange={(event) => update('from', event.target.value || undefined)}
            className={fieldClass}
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Até</span>
          <input
            type="date"
            value={draft.to ?? ''}
            onChange={(event) => update('to', event.target.value || undefined)}
            className={fieldClass}
          />
        </label>

        <fieldset className="block text-sm sm:col-span-2">
          <legend className="font-medium text-ink">Contas e cartões</legend>
          <p className="mt-1 text-xs text-ink-muted">
            Nenhum selecionado significa todas as contas e cartões.
          </p>
          <div className="mt-2 max-h-40 space-y-1 overflow-y-auto rounded-card border border-line bg-surface p-2">
            {accounts.map((account) => (
              <label key={account.id} className="flex items-center gap-2 text-sm text-ink">
                <input
                  type="checkbox"
                  checked={(draft.accountIds ?? []).includes(account.id)}
                  onChange={() => toggleAccount(account.id)}
                  className="h-4 w-4 rounded border-line"
                />
                <span>{account.alias ?? account.institutionName ?? account.id}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="block text-sm">
          <span className="font-medium text-ink">Categoria</span>
          <select
            value={draft.categoryId ?? ''}
            onChange={(event) => update('categoryId', event.target.value || undefined)}
            className={fieldClass}
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
          </select>
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Tipo</span>
          <select
            value={draft.type ?? ''}
            onChange={(event) => update('type', event.target.value || undefined)}
            className={fieldClass}
          >
            <option value="">Todos</option>
            <option value="DEBIT">Despesa</option>
            <option value="CREDIT">Receita</option>
            <option value="REFUND">Reembolso</option>
            <option value="CHARGEBACK">Chargeback</option>
            <option value="ADJUSTMENT">Ajuste</option>
          </select>
        </label>

        <label className="block text-sm">
          <span className="font-medium text-ink">Origem</span>
          <select
            value={draft.origin ?? ''}
            onChange={(event) => update('origin', event.target.value || undefined)}
            className={fieldClass}
          >
            <option value="">Todas</option>
            <option value="IMPORTED">Importada</option>
            <option value="MANUAL">Manual</option>
          </select>
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="font-medium text-ink">Valor mínimo</span>
            <input
              inputMode="decimal"
              placeholder="0,00"
              value={draft.minAmount ?? ''}
              onChange={(event) => update('minAmount', event.target.value || undefined)}
              className={fieldClass}
            />
          </label>
          <label className="block text-sm">
            <span className="font-medium text-ink">Valor máximo</span>
            <input
              inputMode="decimal"
              placeholder="0,00"
              value={draft.maxAmount ?? ''}
              onChange={(event) => update('maxAmount', event.target.value || undefined)}
              className={fieldClass}
            />
          </label>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
        >
          Aplicar filtros
        </button>
        <button
          type="button"
          onClick={reset}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted"
        >
          Limpar filtros
        </button>
      </div>
    </form>
  )
}
