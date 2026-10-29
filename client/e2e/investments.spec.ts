import { expect, test, type Page } from '@playwright/test'

/**
 * E2E do dashboard de investimentos com stub de API.
 *
 * Prova resumo, alocação, cobertura, filtros, proventos e CRUD manual, sem
 * recomendação de compra/venda/alocação e sem cotação em tempo real.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const TODAY = '2026-10-27'

const HOLDINGS = [
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

const OVERVIEW: Record<string, unknown> = {
  referenceDate: TODAY,
  providerValue: '1500.00',
  totalValue: '1500.00',
  activeCount: 2,
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
  allocation: [{ assetClass: 'FII', value: '1500.00', count: 2, percentage: '100.00' }],
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

const INCOME = {
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

interface Holding {
  id: string
  name?: string | null
  code?: string | null
  type: string
  status: string
  assetClass: string
  classificationSource: string
  balance?: string | null
  amountOriginal?: string | null
  incomeTotal?: string | null
  manualCostBasis?: string | null
  costBasis?: string | null
  costBasisSource?: string
  estimatedProfit?: string | null
  estimatedReturnPercentage?: string | null
  userAssetClass?: string | null
  unavailableAt?: string | null
  [key: string]: unknown
}

interface Overrides {
  readonly overview?: typeof OVERVIEW
  readonly holdings?: typeof HOLDINGS
  readonly income?: typeof INCOME
}

async function installStub(page: Page, overrides: Overrides = {}): Promise<void> {
  const overview = overrides.overview ?? OVERVIEW
  let holdings: Holding[] = (overrides.holdings ?? HOLDINGS).map((holding) => ({
    ...holding,
  }))
  const baseIncome = overrides.income ?? INCOME
  const providerIncomeItems = baseIncome.items.filter((item) => !item.editable)
  let manualIncomes: Array<Record<string, unknown>> = []

  function currentIncome() {
    const items = [...providerIncomeItems, ...manualIncomes].sort((left, right) =>
      String(right.date).localeCompare(String(left.date)),
    )
    const total = items.reduce((sum, item) => sum + (Number(item.amount) || 0), 0)
    const months = new Map<string, { amount: number; count: number }>()
    for (const item of items) {
      const month = String(item.date).slice(0, 7)
      const entry = months.get(month) ?? { amount: 0, count: 0 }
      entry.amount += Number(item.amount) || 0
      entry.count += 1
      months.set(month, entry)
    }
    const hasProvider = items.some((item) => item.source === 'PROVIDER')
    const hasManual = items.some((item) => item.source === 'MANUAL')
    return {
      ...baseIncome,
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

  function targetFor(body: Record<string, string>) {
    if (body.providerInvestmentId) {
      const holding = holdings.find((item) => item.id === body.providerInvestmentId)
      if (!holding) return null
      return {
        targetId: holding.id,
        targetName: holding.name ?? 'Posição importada',
        targetCode: holding.code ?? null,
        type: holding.type,
      }
    }
    return null
  }

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/investments/overview') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(overview),
      })
    }
    if (path === '/investments/income' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(currentIncome()),
      })
    }
    if (path === '/investments/income' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, string>
      const target = targetFor(body)
      if (!target) return route.fulfill({ status: 422, body: '{}' })
      const created = {
        id: `income-${manualIncomes.length + 1}`,
        date: body.incomeDate ?? '2026-10-27',
        investmentId: target.targetId,
        investmentName: target.targetName,
        code: target.targetCode,
        type: target.type,
        incomeType: body.incomeType ?? 'OTHER',
        amount: body.amount ?? '0.00',
        description: body.description ?? null,
        source: 'MANUAL',
        editable: true,
      }
      manualIncomes = [created, ...manualIncomes]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path.startsWith('/investments/income/') && method === 'PATCH') {
      const id = path.split('/').pop()
      const body = request.postDataJSON() as Record<string, string>
      const target = targetFor(body)
      const existing = manualIncomes.find((item) => item.id === id)
      if (!target || !existing) return route.fulfill({ status: 404, body: '{}' })
      const updated = {
        ...existing,
        investmentId: target.targetId,
        investmentName: target.targetName,
        code: target.targetCode,
        type: target.type,
        incomeType: body.incomeType ?? existing.incomeType,
        date: body.incomeDate ?? existing.date,
        amount: body.amount ?? existing.amount,
        description: body.description ?? existing.description,
      }
      manualIncomes = manualIncomes.map((item) => (item.id === id ? updated : item))
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(updated),
      })
    }
    if (path.startsWith('/investments/income/') && method === 'DELETE') {
      const id = path.split('/').pop()
      manualIncomes = manualIncomes.filter((item) => item.id !== id)
      return route.fulfill({ status: 204 })
    }
    if (path === '/investments/holdings') {
      const status = url.searchParams.get('status') ?? 'ALL'
      const filtered = holdings.filter((holding) => {
        if (status === 'ALL') return true
        if (status === 'ACTIVE') return holding.status !== 'CLOSED' && holding.unavailableAt == null
        if (status === 'CLOSED') return holding.status === 'CLOSED'
        if (status === 'UNAVAILABLE') return holding.unavailableAt != null
        return holding.status === status
      })
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: filtered }),
      })
    }
    if (
      path.startsWith('/investments/holdings/') &&
      path.endsWith('/cost-basis') &&
      method === 'PATCH'
    ) {
      const id = path.split('/')[3]
      const body = request.postDataJSON() as Record<string, string | null>
      const holding = holdings.find((item) => item.id === id)
      if (!holding) return route.fulfill({ status: 404, body: '{}' })
      const manual =
        body.costBasis && String(body.costBasis).trim() !== '' ? Number(body.costBasis) : null
      const provider = holding.amountOriginal == null ? null : Number(holding.amountOriginal)
      const cost = manual ?? provider
      const balance = Number(holding.balance) || 0
      const income = Number(holding.incomeTotal) || 0
      const profit = cost == null ? null : balance + income - cost
      const updated = {
        ...holding,
        manualCostBasis: manual == null ? null : manual.toFixed(2),
        costBasis: cost == null ? null : cost.toFixed(2),
        costBasisSource: manual != null ? 'MANUAL' : provider != null ? 'PROVIDER' : 'UNAVAILABLE',
        estimatedProfit: profit == null ? null : profit.toFixed(2),
        estimatedReturnPercentage:
          profit == null || cost == null || cost <= 0 ? null : ((profit / cost) * 100).toFixed(2),
      }
      holdings = holdings.map((item) => (item.id === id ? updated : item))
      return route.fulfill({ status: 204 })
    }
    if (
      path.startsWith('/investments/holdings/') &&
      path.endsWith('/asset-class') &&
      method === 'PATCH'
    ) {
      const id = path.split('/')[3]
      const body = request.postDataJSON() as Record<string, string | null>
      const holding = holdings.find((item) => item.id === id)
      if (!holding) return route.fulfill({ status: 404, body: '{}' })
      const assetClass = body.assetClass ? String(body.assetClass) : null
      const updated = {
        ...holding,
        userAssetClass: assetClass,
        assetClass: assetClass ?? holding.assetClass,
        classificationSource: assetClass ? 'USER' : holding.classificationSource,
      }
      holdings = holdings.map((item) => (item.id === id ? updated : item))
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
}

test('mostra resumo, filtros e proventos sem recomendação', async ({ page }) => {
  await installStub(page)
  await page.goto('/investments')

  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Cobertura das conexões' })).toHaveCount(0)
  await expect(page.getByText('R$ 1.500,00').first()).toBeVisible()

  const holdings = page.getByRole('region', { name: 'Posições importadas' })
  await expect(holdings.getByText('FII Alfa')).toBeVisible()
  await expect(holdings.getByText('FII Gama')).toHaveCount(0)
  await page.getByLabel('Situação').selectOption('CLOSED')
  await expect(holdings.getByText('FII Gama')).toBeVisible()

  await expect(page.getByText(/R\$\s*12,50/).first()).toBeVisible()
  await expect(page.getByText(/não há recomendação de compra, venda, alocação/i)).toBeVisible()
  await expect(page.getByRole('button', { name: /comprar|vender|investir agora/i })).toHaveCount(0)
})

test('registra provento manual e custo informado', async ({ page }) => {
  await installStub(page)
  await page.goto('/investments')

  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  const income = page.getByRole('region', { name: 'Proventos' })
  await page.locator('summary').filter({ hasText: 'Registrar provento manual' }).click()
  await income.getByLabel('Posição').selectOption({ label: 'FII Alfa (AAAA11)' })
  await income.getByLabel('Valor (BRL)').fill('7.50')
  await income.getByLabel('Descrição (opcional)').fill('Dividendo manual')
  await income.getByRole('button', { name: 'Registrar provento' }).click()

  await expect(income.getByRole('cell', { name: 'Dividendo' })).toBeVisible()
  await expect(income.getByText('R$ 20,00').first()).toBeVisible()

  const card = page.getByRole('listitem').filter({ hasText: 'FII Alfa' })
  await card.getByText('Detalhes do provedor').click()
  await card.getByLabel('Custo informado (BRL)').fill('900.00')
  await card.getByRole('button', { name: 'Salvar custo' }).click()
  await expect(card.getByText('Custo informado atualizado.')).toBeVisible()
  await expect(card.getByText('R$ 112,50')).toBeVisible()
})

test('explica ausência de proventos sem inventar dados', async ({ page }) => {
  await installStub(page, {
    overview: {
      ...OVERVIEW,
      providerValue: '0.00',
      totalValue: '0.00',
      activeCount: 0,
      closedCount: 0,
      incomeLast12Months: '0.00',
      incomeSource: 'UNAVAILABLE',
      profitReported: null,
      profitCoverageCount: 0,
      allocation: [],
      coverage: [],
    },
    holdings: [],
    income: {
      ...INCOME,
      total: '0.00',
      source: 'UNAVAILABLE',
      byMonth: [],
      items: [],
    },
  })
  await page.goto('/investments')

  await expect(page.getByRole('heading', { name: 'Cobertura das conexões' })).toHaveCount(0)
  await expect(page.getByText(/nenhum provento informado pelo provedor/i)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Posições manuais' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /importar/i })).toHaveCount(0)
})
