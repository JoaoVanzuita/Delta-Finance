import { useState } from 'react'

import { useQuery, useQueryClient } from '@tanstack/react-query'

import { ApiError } from '@/lib/api/problem'

import { formatDateTime } from '@/features/accounts/format'
import { financeApi } from '@/features/integration/api'

import { InvestmentAllocation } from '../components/InvestmentAllocation'
import { InvestmentHoldings } from '../components/InvestmentHoldings'
import { InvestmentIncomeSection } from '../components/InvestmentIncomeSection'
import { InvestmentSummary } from '../components/InvestmentSummary'
import { ASSET_CLASS_LABELS } from '../labels'

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Ativas' },
  { value: 'CLOSED', label: 'Encerradas' },
  { value: 'UNAVAILABLE', label: 'Indisponíveis' },
  { value: 'PENDING', label: 'Pendentes' },
  { value: 'ALL', label: 'Todas' },
] as const

/**
 * Dashboard de investimentos.
 *
 * <p>Exibe apenas posições importadas do provedor, sem recomendar compra, venda ou alocação.
 * Valores ausentes são exibidos como "não informado"; não há cotação em tempo real nem histórico de
 * valor de portfólio.
 */
export function InvestmentsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<string>('ACTIVE')
  const [assetClass, setAssetClass] = useState<string>('')

  const overviewQuery = useQuery({
    queryKey: ['investments', 'overview'],
    queryFn: financeApi.investmentOverview,
  })
  const holdingsQuery = useQuery({
    queryKey: ['investments', 'holdings', status, assetClass],
    queryFn: () => financeApi.investmentHoldings(status, assetClass || undefined),
  })
  const incomeQuery = useQuery({
    queryKey: ['investments', 'income'],
    queryFn: () => financeApi.investmentIncome(),
  })

  const overview = overviewQuery.data
  const holdings = holdingsQuery.data?.items ?? []
  const queryError = overviewQuery.error ?? holdingsQuery.error ?? incomeQuery.error

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['investments'] })
  }

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <header className="space-y-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-ink-muted uppercase">
          Fase secundária
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Investimentos</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-ink-muted">
          Posições importadas do Meu Pluggy. O valor atual usa o último dado do provedor; o sistema
          não calcula cotação em tempo real, não deriva rentabilidade de histórico incompleto e não
          oferece recomendação de compra, venda ou alocação.
        </p>
      </header>

      {queryError ? (
        <p
          role="alert"
          className="rounded-card border border-expense/30 bg-expense/5 p-4 text-sm text-ink"
        >
          {queryError instanceof ApiError
            ? queryError.message
            : 'Não foi possível carregar os investimentos.'}
        </p>
      ) : null}

      {overviewQuery.isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando investimentos…
        </p>
      ) : null}

      {overview ? (
        <>
          <InvestmentSummary overview={overview} />

          <section aria-labelledby="investment-allocation-heading" className="space-y-3">
            <h2 id="investment-allocation-heading" className="text-lg font-semibold text-ink">
              Alocação
            </h2>
            <InvestmentAllocation
              allocation={overview.allocation}
              providerValue={overview.providerValue}
              totalValue={overview.totalValue}
            />
          </section>

          <section aria-labelledby="investment-holdings-heading" className="space-y-3">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
              <div className="space-y-1">
                <h2 id="investment-holdings-heading" className="text-lg font-semibold text-ink">
                  Posições importadas
                </h2>
                <p className="text-sm text-ink-muted">
                  {overview.activeCount} ativa(s), {overview.closedCount} encerrada(s) e{' '}
                  {overview.unavailableCount} indisponível(is).
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
                  Situação
                  <select
                    value={status}
                    onChange={(event) => setStatus(event.target.value)}
                    className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
                  >
                    {STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="flex flex-col gap-1 text-xs font-medium text-ink-muted">
                  Classe
                  <select
                    value={assetClass}
                    onChange={(event) => setAssetClass(event.target.value)}
                    className="min-h-11 rounded-card border border-line bg-surface px-3 text-sm text-ink"
                  >
                    <option value="">Todas</option>
                    {Object.entries(ASSET_CLASS_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
            {holdingsQuery.isLoading ? (
              <p role="status" aria-live="polite" className="text-sm text-ink-muted">
                Carregando posições…
              </p>
            ) : null}
            {holdingsQuery.isError ? (
              <p role="alert" className="text-sm text-expense">
                Não foi possível carregar as posições importadas.
              </p>
            ) : null}
            {!holdingsQuery.isLoading && !holdingsQuery.isError ? (
              <InvestmentHoldings items={holdings} onChanged={invalidate} />
            ) : null}
          </section>

          <section aria-labelledby="investment-income-heading" className="space-y-3">
            <h2 id="investment-income-heading" className="text-lg font-semibold text-ink">
              Proventos
            </h2>
            {incomeQuery.isLoading ? (
              <p role="status" aria-live="polite" className="text-sm text-ink-muted">
                Carregando proventos…
              </p>
            ) : null}
            {incomeQuery.isError ? (
              <p role="alert" className="text-sm text-expense">
                Não foi possível carregar os proventos.
              </p>
            ) : null}
            {incomeQuery.data ? (
              <InvestmentIncomeSection
                income={incomeQuery.data}
                holdings={holdings}
                onChanged={invalidate}
              />
            ) : null}
          </section>
        </>
      ) : null}

      <section
        aria-labelledby="investment-disclaimer-heading"
        className="rounded-card border border-line bg-surface-muted p-4"
      >
        <h2 id="investment-disclaimer-heading" className="text-sm font-semibold text-ink">
          Limites deste módulo
        </h2>
        <ul className="mt-2 space-y-1 text-sm leading-relaxed text-ink-muted">
          <li>Não há recomendação de compra, venda, alocação ou ranking de ativos.</li>
          <li>
            O valor atual é o último saldo informado pelo provedor; não é cotação em tempo real.
          </li>
          <li>
            O sistema não mantém histórico de valor de portfólio. O histórico de proventos deriva de
            eventos datados do provedor.
          </li>
          <li>
            Quando o provedor não envia dividendos ou rentabilidade, o campo aparece como “não
            informado”.
          </li>
        </ul>
      </section>

      {overview?.lastSyncedAt ? (
        <p className="text-xs text-ink-muted">
          Última sincronização consolidada: {formatDateTime(overview.lastSyncedAt)}.
        </p>
      ) : null}
    </div>
  )
}
