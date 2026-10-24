import { expect, test, type Page } from '@playwright/test'

/**
 * E2E de investimentos manuais (stub de API).
 *
 * Prova CRUD de posições, total quantidade × preço, data de referência e
 * ausência de importação automática ou recomendação.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
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

async function installStub(page: Page): Promise<State> {
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

test('registra, edita e exclui posição manual sem anunciar importação automática', async ({
  page,
}) => {
  const state = await installStub(page)
  await page.goto('/investments')

  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  await expect(
    page.getByText(/não há importação automática de investimentos validada/i),
  ).toBeVisible()
  await expect(
    page.getByText(/não oferece recomendação de compra, venda ou alocação/i),
  ).toBeVisible()
  const initialRow = page.getByRole('listitem').filter({ hasText: 'Petrobras PN' })
  await expect(initialRow).toBeVisible()
  await expect(initialRow.getByText(/R\$\s*125,00/)).toBeVisible()

  await page.getByLabel('Corretora').selectOption('SANTANDER')
  await page.getByLabel('Ativo').fill('Fundo imobiliário')
  await page.getByLabel('Ticker').fill('HGLG11')
  await page.getByLabel('Quantidade').fill('3')
  await page.getByLabel('Valor total').check()
  await page.getByLabel('Valor total informado').fill('100.00')
  await page.getByLabel('Data de referência').fill('2026-10-22')
  await page.getByRole('button', { name: 'Adicionar posição' }).click()

  await expect(page.getByText('Fundo imobiliário')).toBeVisible()
  await expect(page.getByText(/33,33333333/)).toBeVisible()
  await expect(page.getByText('Posição adicionada.')).toBeVisible()
  expect(state.positions).toHaveLength(2)

  const newRow = page.getByRole('listitem').filter({ hasText: 'Fundo imobiliário' })
  await expect(newRow.getByText(/R\$\s*100,00/)).toBeVisible()
  await newRow.getByRole('button', { name: 'Editar' }).click()
  await newRow.getByLabel('Ativo').fill('Fundo imobiliário atualizado')
  await newRow.getByRole('button', { name: 'Salvar posição' }).click()

  await expect(page.getByText('Fundo imobiliário atualizado')).toBeVisible()

  const editedRow = page.getByRole('listitem').filter({ hasText: 'Fundo imobiliário atualizado' })
  await editedRow.getByRole('button', { name: 'Excluir' }).click()
  await editedRow.getByRole('button', { name: 'Confirmar exclusão' }).click()

  await expect(page.getByText('Fundo imobiliário atualizado')).toBeHidden()
  await expect(page.getByText(/não é cotação de mercado/i)).toBeVisible()
})

test('mostra estado vazio sem sugerir importação automática', async ({ page }) => {
  await installStub(page)
  await page.route('**/api/v1/investments/positions?**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [], hasMore: false }),
    })
  })

  await page.goto('/investments')

  await expect(page.getByText('Nenhuma posição registrada')).toBeVisible()
  await expect(
    page.getByText(/não há importação automática de investimentos validada/i),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: /importar/i })).toHaveCount(0)
})
