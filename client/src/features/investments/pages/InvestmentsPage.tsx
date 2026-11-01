import { useState } from 'react'

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Search } from 'lucide-react'
import { useSearchParams } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Drawer } from '@/components/ui/drawer'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/ui/page-header'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'
import { ApiError } from '@/lib/api/problem'
import { invalidateInvestmentViews } from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'
import {
  financeApi,
  type InvestmentHolding,
  type InvestmentIncomeItem,
} from '@/features/integration/api'

import { InvestmentAllocation } from '../components/InvestmentAllocation'
import { InvestmentCoverage } from '../components/InvestmentCoverage'
import { InvestmentHoldingDrawer } from '../components/InvestmentHoldingDrawer'
import { InvestmentHoldings } from '../components/InvestmentHoldings'
import { InvestmentIncomeForm } from '../components/InvestmentIncomeForm'
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

type InvestmentsTab = 'summary' | 'positions' | 'income'

function parseTab(value: string | null): InvestmentsTab {
  return value === 'positions' || value === 'income' ? value : 'summary'
}

function searchParamsWithTab(params: URLSearchParams, tab: InvestmentsTab): URLSearchParams {
  const next = new URLSearchParams(params)
  if (tab === 'summary') next.delete('tab')
  else next.set('tab', tab)
  return next
}

/** Investimentos importados: resumo, posições e proventos, sem recomendação. */
export function InvestmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const tab = parseTab(searchParams.get('tab'))
  const [status, setStatus] = useState<string>('ACTIVE')
  const [assetClass, setAssetClass] = useState<string>('')
  const [search, setSearch] = useState('')
  const [visibleCount, setVisibleCount] = useState(12)
  const [openHolding, setOpenHolding] = useState<InvestmentHolding | null>(null)
  const [incomeForm, setIncomeForm] = useState<'new' | InvestmentIncomeItem | null>(null)

  const overviewQuery = useQuery({
    queryKey: queryKeys.investmentOverview,
    queryFn: financeApi.investmentOverview,
  })
  const holdingsQuery = useQuery({
    queryKey: [...queryKeys.investmentHoldings(status, assetClass || undefined), 'positions'],
    queryFn: () => financeApi.investmentHoldings(status, assetClass || undefined),
    enabled: tab === 'positions',
  })
  const activeHoldingsQuery = useQuery({
    queryKey: [...queryKeys.investmentHoldings('ACTIVE'), 'active'],
    queryFn: () => financeApi.investmentHoldings('ACTIVE'),
    enabled: tab === 'summary' || tab === 'income',
  })
  const incomeQuery = useQuery({
    queryKey: queryKeys.investmentIncome,
    queryFn: () => financeApi.investmentIncome(),
    enabled: tab === 'income',
  })

  const overview = overviewQuery.data
  const holdings = holdingsQuery.data?.items ?? []
  const searchTerm = search.trim().toLocaleLowerCase('pt-BR')
  const filteredHoldings = searchTerm
    ? holdings.filter((holding) =>
        [holding.name, holding.code, holding.isin]
          .filter(Boolean)
          .some((value) => String(value).toLocaleLowerCase('pt-BR').includes(searchTerm)),
      )
    : holdings
  const visibleHoldings = filteredHoldings.slice(0, visibleCount)

  const invalidate = () => {
    void invalidateInvestmentViews(queryClient)
  }

  function selectTab(next: string) {
    setSearchParams(searchParamsWithTab(searchParams, next as InvestmentsTab), {
      replace: false,
    })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        overline="Planejar"
        title="Investimentos"
        description="Posições importadas do Meu Pluggy. O sistema não calcula cotação em tempo real e não há recomendação de compra, venda, alocação ou ranking."
        actions={
          <Button
            type="button"
            variant="secondary"
            onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.investments })}
            leadingIcon={<RefreshCw className="size-4" />}
          >
            Atualizar dados
          </Button>
        }
      />

      {overviewQuery.isError ? (
        <Alert variant="danger" title="Não foi possível carregar os investimentos">
          {overviewQuery.error instanceof ApiError
            ? overviewQuery.error.message
            : 'Tente novamente em instantes.'}
        </Alert>
      ) : null}

      {overviewQuery.isLoading ? (
        <div className="space-y-4" role="status" aria-live="polite">
          <span className="sr-only">Carregando investimentos…</span>
          <Skeleton className="h-40 rounded-panel" />
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} className="h-28 rounded-card" />
            ))}
          </div>
        </div>
      ) : null}

      {overview ? (
        <Tabs value={tab} onValueChange={selectTab}>
          <TabList label="Seções de investimentos">
            <Tab value="summary">Resumo</Tab>
            <Tab value="positions">Posições</Tab>
            <Tab value="income">Proventos</Tab>
          </TabList>

          <TabPanel value="summary">
            <div className="space-y-6">
              <InvestmentSummary overview={overview} />
              <section aria-labelledby="investment-allocation-heading" className="space-y-3">
                <h2
                  id="investment-allocation-heading"
                  className="text-section-title font-semibold text-ink"
                >
                  Alocação
                </h2>
                <InvestmentAllocation
                  allocation={overview.allocation}
                  providerValue={overview.providerValue}
                  totalValue={overview.totalValue}
                  holdings={activeHoldingsQuery.data?.items ?? []}
                  holdingsLoading={activeHoldingsQuery.isLoading}
                  holdingsError={activeHoldingsQuery.isError}
                  onOpenHolding={setOpenHolding}
                />
              </section>
              <InvestmentCoverage sources={overview.coverage} />
              <details className="rounded-card border border-line bg-surface-muted p-4">
                <summary className="min-h-11 cursor-pointer text-body-sm font-semibold text-ink">
                  Limites deste módulo
                </summary>
                <ul className="mt-2 space-y-1 text-body-sm text-ink-muted">
                  <li>Não há recomendação de compra, venda ou alocação.</li>
                  <li>O valor atual não é cotação em tempo real.</li>
                  <li>Não há histórico de valor de portfólio nem rentabilidade reconstruída.</li>
                  <li>
                    Resultado estimado não é rendimento realizado; proventos não entram no
                    Dashboard.
                  </li>
                </ul>
              </details>
            </div>
          </TabPanel>

          <TabPanel value="positions">
            <section aria-labelledby="investment-holdings-heading" className="space-y-4">
              <div className="flex flex-wrap items-end justify-between gap-3">
                <div>
                  <h2
                    id="investment-holdings-heading"
                    className="text-section-title font-semibold text-ink"
                  >
                    Posições importadas
                  </h2>
                  <p className="text-caption text-ink-muted">
                    {overview.activeCount} ativa(s), {overview.closedCount} encerrada(s),{' '}
                    {overview.unavailableCount} indisponível(is).
                  </p>
                </div>
                <div className="flex flex-wrap items-end gap-2">
                  <label className="flex min-w-52 flex-col gap-1 text-caption font-medium text-ink-muted">
                    Buscar
                    <span className="relative block">
                      <Search
                        aria-hidden="true"
                        className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted"
                      />
                      <Input
                        type="search"
                        value={search}
                        onChange={(event) => {
                          setSearch(event.target.value)
                          setVisibleCount(12)
                        }}
                        placeholder="Nome, código ou ISIN"
                        className="pl-9"
                      />
                    </span>
                  </label>
                  <label className="flex flex-col gap-1 text-caption font-medium text-ink-muted">
                    Situação
                    <Select
                      value={status}
                      onChange={(event) => {
                        setStatus(event.target.value)
                        setVisibleCount(12)
                      }}
                      className="min-w-32"
                    >
                      {STATUS_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </Select>
                  </label>
                  <label className="flex flex-col gap-1 text-caption font-medium text-ink-muted">
                    Classe
                    <Select
                      value={assetClass}
                      onChange={(event) => {
                        setAssetClass(event.target.value)
                        setVisibleCount(12)
                      }}
                      className="min-w-36"
                    >
                      <option value="">Todas</option>
                      {Object.entries(ASSET_CLASS_LABELS).map(([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ))}
                    </Select>
                  </label>
                </div>
              </div>

              {holdingsQuery.isLoading ? (
                <p role="status" className="text-body-sm text-ink-muted">
                  Carregando posições…
                </p>
              ) : null}
              {holdingsQuery.isError ? (
                <Alert variant="danger" title="Posições indisponíveis">
                  Não foi possível carregar as posições importadas.
                </Alert>
              ) : null}
              {holdingsQuery.isSuccess ? (
                <>
                  <p role="status" className="text-caption text-ink-muted">
                    {filteredHoldings.length} posição(ões) no filtro
                    {searchTerm ? ' e na busca local' : ''}.
                  </p>
                  <InvestmentHoldings items={visibleHoldings} onOpen={setOpenHolding} />
                  {visibleCount < filteredHoldings.length ? (
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => setVisibleCount((count) => count + 12)}
                    >
                      Mostrar mais posições
                    </Button>
                  ) : null}
                </>
              ) : null}
            </section>
          </TabPanel>

          <TabPanel value="income">
            <section aria-labelledby="investment-income-heading" className="space-y-4">
              <h2 id="investment-income-heading" className="sr-only">
                Proventos
              </h2>
              {incomeQuery.isLoading ? (
                <p role="status" className="text-body-sm text-ink-muted">
                  Carregando proventos…
                </p>
              ) : null}
              {incomeQuery.isError ? (
                <Alert variant="danger" title="Proventos indisponíveis">
                  Não foi possível carregar os proventos.
                </Alert>
              ) : null}
              {incomeQuery.data ? (
                <InvestmentIncomeSection
                  income={incomeQuery.data}
                  onCreate={() => setIncomeForm('new')}
                  onEdit={(item) => setIncomeForm(item)}
                  onChanged={invalidate}
                />
              ) : null}
            </section>
          </TabPanel>
        </Tabs>
      ) : null}

      <InvestmentHoldingDrawer
        holding={openHolding}
        open={Boolean(openHolding)}
        onClose={() => setOpenHolding(null)}
        onChanged={invalidate}
      />

      <Drawer
        open={incomeForm !== null}
        onClose={() => setIncomeForm(null)}
        title={incomeForm === 'new' ? 'Registrar provento manual' : 'Editar provento manual'}
        description="Proventos manuais complementam o provedor e não entram automaticamente nas receitas do Dashboard."
        width="md"
      >
        {incomeForm ? (
          <InvestmentIncomeForm
            holdings={activeHoldingsQuery.data?.items ?? []}
            initial={incomeForm === 'new' ? undefined : incomeForm}
            onSaved={() => {
              setIncomeForm(null)
              invalidate()
            }}
            onCancel={() => setIncomeForm(null)}
            embedded
          />
        ) : null}
      </Drawer>
    </div>
  )
}
