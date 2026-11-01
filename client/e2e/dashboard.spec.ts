import { expect, test, type Page } from '@playwright/test'

/**
 * E2E do dashboard (T103).
 *
 * Prova a hierarquia do período, filtros na URL, séries acessíveis, drill-down
 * contextual e que uma transação pendente entra nos totais uma única vez após a
 * efetivação. O stub segue o contrato de `/dashboard/*` e `/reports/entries`.
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
  summaryUrls: string[]
}

async function installStub(page: Page): Promise<StubState> {
  const state: StubState = { summaryCalls: 0, summaryUrls: [] }

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
    if (path === '/sync/status') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          integrations: [
            {
              id: 'integration-1',
              provider: 'MEU_PLUGGY',
              status: 'SYNCED',
              lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
              connectionCount: 1,
            },
          ],
          connections: [
            {
              id: 'conn-1',
              institutionName: 'Banco Alfa',
              status: 'SYNCED',
              lastSyncAt: '2026-10-23T12:00:00Z',
              accountCount: 2,
            },
          ],
          pendingJobs: 0,
        }),
      })
    }
    if (path === '/dashboard/summary') {
      state.summaryCalls += 1
      state.summaryUrls.push(url.search)
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
          {
            label: '08/10',
            start: '2026-10-08',
            end: '2026-10-09',
            income: '0.00',
            expenses: '30.00',
            result: '-30.00',
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

test('mantém hierarquia e leitura no tema escuro em 390 px', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript((userId) => {
    localStorage.setItem('delta.prefs.active', userId)
    localStorage.setItem(`delta.prefs.${userId}`, JSON.stringify({ theme: 'dark', accent: 'blue' }))
  }, USER.id)
  await installStub(page)

  await page.goto('/dashboard')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Resultado do período' })).toBeVisible()
  await expect(page.getByRole('img', { name: /Evolução de receitas/i })).toHaveAttribute(
    'viewBox',
    '0 0 380 250',
  )
  await expect(page.getByRole('button', { name: 'Abrir navegação' })).toBeVisible()

  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth + 1)
})

test('mostra totais separados, filtra pela URL e detalha categoria sem perder contexto', async ({
  page,
}) => {
  const state = await installStub(page)

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Resultado do período' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 3, name: 'Receitas' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 3, name: 'Despesas' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 3, name: 'Saldo em contas' })).toBeVisible()
  await expect(
    page.getByRole('heading', { level: 3, name: 'Saldo devedor de cartões' }),
  ).toBeVisible()
  await expect(page.getByText(/R\$\s*500,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*1\.100,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*310,00/).first()).toBeVisible()
  await expect(page.getByText(/Dívida separada do saldo/)).toBeVisible()
  await expect(page.getByText(/Total de despesas no período/)).toBeVisible()
  await expect(page.getByRole('img', { name: /Evolução de receitas/i })).toBeVisible()
  await expect(page.getByText('Feira da semana').first()).toBeVisible()

  await page.getByRole('button', { name: /Feira/ }).first().click()
  await expect(page.getByRole('dialog', { name: 'Categoria: Feira' })).toBeVisible()
  await expect(page.getByText(/R\$\s*200,00 em 1 lançamento/)).toBeVisible()
  await expect(page.getByText('Feira da semana').last()).toBeVisible()
  await page.getByRole('button', { name: 'Fechar' }).click()
  await expect(page.getByRole('dialog', { name: 'Categoria: Feira' })).toBeHidden()

  await page.getByRole('button', { name: 'Ano' }).click()
  await expect(page).toHaveURL(/period=YEAR/)
  await expect(page.getByText(/R\$\s*470,00/).first()).toBeVisible()
  await expect(page.getByText(/R\$\s*530,00/).first()).toBeVisible()
  await expect(page.getByText('Nenhuma pendência crítica')).toBeVisible()

  await page.getByRole('button', { name: /Filtros/ }).click()
  const dialog = page.getByRole('dialog', { name: 'Filtros do dashboard' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('checkbox', { name: 'Carteira' }).check()
  await dialog.getByRole('button', { name: 'Aplicar filtros' }).click()
  await expect(page).toHaveURL(/accountId=acc-cash/)
  await expect(page.getByText('Conta: Carteira')).toBeVisible()
  expect(state.summaryUrls.some((search) => search.includes('accountId=acc-cash'))).toBe(true)

  await page.goBack()
  await expect(page).not.toHaveURL(/accountId=acc-cash/)

  await page.getByRole('button', { name: 'Atualizar dados' }).click()
  await expect(page.getByText(/R\$\s*470,00/).first()).toBeVisible()
  await expect(page.getByText(/fluxo de caixa/i)).toHaveCount(0)
})
