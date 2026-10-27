import { expect, test, type Page } from '@playwright/test'

/**
 * E2E de metas financeiras (stub de API).
 *
 * Prova alocações percentuais com resíduo/saldo não alocado, estados, histórico e
 * ausência de qualquer CTA que mova dinheiro ou crie ordens.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const TODAY = '2026-10-24'

interface Allocation {
  id: string
  sourceType: string
  allocationMode: 'FIXED_AMOUNT' | 'PERCENTAGE'
  allocatedAmount?: string
  percentage?: string
  contributionAmount: string
}

interface Goal {
  id: string
  name: string
  type: string
  targetAmount: string
  targetDate?: string
  status: string
  progressRule: string
  currentValue: string
  percentage: string
  remainingAmount: string
  unallocatedAmount: string
  excessAmount: string
  allocations: Allocation[]
  progressHistory: Array<Record<string, unknown>>
}

interface State {
  goals: Goal[]
}

function goal(overrides: Partial<Goal> = {}): Goal {
  return {
    id: 'goal-1',
    name: 'Reserva de emergência',
    type: 'EMERGENCY_FUND',
    targetAmount: '1000.00',
    status: 'ACTIVE',
    progressRule: 'ALLOCATIONS',
    currentValue: '500.00',
    percentage: '50.00',
    remainingAmount: '500.00',
    unallocatedAmount: '500.00',
    excessAmount: '0.00',
    allocations: [
      {
        id: 'alloc-1',
        sourceType: 'MANUAL',
        allocationMode: 'PERCENTAGE',
        percentage: '30',
        contributionAmount: '300.00',
      },
      {
        id: 'alloc-2',
        sourceType: 'MANUAL',
        allocationMode: 'PERCENTAGE',
        percentage: '20',
        contributionAmount: '200.00',
      },
    ],
    progressHistory: [
      {
        referenceDate: TODAY,
        currentValue: '500.00',
        percentage: '50.00',
        remainingAmount: '500.00',
        ruleVersion: 'v1',
      },
    ],
    ...overrides,
  }
}

async function installStub(page: Page): Promise<State> {
  const state: State = {
    goals: [goal(), goal({ id: 'goal-changed', name: 'Meta alterada', status: 'CHANGED' })],
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
    if (path === '/goals' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.goals }),
      })
    }
    if (path === '/goals' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      const target = Number(body.targetAmount ?? 0)
      const inputs = (body.allocations as Array<Record<string, string>> | undefined) ?? []
      const allocations: Allocation[] = inputs.map((input, index) => {
        const contribution =
          input.allocationMode === 'PERCENTAGE'
            ? Math.floor(target * (Number(input.percentage) / 100) * 100) / 100
            : Number(input.allocatedAmount)
        return {
          id: `alloc-new-${index}`,
          sourceType: input.sourceType ?? 'MANUAL',
          allocationMode: input.allocationMode as Allocation['allocationMode'],
          ...(input.allocationMode === 'PERCENTAGE'
            ? { percentage: input.percentage }
            : { allocatedAmount: input.allocatedAmount }),
          contributionAmount: contribution.toFixed(2),
        }
      })
      const current = allocations.reduce(
        (sum, allocation) => sum + Number(allocation.contributionAmount),
        0,
      )
      const created = goal({
        id: `goal-${state.goals.length + 1}`,
        name: String(body.name),
        type: String(body.type),
        targetAmount: target.toFixed(2),
        progressRule: String(body.progressRule),
        currentValue: current.toFixed(2),
        percentage: Math.min(100, (current / target) * 100).toFixed(2),
        remainingAmount: Math.max(0, target - current).toFixed(2),
        unallocatedAmount: Math.max(0, target - current).toFixed(2),
        allocations,
        progressHistory: [
          {
            referenceDate: TODAY,
            currentValue: current.toFixed(2),
            percentage: Math.min(100, (current / target) * 100).toFixed(2),
            remainingAmount: Math.max(0, target - current).toFixed(2),
            ruleVersion: 'v1',
          },
        ],
      })
      state.goals = [created, ...state.goals]
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(created),
      })
    }
    if (path.endsWith('/status') && method === 'POST') {
      const id = path.split('/').slice(-2)[0]
      const body = request.postDataJSON() as { status: string }
      const updated = state.goals.find((item) => item.id === id)
      if (!updated) return route.fulfill({ status: 404 })
      const next = { ...updated, status: body.status }
      state.goals = state.goals.map((item) => (item.id === id ? next : item))
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(next),
      })
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })

  return state
}

test('cria meta, mostra progresso e estados sem mover dinheiro', async ({ page }) => {
  const state = await installStub(page)
  await page.goto('/goals')

  await expect(page.getByRole('heading', { level: 1, name: 'Metas' })).toBeVisible()
  const card = page.getByRole('article', { name: 'Reserva de emergência' })
  await expect(card.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
  await expect(card.getByText(/Saldo não alocado: R\$\s*500,00/)).toBeVisible()
  await expect(card.getByText(/R\$\s*300,00/)).toBeVisible()
  await expect(card.getByText(/R\$\s*200,00/)).toBeVisible()
  await card.getByText('Histórico de progresso').click()
  await expect(card.getByText(/24\/10\/2026/)).toBeVisible()

  await page.getByLabel('Nome da meta').fill('Viagem')
  await page.getByLabel('Tipo').selectOption('TRAVEL')
  await page.getByLabel('Valor-alvo').fill('1000.00')
  await page.getByLabel('Regra de progresso').selectOption('ALLOCATIONS')
  await page.getByRole('button', { name: 'Adicionar alocação' }).click()
  await page.getByLabel('Modo da alocação').selectOption('PERCENTAGE')
  await page.getByLabel('Percentual da alocação').fill('40')
  await page.getByRole('button', { name: 'Criar meta' }).click()

  const created = page.getByRole('article', { name: 'Viagem' })
  await expect(created).toBeVisible()
  await expect(created.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40')
  await expect(created.getByText(/Saldo não alocado: R\$\s*600,00/)).toBeVisible()

  const changed = page.getByRole('article', { name: 'Meta alterada' })
  await expect(changed.getByText('Alterada', { exact: true })).toBeVisible()
  await changed.getByRole('button', { name: 'Entendi a alteração' }).click()
  await expect(
    page.getByRole('article', { name: 'Meta alterada' }).getByText('Ativa'),
  ).toBeVisible()

  await card.getByRole('button', { name: 'Pausar' }).click()
  await expect(page.getByText('Pausada', { exact: true })).toBeVisible()
  const paused = page.getByRole('article', { name: 'Reserva de emergência' })
  await paused.getByRole('button', { name: 'Reativar' }).click()
  await expect(
    page
      .getByRole('article', { name: 'Reserva de emergência' })
      .getByText('Ativa', { exact: true }),
  ).toBeVisible()

  expect(state.goals.length).toBeGreaterThanOrEqual(2)
  await expect(
    page.getByRole('button', { name: /transferir|pagar|comprar|vender|investir|ordem/i }),
  ).toHaveCount(0)
})
