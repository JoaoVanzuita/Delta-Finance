import { expect, test, type Page } from '@playwright/test'

/**
 * E2E do dashboard (T103).
 *
 * Prova que resultado do período e saldo atual aparecem separados, que o
 * drill-down abre as entradas do total e que uma transação pendente entra nos
 * totais uma única vez após a efetivação.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const ACCOUNTS = {
  items: [
    {
      id: 'acc-cash',
      type: 'CASH',
      origin: 'MANUAL',
      alias: 'Carteira',
      currency: 'BRL',
      currentBalance: '100.00',
      status: 'ACTIVE',
    },
    {
      id: 'acc-card',
      type: 'CREDIT',
      origin: 'IMPORTED',
      alias: 'Cartão',
      currency: 'BRL',
      currentBalance: '0.00',
      status: 'ACTIVE',
    },
  ],
}

const CATEGORY_LIST = {
  items: [
    { id: 'cat-feira', name: 'Feira', kind: 'EXPENSE', predefined: false, active: true },
    { id: 'cat-outros', name: 'Sem categoria', kind: 'OTHER', predefined: true, active: true },
  ],
}

interface StubState {
  summaryCalls: number
}

async function installStub(page: Page): Promise<StubState> {
  const state: StubState = { summaryCalls: 0 }

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/accounts') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ACCOUNTS),
      })
    }
    if (path === '/categories') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(CATEGORY_LIST),
      })
    }
    if (path === '/dashboard/summary') {
      state.summaryCalls += 1
      const posted = state.summaryCalls > 1
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          periodStart: '2026-10-01',
          periodEnd: '2026-10-31',
          periodResult: posted ? '470.00' : '500.00',
          currentBalance: '1100.00',
          currentBalanceReferenceAt: '2026-10-23T12:00:00Z',
          income: '1000.00',
          expenses: posted ? '530.00' : '500.00',
          pendingCount: posted ? 0 : 1,
          pendingIncome: '0.00',
          pendingExpenses: posted ? '0.00' : '30.00',
          pendingInternalMatches: 0,
          lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
          cardOutstandingBalance: '310.00',
        }),
      })
    }
    if (path === '/dashboard/categories') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            categoryId: 'cat-feira',
            categoryName: 'Feira',
            amount: '200.00',
            transactionCount: 1,
          },
          {
            categoryId: '00000000-0000-0000-0000-000000000000',
            categoryName: 'Sem categoria',
            amount: '300.00',
            transactionCount: 3,
          },
        ]),
      })
    }
    if (path === '/dashboard/timeseries') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            label: '03/10',
            start: '2026-10-03',
            end: '2026-10-04',
            income: '1000.00',
            expenses: '500.00',
            result: '500.00',
          },
        ]),
      })
    }
    if (path === '/reports/entries') {
      const categoryId = url.searchParams.get('categoryId')
      const posted = state.summaryCalls > 1
      const items = [
        {
          id: 'entry-feira',
          source: 'TRANSACTION',
          sourceId: 'entry-feira',
          origin: 'MANUAL',
          occurredOn: '2026-10-03',
          amount: '-200.00',
          description: 'Feira da semana',
          categoryId: 'cat-feira',
          accountId: 'acc-cash',
          type: 'DEBIT',
        },
        ...(posted
          ? [
              {
                id: 'tx-pending',
                source: 'TRANSACTION',
                sourceId: 'tx-pending',
                origin: 'IMPORTED',
                occurredOn: '2026-10-06',
                amount: '-30.00',
                description: 'Pendente efetivada',
                categoryId: 'cat-outros',
                accountId: 'acc-cash',
                type: 'DEBIT',
              },
            ]
          : []),
      ]
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: categoryId === 'cat-feira' ? items.slice(0, 1) : items,
          hasMore: false,
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

test('mostra totais separados, abre drill-down e efetiva pendente uma única vez', async ({
  page,
}) => {
  await installStub(page)

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 3, name: 'Resultado do período' })).toBeVisible()
  await expect(
    page.getByRole('heading', { level: 3, name: 'Saldo atual consolidado' }),
  ).toBeVisible()
  await expect(page.getByText(/R\$\s*500,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*1\.100,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*310,00/).first()).toBeVisible()
  await expect(page.getByText(/Total de despesas no período/)).toBeVisible()

  await page.getByRole('button', { name: /Feira/ }).click()
  await expect(page.getByText('Categoria: Feira')).toBeVisible()
  await expect(page.getByText('Feira da semana').first()).toBeVisible()

  await page.getByRole('button', { name: 'Fechar detalhamento' }).click()
  await expect(page.getByText('Categoria: Feira')).toBeHidden()

  await page.getByRole('button', { name: 'Atualizar dados' }).click()
  await expect(page.getByText(/R\$\s*470,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*530,00/).first()).toBeVisible()
  await expect(page.getByText('Nenhuma transação pendente no período.')).toBeVisible()

  await page.getByRole('button', { name: /Sem categoria/ }).click()
  await expect(page.getByRole('row', { name: /Pendente efetivada/ })).toHaveCount(1)
  await expect(page.getByRole('row', { name: /R\$\s*30,00/ }).first()).toBeVisible()
  await expect(page.getByText(/fluxo de caixa/i)).toHaveCount(0)
})
