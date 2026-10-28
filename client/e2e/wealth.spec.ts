import { expect, test, type Page } from '@playwright/test'

/**
 * E2E de patrimônio consolidado (stub de API).
 *
 * Prova consolidação sem dupla contagem, composição com lacunas e CRUD de itens
 * manuais.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const TODAY = '2026-10-24'

interface ManualItem {
  id: string
  kind: 'ASSET' | 'LIABILITY'
  type: string
  description: string
  referenceValue: string
  referenceDate: string
  notes?: string
}

interface State {
  manualItems: ManualItem[]
}

function overview(state: State) {
  const baseItems = [
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-bank',
      description: 'Conta Alfa',
      origin: 'IMPORTED',
      duplicate: false,
      value: '1000.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-gap',
      description: 'Conta sem saldo',
      value: '0.00',
      included: false,
      exclusionReason: 'Saldo não informado.',
      origin: 'IMPORTED',
      duplicate: false,
      stale: false,
    },
    {
      sourceType: 'CARD',
      sourceId: 'card-1',
      description: 'Cartão Alfa',
      origin: 'IMPORTED',
      duplicate: false,
      value: '100.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
    {
      sourceType: 'INVESTMENT',
      sourceId: 'pos-1',
      description: 'Fundo imobiliário (HGLG11)',
      origin: 'MANUAL',
      duplicate: false,
      value: '100.00',
      included: true,
      referenceDate: TODAY,
      stale: false,
    },
  ]
  const manual = state.manualItems.map((item) => ({
    sourceType: item.kind === 'ASSET' ? 'MANUAL_ASSET' : 'MANUAL_LIABILITY',
    sourceId: item.id,
    description: item.description,
    value: item.referenceValue,
    included: true,
    origin: 'MANUAL',
    duplicate: false,
    referenceDate: item.referenceDate,
    stale: false,
  }))
  const items = [...baseItems, ...manual]
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
    referenceDate: TODAY,
    grossAssets: gross.toFixed(2),
    liabilities: liabilities.toFixed(2),
    netWorth: (gross - liabilities).toFixed(2),
    completeness: 'PARTIAL',
    hasData: true,
    items,
  }
}

async function installStub(page: Page): Promise<State> {
  const state: State = {
    manualItems: [
      {
        id: 'item-1',
        kind: 'ASSET',
        type: 'INVESTMENT',
        description: 'Tesouro reserva',
        referenceValue: '500.00',
        referenceDate: '2026-10-23',
      },
      {
        id: 'item-2',
        kind: 'LIABILITY',
        type: 'LOAN',
        description: 'Empréstimo',
        referenceValue: '200.00',
        referenceDate: '2026-10-23',
      },
    ],
  }

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/wealth/overview') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(overview(state)),
      })
    }
    if (path === '/wealth/manual-items' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.manualItems }),
      })
    }
    if (path === '/wealth/manual-items' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, string>
      const created: ManualItem = {
        id: `item-${state.manualItems.length + 1}`,
        kind: body.kind as ManualItem['kind'],
        type: body.type ?? '',
        description: body.description ?? '',
        referenceValue: body.referenceValue ?? '0.00',
        referenceDate: body.referenceDate ?? TODAY,
        notes: body.notes,
      }
      state.manualItems = [created, ...state.manualItems]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path.startsWith('/wealth/manual-items/') && method === 'DELETE') {
      const id = path.split('/').pop()
      state.manualItems = state.manualItems.filter((item) => item.id !== id)
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })

  return state
}

test('consolida patrimônio e cria item manual sem inferir passado', async ({ page }) => {
  await installStub(page)
  await page.goto('/wealth')

  await expect(page.getByRole('heading', { level: 1, name: 'Patrimônio' })).toBeVisible()
  const summary = page.getByRole('region', { name: 'Resumo patrimonial' })
  await expect(summary.getByText(/R\$\s*1\.600,00/)).toBeVisible()
  await expect(summary.getByText(/R\$\s*300,00/)).toBeVisible()
  await expect(summary.getByText(/R\$\s*1\.300,00/)).toBeVisible()
  await expect(summary.getByText('Parcial')).toBeVisible()

  const composition = page.getByRole('region', { name: 'Composição' })
  await expect(composition.getByText('Cartão Alfa')).toBeVisible()
  await expect(composition.getByText('Conta sem saldo')).toBeVisible()
  await expect(composition.getByText(/Saldo não informado/)).toBeVisible()
  await expect(composition.getByText(/Conta Alfa/)).toBeVisible()

  const manual = page.getByRole('region', { name: 'Ativos e passivos manuais' })
  await manual.getByLabel('Natureza').selectOption('ASSET')
  await manual.getByLabel('Tipo do item').fill('IMOVEL')
  await manual.getByLabel('Descrição').fill('Apartamento')
  await manual.getByLabel('Valor de referência').fill('250000.00')
  await manual.getByLabel('Data de referência').fill(TODAY)
  await manual.getByRole('button', { name: 'Adicionar item manual' }).click()

  await expect(manual.getByText('Apartamento')).toBeVisible()
  await expect(summary.getByText(/R\$\s*251\.600,00/)).toBeVisible()
  await expect(summary.getByText(/R\$\s*251\.300,00/)).toBeVisible()

  const addedRow = manual.getByRole('listitem').filter({ hasText: 'Apartamento' })
  await addedRow.getByRole('button', { name: 'Excluir' }).click()
  await addedRow.getByRole('button', { name: 'Confirmar exclusão' }).click()
  await expect(manual.getByText('Apartamento')).toBeHidden()
  await expect(summary.getByText(/R\$\s*1\.300,00/)).toBeVisible()
})

test('não apresenta valor manual como cotação de mercado', async ({ page }) => {
  await installStub(page)
  await page.goto('/wealth')

  await expect(page.getByText(/não é cotação de mercado/i)).toBeVisible()
  await expect(page.getByText(/referência informada por você/i)).toBeVisible()
})
