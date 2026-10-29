import { vi } from 'vitest'

import type {
  InvestmentHolding,
  InvestmentIncome,
  InvestmentIncomeItem,
  InvestmentOverview,
} from '@/features/integration/api'

export const USER = {
  id: 'user-1',
  username: 'alice.invest',
  createdAt: '2026-10-20T12:00:00Z',
}

const TODAY = '2026-10-27'

export const HOLDINGS: InvestmentHolding[] = [
  {
    id: 'holding-1',
    connectionId: 'connection-1',
    name: 'FII Alfa',
    code: 'AAAA11',
    isin: 'BRAAAA',
    type: 'EQUITY',
    subtype: 'STOCK',
    assetClass: 'FII',
    classificationSource: 'CODE_HEURISTIC',
    status: 'ACTIVE',
    currency: 'BRL',
    quantity: '10',
    unitValue: '100.00',
    amount: '1000.00',
    balance: '1000.00',
    amountOriginal: null,
    amountProfit: null,
    amountWithdrawal: '1000.00',
    taxes: null,
    taxes2: null,
    rate: null,
    rateType: null,
    fixedAnnualRate: null,
    lastMonthRate: null,
    annualRate: null,
    lastTwelveMonthsRate: null,
    referenceDate: TODAY,
    purchaseDate: null,
    dueDate: null,
    gracePeriodDate: null,
    providerUpdatedAt: '2026-10-27T12:00:00Z',
    syncedAt: '2026-10-27T12:00:00Z',
    unavailableAt: null,
    manualCostBasis: null,
    costBasis: null,
    costBasisSource: 'UNAVAILABLE',
    incomeTotal: '12.50',
    estimatedProfit: null,
    estimatedReturnPercentage: null,
    userAssetClass: null,
    profitSource: 'UNAVAILABLE',
    stale: false,
  },
  {
    id: 'holding-2',
    connectionId: 'connection-1',
    name: 'FII Beta',
    code: 'BBBB11',
    isin: 'BRBBBB',
    type: 'EQUITY',
    subtype: 'STOCK',
    assetClass: 'FII',
    classificationSource: 'CODE_HEURISTIC',
    status: 'ACTIVE',
    currency: 'BRL',
    quantity: '5',
    unitValue: '100.00',
    amount: '500.00',
    balance: '500.00',
    amountOriginal: '450.00',
    amountProfit: '50.00',
    amountWithdrawal: '500.00',
    taxes: null,
    taxes2: null,
    rate: null,
    rateType: null,
    fixedAnnualRate: null,
    lastMonthRate: null,
    annualRate: null,
    lastTwelveMonthsRate: null,
    referenceDate: TODAY,
    purchaseDate: null,
    dueDate: null,
    gracePeriodDate: null,
    providerUpdatedAt: '2026-10-27T12:00:00Z',
    syncedAt: '2026-10-27T12:00:00Z',
    unavailableAt: null,
    manualCostBasis: null,
    costBasis: '450.00',
    costBasisSource: 'PROVIDER',
    incomeTotal: '0.00',
    estimatedProfit: '50.00',
    estimatedReturnPercentage: '11.11',
    userAssetClass: null,
    profitSource: 'PROVIDER',
    stale: false,
  },
  {
    id: 'holding-3',
    connectionId: 'connection-1',
    name: 'CDB Alfa',
    code: 'CDB-1',
    isin: null,
    type: 'FIXED_INCOME',
    subtype: 'CDB',
    assetClass: 'RENDA_FIXA',
    classificationSource: 'PROVIDER',
    status: 'ACTIVE',
    currency: 'BRL',
    quantity: '1',
    unitValue: '250.00',
    amount: '250.00',
    balance: '250.00',
    amountOriginal: null,
    amountProfit: null,
    amountWithdrawal: '250.00',
    taxes: null,
    taxes2: null,
    rate: '100',
    rateType: 'CDI',
    fixedAnnualRate: '2.00',
    lastMonthRate: null,
    annualRate: null,
    lastTwelveMonthsRate: null,
    referenceDate: TODAY,
    purchaseDate: null,
    dueDate: null,
    gracePeriodDate: null,
    providerUpdatedAt: '2026-10-27T12:00:00Z',
    syncedAt: '2026-10-27T12:00:00Z',
    unavailableAt: null,
    manualCostBasis: null,
    costBasis: null,
    costBasisSource: 'UNAVAILABLE',
    incomeTotal: '0.00',
    estimatedProfit: null,
    estimatedReturnPercentage: null,
    userAssetClass: null,
    profitSource: 'UNAVAILABLE',
    stale: false,
  },
  {
    id: 'holding-4',
    connectionId: 'connection-1',
    name: 'FII Gama',
    code: 'CCCC11',
    isin: 'BRCCCC',
    type: 'EQUITY',
    subtype: 'STOCK',
    assetClass: 'FII',
    classificationSource: 'CODE_HEURISTIC',
    status: 'CLOSED',
    currency: 'BRL',
    quantity: '0',
    unitValue: '0',
    amount: '0',
    balance: '0',
    amountOriginal: null,
    amountProfit: null,
    amountWithdrawal: '0',
    taxes: null,
    taxes2: null,
    rate: null,
    rateType: null,
    fixedAnnualRate: null,
    lastMonthRate: null,
    annualRate: null,
    lastTwelveMonthsRate: null,
    referenceDate: TODAY,
    purchaseDate: null,
    dueDate: null,
    gracePeriodDate: null,
    providerUpdatedAt: '2026-10-27T12:00:00Z',
    syncedAt: '2026-10-27T12:00:00Z',
    unavailableAt: null,
    manualCostBasis: null,
    costBasis: null,
    costBasisSource: 'UNAVAILABLE',
    incomeTotal: '0.00',
    estimatedProfit: null,
    estimatedReturnPercentage: null,
    userAssetClass: null,
    profitSource: 'UNAVAILABLE',
    stale: false,
  },
]

export const OVERVIEW: InvestmentOverview = {
  referenceDate: TODAY,
  providerValue: '1750.00',
  totalValue: '1750.00',
  activeCount: 3,
  closedCount: 1,
  unavailableCount: 0,
  incomeLast12Months: '12.50',
  incomeSource: 'PROVIDER',
  profitReported: '50.00',
  profitCoverageCount: 1,
  estimatedProfit: '50.00',
  estimatedProfitCoverageCount: 1,
  lastSyncedAt: '2026-10-27T12:00:00Z',
  stale: false,
  allocation: [
    { assetClass: 'FII', value: '1500.00', count: 2, percentage: '85.71' },
    { assetClass: 'RENDA_FIXA', value: '250.00', count: 1, percentage: '14.29' },
  ],
  coverage: [
    {
      connectionId: 'connection-1',
      scope: 'AVAILABLE',
      resourceCount: 2,
      availableResourceCount: 2,
      pendingResourceCount: 0,
      unavailableResourceCount: 0,
      resourcesCollectedAt: '2026-10-27T12:00:00Z',
      lastSyncedAt: '2026-10-27T12:00:00Z',
      errorCode: null,
    },
    {
      connectionId: 'connection-2',
      scope: 'NOT_SHARED',
      resourceCount: 0,
      availableResourceCount: 0,
      pendingResourceCount: 0,
      unavailableResourceCount: 0,
      resourcesCollectedAt: '2026-10-27T12:00:00Z',
      lastSyncedAt: '2026-10-27T12:00:00Z',
      errorCode: null,
    },
  ],
}

export const INCOME: InvestmentIncome = {
  from: '2025-10-27',
  to: TODAY,
  total: '12.50',
  source: 'PROVIDER',
  byMonth: [{ month: '2026-10', amount: '12.50', count: 1 }],
  items: [
    {
      id: null,
      date: '2026-10-26',
      investmentId: 'holding-1',
      investmentName: 'FII Alfa',
      code: 'AAAA11',
      type: 'EQUITY',
      incomeType: 'INTEREST',
      amount: '12.50',
      description: 'Rendimento',
      source: 'PROVIDER',
      editable: false,
    },
  ],
}

interface StubOptions {
  readonly holdings?: InvestmentHolding[]
  readonly overview?: InvestmentOverview
  readonly income?: InvestmentIncome
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

export function stubInvestmentsApi(options: StubOptions = {}) {
  let holdings = (options.holdings ?? HOLDINGS).map((holding) => ({ ...holding }))
  const overview = options.overview ?? OVERVIEW
  const baseItems = options.income?.items ?? INCOME.items
  const providerItems = baseItems.filter((item) => !item.editable)
  let manualIncomes: InvestmentIncomeItem[] = baseItems
    .filter((item) => item.editable)
    .map((item) => ({ ...item }))

  function incomeItems(): InvestmentIncomeItem[] {
    return [...providerItems, ...manualIncomes].sort((left, right) =>
      right.date.localeCompare(left.date),
    )
  }

  function currentIncome(): InvestmentIncome {
    const items = incomeItems()
    const total = items.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
    const months = new Map<string, { amount: number; count: number }>()
    for (const item of items) {
      const month = item.date.slice(0, 7)
      const entry = months.get(month) ?? { amount: 0, count: 0 }
      entry.amount += Number(item.amount) || 0
      entry.count += 1
      months.set(month, entry)
    }
    const hasProvider = items.some((item) => item.source === 'PROVIDER')
    const hasManual = items.some((item) => item.source === 'MANUAL')
    return {
      from: options.income?.from ?? INCOME.from,
      to: options.income?.to ?? INCOME.to,
      total: total.toFixed(2),
      source:
        hasProvider && hasManual
          ? 'MIXED'
          : hasProvider
            ? 'PROVIDER'
            : hasManual
              ? 'MANUAL'
              : 'UNAVAILABLE',
      byMonth: [...months.entries()].map(([month, entry]) => ({
        month,
        amount: entry.amount.toFixed(2),
        count: entry.count,
      })),
      items,
    }
  }

  function targetFor(body: Record<string, string>): {
    targetId: string
    targetName: string
    targetCode: string | null
    type: string | null
  } | null {
    if (!body.providerInvestmentId) return null
    const holding = holdings.find((item) => item.id === body.providerInvestmentId)
    if (!holding) return null
    return {
      targetId: holding.id,
      targetName: holding.name ?? 'Posição importada',
      targetCode: holding.code ?? null,
      type: holding.type,
    }
  }

  const mock = vi.fn((input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    const method = init.method ?? 'GET'
    const path = new URL(url, 'http://localhost').pathname.replace('/api/v1', '')

    if (path === '/auth/me') return jsonResponse(USER)
    if (path === '/auth/csrf') return new Response(null, { status: 204 })
    if (path === '/investments/overview') return jsonResponse(overview)
    if (path === '/investments/income' && method === 'GET') return jsonResponse(currentIncome())
    if (path === '/investments/holdings') {
      const params = new URL(url, 'http://localhost').searchParams
      const status = params.get('status') ?? 'ALL'
      const assetClass = params.get('assetClass')
      const filtered = holdings.filter((holding) => {
        if (assetClass && holding.assetClass !== assetClass) return false
        if (status === 'ALL') return true
        if (status === 'ACTIVE') return holding.status !== 'CLOSED' && holding.unavailableAt == null
        if (status === 'CLOSED') return holding.status === 'CLOSED'
        if (status === 'UNAVAILABLE') return holding.unavailableAt != null
        return holding.status === status
      })
      return jsonResponse({ items: filtered })
    }
    if (path.startsWith('/investments/holdings/') && method === 'GET') {
      const id = path.split('/').pop()
      const holding = holdings.find((item) => item.id === id)
      return holding ? jsonResponse(holding) : jsonResponse({}, 404)
    }
    if (path === '/investments/income' && method === 'POST') {
      const body = JSON.parse(String(init.body)) as Record<string, string>
      const target = targetFor(body)
      if (!target) return jsonResponse({}, 422)
      const created: InvestmentIncomeItem = {
        id: `income-${manualIncomes.length + 1}`,
        date: body.incomeDate ?? '2026-10-27',
        investmentId: target.targetId,
        investmentName: target.targetName,
        code: target.targetCode,
        type: target.type,
        incomeType: (body.incomeType ?? 'OTHER') as InvestmentIncomeItem['incomeType'],
        amount: body.amount ?? '0.00',
        description: body.description ?? null,
        source: 'MANUAL',
        editable: true,
      }
      manualIncomes = [created, ...manualIncomes]
      return jsonResponse(created, 201)
    }
    if (path.startsWith('/investments/income/') && method === 'PATCH') {
      const id = path.split('/').pop() ?? ''
      const body = JSON.parse(String(init.body)) as Record<string, string>
      const target = targetFor(body)
      if (!target) return jsonResponse({}, 422)
      const existing = manualIncomes.find((item) => item.id === id)
      if (!existing) return jsonResponse({}, 404)
      const updated: InvestmentIncomeItem = {
        ...existing,
        investmentId: target.targetId,
        investmentName: target.targetName,
        code: target.targetCode,
        type: target.type,
        incomeType: (body.incomeType ?? existing.incomeType) as InvestmentIncomeItem['incomeType'],
        date: body.incomeDate ?? existing.date,
        amount: body.amount ?? existing.amount,
        description: body.description ?? existing.description,
      }
      manualIncomes = manualIncomes.map((item) => (item.id === id ? updated : item))
      return jsonResponse(updated)
    }
    if (path.startsWith('/investments/income/') && method === 'DELETE') {
      const id = path.split('/').pop() ?? ''
      manualIncomes = manualIncomes.filter((item) => item.id !== id)
      return new Response(null, { status: 204 })
    }
    if (
      path.startsWith('/investments/holdings/') &&
      path.endsWith('/cost-basis') &&
      method === 'PATCH'
    ) {
      const id = path.split('/')[3]
      const body = JSON.parse(String(init.body)) as Record<string, string | null>
      const holding = holdings.find((item) => item.id === id)
      if (!holding) return jsonResponse({}, 404)
      const manual =
        body.costBasis && String(body.costBasis).trim() !== '' ? Number(body.costBasis) : null
      const provider = holding.amountOriginal == null ? null : Number(holding.amountOriginal)
      const cost = manual ?? provider
      const balance = Number(holding.balance) || 0
      const income = Number(holding.incomeTotal) || 0
      const profit = cost == null ? null : balance + income - cost
      const updated: InvestmentHolding = {
        ...holding,
        manualCostBasis: manual == null ? null : manual.toFixed(2),
        costBasis: cost == null ? null : cost.toFixed(2),
        costBasisSource: manual != null ? 'MANUAL' : provider != null ? 'PROVIDER' : 'UNAVAILABLE',
        estimatedProfit: profit == null ? null : profit.toFixed(2),
        estimatedReturnPercentage:
          profit == null || cost == null || cost <= 0 ? null : ((profit / cost) * 100).toFixed(2),
        userAssetClass: holding.userAssetClass,
      }
      holdings = holdings.map((item) => (item.id === id ? updated : item))
      return new Response(null, { status: 204 })
    }
    if (
      path.startsWith('/investments/holdings/') &&
      path.endsWith('/asset-class') &&
      method === 'PATCH'
    ) {
      const id = path.split('/')[3]
      const body = JSON.parse(String(init.body)) as Record<string, string | null>
      const holding = holdings.find((item) => item.id === id)
      if (!holding) return jsonResponse({}, 404)
      const assetClass = body.assetClass
        ? (String(body.assetClass) as InvestmentHolding['assetClass'])
        : null
      const updated: InvestmentHolding = {
        ...holding,
        userAssetClass: assetClass,
        assetClass: assetClass ?? holding.assetClass,
        classificationSource: assetClass ? 'USER' : holding.classificationSource,
      }
      holdings = holdings.map((item) => (item.id === id ? updated : item))
      return new Response(null, { status: 204 })
    }
    return jsonResponse({}, 404)
  })

  vi.stubGlobal('fetch', mock)
  return mock
}
