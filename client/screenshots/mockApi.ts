/**
 * Handler de API (mock) que serve o banco de dados de teste via `page.route`.
 *
 * Instala o cookie CSRF e intercepta todas as chamadas à API (`/api/v1`),
 * devolvendo os dados do `Db`. Segue o padrão dos specs e2e existentes, mas
 * centraliza as rotas de todos os domínios para percorrer a aplicação inteira.
 */
import type { Page, Route } from '@playwright/test'

import type { Db } from './db'
import { seedDb } from './db'

function json(route: Route, status: number, body: unknown): Promise<void> {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function noContent(route: Route): Promise<void> {
  return route.fulfill({ status: 204 })
}

function problem(route: Route, status: number, code: string, detail: string): Promise<void> {
  return route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({
      type: `urn:delta-finance:error:${code.toLowerCase()}`,
      title: status >= 500 ? 'Service Unavailable' : 'Bad Request',
      status,
      code,
      detail,
      requestId: 'req-screenshot',
    }),
  })
}

function matches(path: string, patterns: readonly string[] | undefined): boolean {
  return Boolean(patterns?.some((pattern) => path === pattern || path.startsWith(`${pattern}/`)))
}

export interface MockApiOptions {
  readonly delayMs?: number
  /** Rotas que devem responder com atraso (estado de carregamento). */
  readonly delayPaths?: readonly string[]
  /** Rotas que devem responder com erro controlado (estado de erro). */
  readonly failPaths?: readonly string[]
  /** `false` simula visitante sem sessão. */
  readonly authenticated?: boolean
}

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

function notFound(route: Route): Promise<void> {
  return json(route, 404, {
    type: 'urn:delta-finance:error:not_found',
    title: 'Not Found',
    status: 404,
    code: 'NOT_FOUND',
    detail: 'Não encontrado.',
  })
}

/** Resumo do dashboard coerente com as contas/cartões do banco de teste. */
function dashboardSummary(db: Db) {
  const empty = db.accounts.length === 0 && db.transactions.length === 0
  const activeBrlAccounts = db.accounts.filter(
    (account) =>
      account.status === 'ACTIVE' &&
      account.currency === 'BRL' &&
      ['BANK', 'CASH', 'WALLET'].includes(account.type),
  )
  const currentBalance = activeBrlAccounts
    .reduce((sum, account) => sum + Number(account.currentBalance), 0)
    .toFixed(2)

  return {
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    periodResult: empty ? '0.00' : '12627.90',
    currentBalance,
    currentBalanceReferenceAt: empty ? null : '2026-10-27T09:30:00Z',
    income: empty ? '0.00' : '16200.00',
    expenses: empty ? '0.00' : '3572.10',
    pendingCount: empty ? 0 : 1,
    pendingIncome: '0.00',
    pendingExpenses: empty ? '0.00' : '99.90',
    pendingInternalMatches: db.internalMatches.length,
    lastSuccessfulSyncAt: empty ? null : '2026-10-27T09:30:00Z',
    cardOutstandingBalance: cardOverview(db).totalOutstanding,
    investmentContributions: empty ? '0.00' : '3000.00',
    investmentWithdrawals: empty ? '0.00' : '500.00',
    netInvestmentContributions: empty ? '0.00' : '2500.00',
    investmentTrackingAvailable: !empty,
  }
}

function cardOverview(db: Db) {
  const items = db.cardOverviewItems
  return {
    totalOutstanding: items.reduce((sum, item) => sum + Number(item.outstanding), 0).toFixed(2),
    totalLimit: items.reduce((sum, item) => sum + Number(item.creditLimit ?? 0), 0).toFixed(2),
    totalUsed: items.reduce((sum, item) => sum + Number(item.usedAmount), 0).toFixed(2),
    nextDueDate: items.find((item) => item.nextDueDate)?.nextDueDate ?? null,
    items,
  }
}

function scaledMoney(value: string, factor: number): string {
  return (Number(value) * factor).toFixed(2)
}

function wealthHistory(db: Db) {
  const overview = wealthOverview(db)
  return [
    {
      id: 'snap-3',
      referenceDate: '2026-10-31',
      capturedAt: '2026-11-01T03:10:00Z',
      trigger: 'SCHEDULED',
      completeness: overview.completeness,
      grossAssets: overview.grossAssets,
      liabilities: overview.liabilities,
      netWorth: overview.netWorth,
      itemCount: overview.items.length,
      includedItemCount: overview.items.filter((item) => item.included).length,
      hasPartialData: overview.completeness !== 'COMPLETE',
    },
    {
      id: 'snap-2',
      referenceDate: '2026-09-30',
      capturedAt: '2026-10-01T03:10:00Z',
      trigger: 'SCHEDULED',
      completeness: 'COMPLETE',
      grossAssets: scaledMoney(overview.grossAssets, 0.94),
      liabilities: scaledMoney(overview.liabilities, 0.9),
      netWorth: scaledMoney(overview.netWorth, 0.94),
      itemCount: overview.items.length,
      includedItemCount: overview.items.filter((item) => item.included).length,
      hasPartialData: false,
    },
    {
      id: 'snap-1',
      referenceDate: '2026-08-31',
      capturedAt: '2026-09-01T03:10:00Z',
      trigger: 'SCHEDULED',
      completeness: 'COMPLETE',
      grossAssets: scaledMoney(overview.grossAssets, 0.88),
      liabilities: scaledMoney(overview.liabilities, 0.8),
      netWorth: scaledMoney(overview.netWorth, 0.88),
      itemCount: overview.items.length,
      includedItemCount: overview.items.filter((item) => item.included).length,
      hasPartialData: false,
    },
  ]
}

function wealthSnapshotDetail(db: Db, id: string) {
  const summary = wealthHistory(db).find((snapshot) => snapshot.id === id) ?? wealthHistory(db)[0]
  const overview = wealthOverview(db)
  return {
    ...summary,
    items: overview.items.map((item) => ({
      ...item,
      currency: 'BRL',
      assetClass:
        item.sourceType === 'INVESTMENT'
          ? (db.holdings.find((holding) => holding.id === item.sourceId)?.assetClass ?? null)
          : null,
      exclusionReason: 'exclusionReason' in item ? (item.exclusionReason ?? null) : null,
      referenceDate: item.referenceDate ?? null,
    })),
  }
}

function investmentHistory(db: Db) {
  const overview = db.investmentOverview as {
    totalValue: string
    allocation: Array<{ assetClass: string; value: string; count: number; percentage: string }>
  }
  const allocation = overview.allocation
  return [
    {
      snapshotId: 'snap-1',
      referenceDate: '2026-08-31',
      totalValue: scaledMoney(overview.totalValue, 0.88),
      completeness: 'COMPLETE',
      allocation: allocation.map((entry) => ({
        ...entry,
        value: scaledMoney(entry.value, 0.88),
      })),
    },
    {
      snapshotId: 'snap-2',
      referenceDate: '2026-09-30',
      totalValue: scaledMoney(overview.totalValue, 0.94),
      completeness: 'COMPLETE',
      allocation: allocation.map((entry) => ({
        ...entry,
        value: scaledMoney(entry.value, 0.94),
      })),
    },
    {
      snapshotId: 'snap-3',
      referenceDate: '2026-10-31',
      totalValue: overview.totalValue,
      completeness: 'COMPLETE',
      allocation,
    },
  ]
}

function wealthOverview(db: Db) {
  const items = [
    ...db.wealthItems,
    ...db.wealthManualItems.map((item) => ({
      sourceType: item.kind === 'ASSET' ? 'MANUAL_ASSET' : 'MANUAL_LIABILITY',
      sourceId: item.id,
      description: item.description,
      value: item.referenceValue,
      included: true,
      origin: 'MANUAL',
      referenceDate: item.referenceDate,
      stale: false,
    })),
  ]
  const gross = items
    .filter(
      (item) =>
        item.included && item.sourceType !== 'CARD' && item.sourceType !== 'MANUAL_LIABILITY',
    )
    .reduce((sum, item) => sum + Number(item.value), 0)
  const liabilities = items
    .filter(
      (item) =>
        item.included && (item.sourceType === 'CARD' || item.sourceType === 'MANUAL_LIABILITY'),
    )
    .reduce((sum, item) => sum + Number(item.value), 0)

  return {
    referenceDate: '2026-10-27',
    grossAssets: gross.toFixed(2),
    liabilities: liabilities.toFixed(2),
    netWorth: (gross - liabilities).toFixed(2),
    completeness: 'PARTIAL',
    hasData: true,
    items,
  }
}

function investmentContribution() {
  return {
    from: '2026-09-01',
    to: '2026-11-01',
    currency: 'BRL',
    contributions: '3000.00',
    withdrawals: '500.00',
    netContributions: '2500.00',
    applied: '2800.00',
    redeemed: '0.00',
    income: '12.50',
    fees: '15.00',
    idleCash: '185.00',
    portfolioValue: '1500.00',
    cashBalance: '5432.10',
    settlementDebits: '2815.00',
    result: '600.00',
    resultPercentage: '6.67',
    cumulativeContributions: '9000.00',
    cumulativeWithdrawals: '1000.00',
    feeSource: 'DERIVED',
    reconciliationDifference: '0.00',
    contributionCount: 3,
    withdrawalCount: 1,
    applicationCount: 2,
    positionCount: 2,
    months: [
      {
        month: '2026-11',
        contributions: '1000.00',
        withdrawals: '0.00',
        netContributions: '1000.00',
        applied: '1000.00',
        redeemed: '0.00',
        income: '0.00',
        fees: '5.00',
        idleCash: '0.00',
      },
      {
        month: '2026-10',
        contributions: '1000.00',
        withdrawals: '500.00',
        netContributions: '500.00',
        applied: '1000.00',
        redeemed: '0.00',
        income: '12.50',
        fees: '5.00',
        idleCash: '185.00',
      },
      {
        month: '2026-09',
        contributions: '1000.00',
        withdrawals: '0.00',
        netContributions: '1000.00',
        applied: '800.00',
        redeemed: '0.00',
        income: '0.00',
        fees: '5.00',
        idleCash: '0.00',
      },
    ],
    flows: [
      {
        date: '2026-10-05',
        kind: 'CONTRIBUTION',
        amount: '1000.00',
        description: 'Transferência para corretora',
        accountId: 'acc-bank',
        connectionId: 'connection-1',
        transactionId: 'tx-transfer-out',
        investmentId: null,
        investmentName: null,
        investmentCode: null,
        source: 'BANK',
        status: 'CONFIRMED',
      },
      {
        date: '2026-10-08',
        kind: 'APPLICATION',
        amount: '-1000.00',
        description: 'Compra de FII Alfa',
        accountId: null,
        connectionId: 'connection-1',
        transactionId: null,
        investmentId: 'holding-1',
        investmentName: 'FII Alfa',
        investmentCode: 'AAAA11',
        source: 'PROVIDER',
        status: 'PROVIDER',
      },
      {
        date: '2026-10-20',
        kind: 'WITHDRAWAL',
        amount: '-500.00',
        description: 'Resgate para conta corrente',
        accountId: 'acc-bank',
        connectionId: 'connection-1',
        transactionId: 'tx-transfer-in',
        investmentId: null,
        investmentName: null,
        investmentCode: null,
        source: 'BANK',
        status: 'CONFIRMED',
      },
    ],
    sources: [
      {
        connectionId: 'connection-1',
        name: 'Banco Alfa',
        accountCount: 2,
        positionCount: 2,
        perimeterSource: 'ACCOUNT_FLAG',
      },
    ],
  }
}

function income(db: Db) {
  const items = [...db.incomeItems]
  const total = items.reduce((sum, item) => sum + Number(item.amount), 0)
  const byMonth: Array<{ month: string; amount: string; count: number }> = []
  const months = new Map<string, { amount: number; count: number }>()
  for (const item of items) {
    const month = item.date.slice(0, 7)
    const entry = months.get(month) ?? { amount: 0, count: 0 }
    entry.amount += Number(item.amount)
    entry.count += 1
    months.set(month, entry)
  }
  for (const [month, entry] of months) {
    byMonth.push({ month, amount: entry.amount.toFixed(2), count: entry.count })
  }
  const hasManual = items.some((item) => item.source === 'MANUAL')
  const hasProvider = items.some((item) => item.source === 'PROVIDER')
  return {
    from: '2025-10-27',
    to: '2026-10-27',
    total: total.toFixed(2),
    source: hasManual && hasProvider ? 'MIXED' : hasProvider ? 'PROVIDER' : 'MANUAL',
    byMonth,
    items,
  }
}

/**
 * Instala o mock da API no `page` fornecido.
 *
 * O banco passado é clonado de forma rasa por chamada; cada teste recebe sua
 * própria instância para evitar vazamento de estado entre capturas.
 */
export async function installMockApi(
  page: Page,
  db: Db = seedDb(),
  options: MockApiOptions = {},
): Promise<void> {
  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (matches(path, options.delayPaths)) {
      await delay(options.delayMs ?? 1_500)
    }
    if (matches(path, options.failPaths)) {
      return problem(
        route,
        503,
        'TEMPORARILY_UNAVAILABLE',
        'Não foi possível carregar os dados agora. Tente novamente em instantes.',
      )
    }

    // Identidade e sessão.
    if (path === '/auth/csrf') return noContent(route)
    if (path === '/auth/me') {
      if (options.authenticated === false) {
        return problem(route, 401, 'UNAUTHENTICATED', 'Sessão não encontrada.')
      }
      return json(route, 200, db.user)
    }
    if (path === '/auth/login' && method === 'POST') return json(route, 200, db.user)
    if (path === '/auth/register' && method === 'POST') return json(route, 201, db.user)
    if (path === '/auth/logout' && method === 'POST') return noContent(route)
    if (path === '/auth/password' && method === 'PUT') return noContent(route)

    // Contas.
    if (path === '/accounts' && method === 'GET') {
      return json(route, 200, { items: db.accounts })
    }
    if (path === '/accounts' && method === 'POST') {
      return json(route, 201, { ...(db.accounts[0] ?? {}), id: 'acc-new' })
    }
    if (path.startsWith('/accounts/') && path.endsWith('/reserved-balances') && method === 'GET') {
      const accountId = path.split('/')[2]
      const items = db.reservedBalances.filter((item) => item.accountId === accountId)
      return json(route, 200, { items })
    }
    if (
      path.startsWith('/accounts/') &&
      path.endsWith('/reserved-balance-treatment') &&
      method === 'PUT'
    ) {
      const accountId = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === accountId)
      return account
        ? json(route, 200, {
            ...account,
            reservedBalanceTreatment: 'SEPARATE_ASSET',
            reservedBalanceTreatmentSource: 'ACCOUNT',
          })
        : notFound(route)
    }
    if (path.startsWith('/accounts/') && path.endsWith('/investment') && method === 'PUT') {
      const accountId = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === accountId)
      return account ? json(route, 200, { ...account, investmentAccount: true }) : notFound(route)
    }
    if (path.startsWith('/accounts/') && method === 'GET') {
      const id = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === id)
      return account ? json(route, 200, account) : notFound(route)
    }
    if (path.startsWith('/accounts/') && path.endsWith('/display-name') && method === 'PUT') {
      const id = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === id)
      return account ? json(route, 200, account) : notFound(route)
    }
    if (path.startsWith('/accounts/') && path.endsWith('/display-name') && method === 'DELETE') {
      const id = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === id)
      return account ? json(route, 200, account) : notFound(route)
    }
    if (path.startsWith('/accounts/') && method === 'PATCH') {
      const id = path.split('/')[2]
      const account = db.accounts.find((item) => item.id === id)
      return account ? json(route, 200, account) : notFound(route)
    }
    if (path.startsWith('/reserved-balances/') && path.endsWith('/display-name')) {
      const id = path.split('/')[2]
      const reserved = db.reservedBalances.find((item) => item.id === id)
      if (!reserved) return notFound(route)
      if (method === 'DELETE') {
        return json(route, 200, {
          ...reserved,
          name: reserved.providerName ?? reserved.name,
          nameCustomized: false,
        })
      }
      return json(route, 200, { ...reserved, name: 'Reserva de emergência', nameCustomized: true })
    }

    // Categorias.
    if (path === '/categories' && method === 'GET') {
      return json(route, 200, { items: db.categories })
    }
    if (path === '/categories' && method === 'POST') {
      return json(route, 201, { id: 'cat-new', name: 'Nova', kind: 'EXPENSE', active: true })
    }
    if (path.startsWith('/categories/') && method === 'PATCH') {
      return json(route, 200, { id: 'cat-1', name: 'Editada', kind: 'EXPENSE', active: true })
    }
    if (path.startsWith('/categories/') && method === 'DELETE') {
      return noContent(route)
    }

    // Cartões.
    if (path === '/cards/overview' && method === 'GET') {
      return json(route, 200, cardOverview(db))
    }
    if (path.startsWith('/cards/') && path.endsWith('/profile') && method === 'PUT') {
      return json(
        route,
        200,
        db.accounts.find((a) => a.id === path.split('/')[2])?.cardProfile ?? {},
      )
    }

    // Transações (com filtros por conta/categoria/estado/tipo/origem).
    if (path === '/transactions' && method === 'GET') {
      const accountId = url.searchParams.get('accountId')
      const categoryId = url.searchParams.get('categoryId')
      const status = url.searchParams.get('status')
      const type = url.searchParams.get('type')
      const origin = url.searchParams.get('origin')
      const items = db.transactions.filter((transaction) => {
        if (accountId && transaction.accountId !== accountId) return false
        if (categoryId && transaction.categoryId !== categoryId) return false
        if (status && transaction.status !== status) return false
        if (type && transaction.type !== type) return false
        if (origin && transaction.source !== origin) return false
        return true
      })
      return json(route, 200, { items, hasMore: false })
    }
    if (path === '/transactions' && method === 'POST') {
      return json(route, 201, { id: 'tx-new', accountId: 'acc-cash', source: 'MANUAL' })
    }
    if (path.startsWith('/transactions/') && method === 'PATCH') {
      const id = path.split('/')[2]
      const transaction = db.transactions.find((item) => item.id === id)
      return transaction ? json(route, 200, transaction) : notFound(route)
    }
    if (path.startsWith('/transactions/') && method === 'DELETE') {
      return noContent(route)
    }

    // Reconciliação (movimentos internos).
    if (path.startsWith('/internal-matches') && method === 'GET') {
      return json(route, 200, { items: db.internalMatches, hasMore: false })
    }
    if (path.endsWith('/confirm') && method === 'POST') {
      return json(route, 200, { ...(db.internalMatches[0] ?? {}), status: 'CONFIRMED' })
    }
    if (path.endsWith('/reject') && method === 'POST') {
      return json(route, 200, { ...(db.internalMatches[0] ?? {}), status: 'REJECTED' })
    }

    // Faturas.
    if (path.startsWith('/cards/') && path.endsWith('/invoices') && method === 'GET') {
      const accountId = path.split('/')[2]
      const items = db.invoices.filter((invoice) => invoice.cardAccountId === accountId)
      return json(route, 200, { items, hasMore: false })
    }
    if (path.startsWith('/invoices/') && method === 'GET') {
      const id = path.split('/')[2]
      const invoice = db.invoices.find((item) => item.id === id)
      return invoice ? json(route, 200, invoice) : notFound(route)
    }
    if (path.startsWith('/invoices/') && path.endsWith('/items') && method === 'POST') {
      return json(route, 201, {
        id: 'item-new',
        invoiceId: 'inv-open',
        kind: 'MANUAL_ADJUSTMENT',
        reportingEffect: 'EXPENSE_INCREASE',
        source: 'MANUAL',
        description: 'Ajuste',
        amount: '10.00',
        occurredOn: '2026-10-27',
      })
    }
    if (path.startsWith('/invoice-items/') && method === 'PATCH') {
      return json(route, 200, {})
    }
    if (path.startsWith('/invoice-items/') && method === 'DELETE') {
      return noContent(route)
    }
    if (path.startsWith('/invoice-items/') && path.endsWith('/corrections') && method === 'GET') {
      return json(route, 200, [
        {
          id: 'corr-1',
          invoiceItemId: 'item-open-3',
          fieldName: 'amount',
          oldValue: '35.10',
          newValue: '30.10',
          reason: 'Ajuste de juros',
          changedAt: '2026-10-22T12:00:00Z',
        },
      ])
    }
    if (path.startsWith('/invoices/') && path.endsWith('/payments') && method === 'POST') {
      return json(route, 201, {
        id: 'pay-new',
        invoiceId: 'inv-open',
        paymentAccountId: 'acc-bank',
        amount: '100.00',
        paidOn: '2026-10-27',
        status: 'CONFIRMED',
        source: 'MANUAL',
      })
    }
    if (
      path.startsWith('/invoices/') &&
      path.endsWith('/reconcile-provider') &&
      method === 'POST'
    ) {
      return json(route, 200, db.invoices[0])
    }

    // Integração e conexões.
    if (path === '/integrations/pluggy' && method === 'GET') {
      return json(route, 200, db.integration)
    }
    if (path === '/integrations/pluggy' && method === 'PUT') {
      return json(route, 200, db.integration)
    }
    if (path === '/integrations/pluggy/validate' && method === 'POST') {
      return json(route, 200, {
        valid: true,
        message: 'Credenciais válidas.',
        itemValidated: false,
      })
    }
    if (path === '/integrations/pluggy' && method === 'DELETE') {
      return noContent(route)
    }
    if (path === '/connections' && method === 'GET') {
      return json(route, 200, { items: db.connections, hasMore: false })
    }
    if (path === '/connections' && method === 'POST') {
      return json(route, 201, {
        id: 'connection-2',
        itemIdMasked: 'item…-002',
        institutionName: 'Banco Beta',
        status: 'SYNCED',
        consentExpiresAt: '2027-10-22T12:00:00Z',
        lastSyncAt: '2026-10-27T09:30:00Z',
        accountCount: 1,
      })
    }
    if (path.startsWith('/connections/') && path.endsWith('/sync') && method === 'POST') {
      return json(route, 202, { jobId: 'job-1', status: 'QUEUED' })
    }
    if (
      path.startsWith('/connections/') &&
      path.endsWith('/reserved-balance-treatment') &&
      method === 'PUT'
    ) {
      const id = path.split('/')[2]
      const connection = db.connections.find((item) => item.id === id)
      return connection
        ? json(route, 200, { ...connection, reservedBalanceTreatment: 'SEPARATE_ASSET' })
        : notFound(route)
    }
    if (path.startsWith('/connections/') && method === 'DELETE') {
      return noContent(route)
    }

    // Sincronização.
    if (path === '/sync/status' && method === 'GET') {
      return json(route, 200, {
        integrations: [db.integration],
        connections: db.connections,
        pendingJobs: 1,
      })
    }
    if (path === '/sync/runs' && method === 'GET') {
      return json(route, 200, { items: db.syncRuns, hasMore: false })
    }

    // Investimentos.
    if (path === '/investments/history') {
      return json(route, 200, investmentHistory(db))
    }
    if (path === '/investments/contributions') {
      return json(route, 200, investmentContribution())
    }
    if (path === '/investments/overview') {
      return json(route, 200, db.investmentOverview)
    }
    if (path === '/investments/holdings' && method === 'GET') {
      const status = url.searchParams.get('status')
      const assetClass = url.searchParams.get('assetClass')
      const items = db.holdings.filter((holding) => {
        if (assetClass && holding.assetClass !== assetClass) return false
        if (!status || status === 'ALL') return true
        if (status === 'ACTIVE') return holding.status !== 'CLOSED'
        if (status === 'CLOSED') return holding.status === 'CLOSED'
        return holding.status === status
      })
      return json(route, 200, { items })
    }
    if (path.startsWith('/investments/holdings/') && method === 'GET') {
      const id = path.split('/')[3]
      const holding = db.holdings.find((item) => item.id === id)
      return holding ? json(route, 200, holding) : notFound(route)
    }
    if (path === '/investments/sources') {
      return json(route, 200, { items: (db.investmentOverview.coverage as unknown[]) ?? [] })
    }
    if (path === '/investments/income' && method === 'GET') {
      return json(route, 200, income(db))
    }
    if (path === '/investments/income' && method === 'POST') {
      return json(route, 201, {
        id: 'income-new',
        targetId: 'holding-1',
        incomeType: 'DIVIDEND',
        incomeDate: '2026-10-27',
        amount: '7.50',
        source: 'MANUAL',
        createdAt: '2026-10-27T12:00:00Z',
      })
    }
    if (path.startsWith('/investments/income/') && method === 'DELETE') {
      return noContent(route)
    }
    if (path.endsWith('/cost-basis') && method === 'PATCH') {
      return noContent(route)
    }
    if (path.endsWith('/asset-class') && method === 'PATCH') {
      return noContent(route)
    }

    // Patrimônio.
    if (path.startsWith('/wealth/reserved-balances/') && path.endsWith('/history')) {
      return json(route, 200, { items: db.reservedBalanceHistory })
    }
    if (path === '/wealth/history' && method === 'GET') {
      return json(route, 200, wealthHistory(db))
    }
    if (path.startsWith('/wealth/history/') && method === 'GET') {
      return json(route, 200, wealthSnapshotDetail(db, path.split('/').pop() ?? 'snap-3'))
    }
    if (path === '/wealth/history' && method === 'POST') {
      return json(route, 201, wealthSnapshotDetail(db, 'snap-3'))
    }
    if (path === '/wealth/overview') {
      return json(route, 200, wealthOverview(db))
    }
    if (path === '/wealth/manual-items' && method === 'GET') {
      return json(route, 200, { items: db.wealthManualItems })
    }
    if (path === '/wealth/manual-items' && method === 'POST') {
      return json(route, 201, { id: 'item-new', kind: 'ASSET', type: 'IMOVEL' })
    }
    if (path.startsWith('/wealth/manual-items/') && method === 'DELETE') {
      return noContent(route)
    }
    if (path.startsWith('/wealth/manual-items/') && method === 'PATCH') {
      return json(route, 200, { id: 'item-1' })
    }

    // Metas.
    if (path === '/goals' && method === 'GET') {
      return json(route, 200, { items: db.goals })
    }
    if (path.startsWith('/goals/') && path.endsWith('/progress') && method === 'GET') {
      const id = path.split('/')[2]
      const goal = db.goals.find((item) => item.id === id)
      return goal
        ? json(route, 200, { items: goal.progressHistory, hasMore: false })
        : notFound(route)
    }
    if (/^\/goals\/[^/]+$/.test(path) && method === 'GET') {
      const id = path.split('/')[2]
      const goal = db.goals.find((item) => item.id === id)
      return goal ? json(route, 200, goal) : notFound(route)
    }
    if (path === '/goals' && method === 'POST') {
      return json(route, 201, db.goals[0])
    }
    if (path.startsWith('/goals/') && path.endsWith('/status') && method === 'POST') {
      return json(route, 200, db.goals[0])
    }
    if (path.startsWith('/goals/') && method === 'PATCH') {
      return json(route, 200, db.goals[0])
    }
    if (path.startsWith('/goals/') && method === 'DELETE') {
      return noContent(route)
    }

    // Dashboard e relatórios.
    if (path === '/dashboard/summary') {
      return json(route, 200, dashboardSummary(db))
    }
    if (path === '/dashboard/categories') {
      if (db.accounts.length === 0 && db.transactions.length === 0) {
        return json(route, 200, [])
      }
      if (url.searchParams.get('direction') === 'INCOME') {
        return json(route, 200, [
          {
            categoryId: 'cat-salario',
            categoryName: 'Salário',
            amount: '15000.00',
            transactionCount: 1,
          },
          {
            categoryId: 'cat-rendimentos',
            categoryName: 'Rendimentos de investimentos',
            amount: '1000.00',
            transactionCount: 1,
          },
          {
            categoryId: 'cat-outros-receita',
            categoryName: 'Outros',
            amount: '200.00',
            transactionCount: 1,
          },
        ])
      }
      return json(route, 200, [
        {
          categoryId: 'cat-moradia',
          categoryName: 'Moradia',
          amount: '2500.00',
          transactionCount: 1,
        },
        {
          categoryId: 'cat-alimentacao',
          categoryName: 'Alimentação',
          amount: '540.00',
          transactionCount: 1,
        },
        { categoryId: 'cat-saude', categoryName: 'Saúde', amount: '300.00', transactionCount: 1 },
        { categoryId: 'cat-feira', categoryName: 'Feira', amount: '232.10', transactionCount: 1 },
      ])
    }
    if (path === '/dashboard/timeseries') {
      if (db.accounts.length === 0 && db.transactions.length === 0) {
        return json(route, 200, [])
      }
      return json(route, 200, [
        {
          label: '06/10',
          start: '2026-10-06',
          end: '2026-10-12',
          income: '15200.00',
          expenses: '2500.00',
          result: '12700.00',
        },
        {
          label: '13/10',
          start: '2026-10-13',
          end: '2026-10-19',
          income: '1000.00',
          expenses: '532.10',
          result: '467.90',
        },
        {
          label: '20/10',
          start: '2026-10-20',
          end: '2026-10-26',
          income: '0.00',
          expenses: '540.00',
          result: '-540.00',
        },
      ])
    }
    if (path === '/reports/entries') {
      if (db.accounts.length === 0 && db.transactions.length === 0) {
        return json(route, 200, { items: [], hasMore: false })
      }
      const categoryId = url.searchParams.get('categoryId')
      const accountId = url.searchParams.get('accountId')
      const type = url.searchParams.get('type')
      const origin = url.searchParams.get('origin')
      const entries = [
        {
          id: 'entry-1',
          source: 'TRANSACTION',
          sourceId: 'tx-aluguel',
          origin: 'IMPORTED',
          occurredOn: '2026-10-06',
          amount: '-2500.00',
          description: 'Aluguel',
          categoryId: 'cat-moradia',
          accountId: 'acc-bank',
          type: 'DEBIT',
        },
        {
          id: 'entry-2',
          source: 'TRANSACTION',
          sourceId: 'tx-feira',
          origin: 'MANUAL',
          occurredOn: '2026-10-12',
          amount: '-232.10',
          description: 'Feira da semana',
          categoryId: 'cat-feira',
          accountId: 'acc-cash',
          type: 'DEBIT',
        },
        {
          id: 'entry-3',
          source: 'TRANSACTION',
          sourceId: 'tx-mercado',
          origin: 'IMPORTED',
          occurredOn: '2026-10-18',
          amount: '-540.00',
          description: 'Mercado do mês',
          categoryId: 'cat-alimentacao',
          accountId: 'acc-bank',
          type: 'DEBIT',
        },
        {
          id: 'entry-4',
          source: 'TRANSACTION',
          sourceId: 'tx-farmacia',
          origin: 'IMPORTED',
          occurredOn: '2026-10-22',
          amount: '-300.00',
          description: 'Farmácia',
          categoryId: 'cat-saude',
          accountId: 'acc-bank',
          type: 'DEBIT',
        },
      ].filter(
        (entry) =>
          (!categoryId || entry.categoryId === categoryId) &&
          (!accountId || entry.accountId === accountId) &&
          (!type || entry.type === type) &&
          (!origin || entry.origin === origin),
      )
      return json(route, 200, { items: entries, hasMore: false })
    }

    // Privacidade.
    if (path === '/me/export') {
      return route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('PK\u0003\u0004delta-finance-export'),
      })
    }
    if (path === '/me/delete' && method === 'POST') {
      return route.fulfill({ status: 202 })
    }

    return notFound(route)
  })
}
