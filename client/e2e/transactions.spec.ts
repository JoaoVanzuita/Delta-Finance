import { expect, test, type Page } from '@playwright/test'

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const TRANSACTIONS = {
  items: [
    {
      id: 'tx-1',
      accountId: 'acc-1',
      categoryId: 'cat-1',
      categoryName: 'Feira',
      source: 'MANUAL',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-20',
      amount: '-45.00',
      currency: 'BRL',
      description: 'Conta de luz',
      editedFields: [],
      reconciliationState: 'NONE',
    },
    {
      id: 'tx-2',
      accountId: 'acc-1',
      categoryId: 'cat-2',
      categoryName: 'Salário',
      source: 'IMPORTED',
      type: 'CREDIT',
      status: 'POSTED',
      postedOn: '2026-10-19',
      amount: '1000.00',
      currency: 'BRL',
      description: 'Salário do mês',
      editedFields: [],
      reconciliationState: 'NONE',
    },
  ],
  hasMore: false,
}

async function installStub(page: Page) {
  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
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
        body: JSON.stringify({
          items: [
            {
              id: 'acc-1',
              type: 'BANK',
              origin: 'MANUAL',
              alias: 'Conta Casa',
              currency: 'BRL',
              currentBalance: '500.00',
              status: 'ACTIVE',
            },
          ],
        }),
      })
    }
    if (path === '/categories') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            { id: 'cat-1', name: 'Feira', kind: 'EXPENSE', predefined: false, active: true },
            { id: 'cat-2', name: 'Salário', kind: 'INCOME', predefined: true, active: true },
          ],
        }),
      })
    }
    if (path === '/transactions') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(TRANSACTIONS),
      })
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'NOT_FOUND', detail: 'Não encontrado.' }),
    })
  })
}

test('busca local, filtros na URL e detalhe em drawer', async ({ page }) => {
  await installStub(page)
  await page.goto('/transactions')

  await expect(page.getByRole('heading', { level: 1, name: 'Transações' })).toBeVisible()
  await expect(page.getByText('Conta de luz')).toBeVisible()
  await expect(page.getByText('Salário do mês')).toBeVisible()

  await page.getByLabel('Buscar nas transações carregadas').fill('luz')
  await expect(page.getByText('Conta de luz')).toBeVisible()
  await expect(page.getByText('Salário do mês')).toBeHidden()
  await expect(page.getByText(/Busca local em 2 transação/)).toBeVisible()
  await page.getByLabel('Buscar nas transações carregadas').fill('')

  await page.getByRole('button', { name: /Filtros/ }).click()
  const filters = page.getByRole('dialog', { name: 'Filtros de transações' })
  await filters.getByLabel('Estado').selectOption('PENDING')
  await filters.getByRole('button', { name: 'Aplicar filtros' }).click()
  await expect(page).toHaveURL(/status=PENDING/)
  await expect(page.getByText('Estado: Pendente')).toBeVisible()

  await page.getByRole('button', { name: 'Remover filtro Estado: Pendente' }).click()
  await expect(page).not.toHaveURL(/status=PENDING/)

  await page.getByText('Conta de luz').click()
  const drawer = page.getByRole('dialog', { name: 'Detalhes da transação' })
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('Conta Casa')).toBeVisible()
  await expect(drawer.getByText('Feira')).toBeVisible()
  await expect(drawer.getByText('Manual').first()).toBeVisible()
  await drawer.getByRole('button', { name: 'Fechar' }).first().click()
  await expect(drawer).toBeHidden()

  await page.getByRole('button', { name: /Ações de Conta de luz/ }).click()
  await page.getByRole('button', { name: 'Corrigir transação' }).click()
  await expect(page.getByRole('dialog', { name: 'Corrigir transação' })).toBeVisible()
})
