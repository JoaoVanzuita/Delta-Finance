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
    userAssetClass: null,
    profitSource: 'UNAVAILABLE',
    stale: false,
  },
]

const OVERVIEW: Record<string, unknown> = {
  referenceDate: TODAY,
  providerValue: '1500.00',
  manualValue: '125.00',
  totalValue: '1625.00',
  activeCount: 2,
  closedCount: 1,
  unavailableCount: 0,
  manualCount: 1,
  incomeLast12Months: '12.50',
  incomeSource: 'PROVIDER',
  profitReported: '50.00',
  profitCoverageCount: 1,
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
  duplicates: [],
}

const INCOME = {
  from: '2025-10-27',
  to: TODAY,
  total: '12.50',
  source: 'PROVIDER',
  byMonth: [{ month: '2026-10', amount: '12.50', count: 1 }],
  items: [
    {
      date: '2026-10-26',
      investmentId: 'holding-1',
      investmentName: 'FII Alfa',
      code: 'AAAA11',
      type: 'EQUITY',
      amount: '12.50',
      description: 'Rendimento',
      source: 'PROVIDER',
    },
  ],
}

const CONNECTIONS = {
  items: [
    {
      id: 'connection-1',
      itemIdMasked: 'item-…-1',
      institutionName: 'XP Investimentos',
      status: 'SYNCED',
      consentExpiresAt: '2027-10-27T12:00:00Z',
      lastSyncAt: '2026-10-27T12:00:00Z',
      accountCount: 2,
    },
    {
      id: 'connection-2',
      itemIdMasked: 'item-…-2',
      institutionName: 'Banco Beta',
      status: 'SYNCED',
      consentExpiresAt: '2027-10-27T12:00:00Z',
      lastSyncAt: '2026-10-27T12:00:00Z',
      accountCount: 1,
    },
  ],
  hasMore: false,
}

interface Position {
  id: string
  broker: 'XP' | 'SANTANDER' | 'OTHER'
  assetName: string
  ticker?: string
  quantity: string
  referencePrice: string
  total: string
  referenceDate: string
  notes?: string
  origin: 'MANUAL'
}

interface State {
  positions: Position[]
}

interface Overrides {
  readonly overview?: typeof OVERVIEW
  readonly holdings?: typeof HOLDINGS
  readonly income?: typeof INCOME
}

async function installStub(page: Page, overrides: Overrides = {}): Promise<State> {
  const state: State = {
    positions: [
      {
        id: 'pos-1',
        broker: 'XP',
        assetName: 'Petrobras PN',
        ticker: 'PETR4',
        quantity: '10',
        referencePrice: '12.5',
        total: '125.00',
        referenceDate: '2026-10-20',
        origin: 'MANUAL',
      },
    ],
  }
  const overview = overrides.overview ?? OVERVIEW
  const holdings = overrides.holdings ?? HOLDINGS
  const income = overrides.income ?? INCOME

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
    if (path === '/connections') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(CONNECTIONS),
      })
    }
    if (path === '/investments/overview') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(overview),
      })
    }
    if (path === '/investments/income') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(income),
      })
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
    if (path === '/investments/positions' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.positions, hasMore: false }),
      })
    }
    if (path === '/investments/positions' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, string>
      const created: Position = {
        id: `pos-${state.positions.length + 1}`,
        broker: body.broker as Position['broker'],
        assetName: body.assetName ?? '',
        ticker: body.ticker,
        quantity: body.quantity ?? '0',
        referencePrice: body.referencePrice ?? '33.33333333',
        total: body.totalValue ?? '100.00',
        referenceDate: body.referenceDate ?? '2026-10-22',
        notes: body.notes,
        origin: 'MANUAL',
      }
      state.positions = [created, ...state.positions]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path.startsWith('/investments/positions/') && method === 'PATCH') {
      const id = path.split('/').pop()
      const body = request.postDataJSON() as Record<string, string>
      const updated: Position = {
        id: id ?? 'pos-1',
        broker: body.broker as Position['broker'],
        assetName: body.assetName ?? '',
        ticker: body.ticker,
        quantity: body.quantity ?? '0',
        referencePrice: body.referencePrice ?? '0',
        total: '200.00',
        referenceDate: body.referenceDate ?? '2026-10-23',
        notes: body.notes,
        origin: 'MANUAL',
      }
      state.positions = state.positions.map((item) => (item.id === updated.id ? updated : item))
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(updated),
      })
    }
    if (path.startsWith('/investments/positions/') && method === 'DELETE') {
      const id = path.split('/').pop()
      state.positions = state.positions.filter((item) => item.id !== id)
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })

  return state
}

test('mostra resumo, cobertura, filtros e proventos sem recomendação', async ({ page }) => {
  await installStub(page)
  await page.goto('/investments')

  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Cobertura das conexões' })).toBeVisible()
  await expect(page.getByText('R$ 1.625,00').first()).toBeVisible()
  const coverage = page.getByRole('region', { name: 'Cobertura das conexões' })
  await expect(coverage.getByText('XP Investimentos', { exact: true })).toBeVisible()
  await expect(coverage.getByText('Banco Beta', { exact: true })).toBeVisible()
  await expect(coverage.getByText('Compartilha investimentos', { exact: true })).toBeVisible()
  await expect(coverage.getByText('Não compartilha investimentos', { exact: true })).toBeVisible()

  const holdings = page.getByRole('region', { name: 'Posições importadas' })
  await expect(holdings.getByText('FII Alfa')).toBeVisible()
  await expect(holdings.getByText('FII Gama')).toHaveCount(0)
  await page.getByLabel('Situação').selectOption('CLOSED')
  await expect(holdings.getByText('FII Gama')).toBeVisible()

  await expect(page.getByText(/R\$\s*12,50/).first()).toBeVisible()
  await expect(page.getByText(/não há recomendação de compra, venda, alocação/i)).toBeVisible()
  await expect(page.getByRole('button', { name: /comprar|vender|investir agora/i })).toHaveCount(0)
})

test('registra, edita e exclui posição manual no dashboard', async ({ page }) => {
  const state = await installStub(page)
  await page.goto('/investments')

  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  const manual = page.getByRole('region', { name: 'Posições manuais' })
  await expect(manual.getByText('Petrobras PN')).toBeVisible()
  await expect(manual.getByText(/R\$\s*125,00/)).toBeVisible()

  await manual.getByLabel('Corretora').selectOption('SANTANDER')
  await manual.getByLabel('Ativo').fill('Fundo imobiliário')
  await manual.getByLabel('Ticker').fill('HGLG11')
  await manual.getByLabel('Quantidade').fill('3')
  await manual.getByLabel('Valor total').check()
  await manual.getByLabel('Valor total informado').fill('100.00')
  await manual.getByLabel('Data de referência').fill('2026-10-22')
  await manual.getByRole('button', { name: 'Adicionar posição' }).click()

  await expect(manual.getByText('Fundo imobiliário')).toBeVisible()
  await expect(manual.getByText('Posição manual adicionada.')).toBeVisible()
  expect(state.positions).toHaveLength(2)

  const newRow = manual.getByRole('listitem').filter({ hasText: 'Fundo imobiliário' })
  await expect(newRow.getByText(/R\$\s*100,00/)).toBeVisible()
  await newRow.getByRole('button', { name: 'Editar' }).click()
  await newRow.getByLabel('Ativo').fill('Fundo imobiliário atualizado')
  await newRow.getByRole('button', { name: 'Salvar posição' }).click()
  await expect(manual.getByText('Fundo imobiliário atualizado')).toBeVisible()

  const editedRow = manual.getByRole('listitem').filter({ hasText: 'Fundo imobiliário atualizado' })
  await editedRow.getByRole('button', { name: 'Excluir' }).click()
  await editedRow.getByRole('button', { name: 'Confirmar exclusão' }).click()
  await expect(manual.getByText('Fundo imobiliário atualizado')).toHaveCount(0)
})

test('explica ausência de cobertura e de proventos sem inventar dados', async ({ page }) => {
  await installStub(page, {
    overview: {
      ...OVERVIEW,
      providerValue: '0.00',
      manualValue: '0.00',
      totalValue: '0.00',
      activeCount: 0,
      closedCount: 0,
      manualCount: 0,
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
  await page.route('**/api/v1/investments/positions?**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [], hasMore: false }),
    })
  })

  await page.goto('/investments')

  await expect(page.getByText('Nenhuma conexão verificada ainda')).toBeVisible()
  await expect(page.getByText(/nenhum provento informado pelo provedor/i)).toBeVisible()
  await expect(page.getByText('Nenhuma posição manual')).toBeVisible()
  await expect(page.getByRole('button', { name: /importar/i })).toHaveCount(0)
})
