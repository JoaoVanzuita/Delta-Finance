import { expect, test, type Page } from '@playwright/test'

/**
 * E2E de contas/lançamentos manuais e reconciliação (stub de API).
 *
 * Prova que o fluxo funciona sem integração bancária disponível e que a
 * confirmação de movimento interno é sempre explícita.
 */

interface State {
  accounts: Array<Record<string, unknown>>
  transactions: Array<Record<string, unknown>>
  matches: Array<Record<string, unknown>>
}

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

function account(overrides: Record<string, unknown> = {}) {
  return {
    id: 'acc-manual',
    type: 'CASH',
    origin: 'MANUAL',
    alias: 'Carteira',
    currency: 'BRL',
    currentBalance: '100.00',
    status: 'ACTIVE',
    balanceReferenceAt: '2026-10-23T12:00:00Z',
    ...overrides,
  }
}

async function installStub(page: Page): Promise<State> {
  const state: State = { accounts: [account()], transactions: [], matches: [] }

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
    if (path === '/accounts' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.accounts }),
      })
    }
    if (path === '/accounts' && method === 'POST') {
      const created = account({
        alias: 'Carteira nova',
        currentBalance: '50.00',
      })
      state.accounts = [...state.accounts, created]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path.startsWith('/accounts/') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.accounts[0]),
      })
    }
    if (path === '/categories' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 'cat-1', name: 'Alimentação', kind: 'EXPENSE', predefined: true, active: true },
          ],
        }),
      })
    }
    if (path === '/transactions' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.transactions, hasMore: false }),
      })
    }
    if (path === '/transactions' && method === 'POST') {
      const created = {
        id: 'tx-manual',
        accountId: 'acc-manual',
        source: 'MANUAL',
        type: 'DEBIT',
        status: 'POSTED',
        postedOn: '2026-10-23',
        amount: '-25.00',
        currency: 'BRL',
        description: 'Feira',
      }
      state.transactions = [created, ...state.transactions]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path === '/internal-matches' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.matches, hasMore: false }),
      })
    }
    if (path.endsWith('/confirm') && method === 'POST') {
      state.matches = []
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'match-1',
          kind: 'TRANSFER',
          debitTransactionId: 'tx-debit',
          creditTransactionId: 'tx-credit',
          score: 100,
          status: 'CONFIRMED',
          criteriaVersion: 'v1',
          decidedAt: '2026-10-23T12:00:00Z',
        }),
      })
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'NOT_FOUND', detail: 'Não encontrado.' }),
    })
  })

  return state
}

test('cria conta manual e lançamento sem integração bancária', async ({ page }) => {
  await installStub(page)

  await page.goto('/accounts/new')
  await page.getByLabel('Apelido').fill('Carteira nova')
  await page.getByLabel('Saldo de referência (R$)').fill('50,00')
  await page.getByRole('button', { name: 'Criar conta manual' }).click()

  await expect(page).toHaveURL(/\/accounts\/acc-manual$/)
  await expect(page.getByRole('heading', { name: 'Carteira' })).toBeVisible()

  await page.goto('/transactions/new')
  await page.getByLabel('Conta').selectOption('acc-manual')
  await page.getByLabel('Valor (R$)').fill('25,00')
  await page.getByLabel('Descrição').fill('Feira')
  await page.getByRole('button', { name: 'Registrar lançamento' }).click()

  await expect(page.getByText('Lançamento registrado.')).toBeVisible()
})

test('reconciliação exige confirmação explícita', async ({ page }) => {
  const state = await installStub(page)
  state.transactions = [
    {
      id: 'tx-debit',
      accountId: 'acc-manual',
      source: 'MANUAL',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-22',
      amount: '-100.00',
      currency: 'BRL',
      description: 'Transferência para poupança',
    },
    {
      id: 'tx-credit',
      accountId: 'acc-other',
      source: 'MANUAL',
      type: 'CREDIT',
      status: 'POSTED',
      postedOn: '2026-10-22',
      amount: '100.00',
      currency: 'BRL',
      description: 'Entrada da poupança',
    },
  ]
  state.matches = [
    {
      id: 'match-1',
      kind: 'TRANSFER',
      debitTransactionId: 'tx-debit',
      creditTransactionId: 'tx-credit',
      score: 100,
      status: 'PENDING',
      criteriaVersion: 'v1',
    },
  ]

  await page.goto('/reconciliation')
  await expect(page.getByRole('heading', { name: 'Movimentos internos' })).toBeVisible()
  await expect(page.getByText('Transferência entre contas')).toBeVisible()
  await page.getByRole('button', { name: 'Confirmar' }).click()

  await expect(page.getByText(/Nenhuma sugestão pendente/)).toBeVisible()
})
