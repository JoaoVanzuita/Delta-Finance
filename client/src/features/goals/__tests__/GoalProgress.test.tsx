import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { GoalsPage } from '@/features/goals/pages/GoalsPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const USER = {
  id: 'user-1',
  username: 'alice.goals',
  createdAt: '2026-10-20T12:00:00Z',
}

interface GoalFixture {
  id: string
  name: string
  description?: string
  type: 'EMERGENCY_FUND' | 'TRAVEL' | 'PLANNED_PURCHASE' | 'DEBT_REDUCTION' | 'NET_WORTH'
  targetAmount: string
  targetDate?: string
  status: 'ACTIVE' | 'ACHIEVED' | 'PAUSED' | 'ARCHIVED' | 'OVERDUE' | 'CHANGED'
  progressRule: 'ALLOCATIONS' | 'NET_WORTH' | 'DEBT_REDUCTION'
  initialValue?: string
  currentValue: string
  percentage: string
  remainingAmount: string
  unallocatedAmount: string
  excessAmount: string
  allocations: Array<Record<string, unknown>>
  progressHistory: Array<Record<string, unknown>>
}

function goal(overrides: Partial<GoalFixture> = {}): GoalFixture {
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
        referenceDate: '2026-10-24',
        currentValue: '500.00',
        percentage: '50.00',
        remainingAmount: '500.00',
        ruleVersion: 'v1',
      },
    ],
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(initialGoals: GoalFixture[]) {
  let goals = initialGoals
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET'
    if (url.includes('/auth/me')) return jsonResponse(USER)
    if (url.endsWith('/goals') && method === 'GET') {
      return jsonResponse({ items: goals })
    }
    if (url.endsWith('/goals') && method === 'POST') {
      const body = JSON.parse(String(init.body)) as Record<string, unknown>
      const created = goal({
        id: 'goal-new',
        name: String(body.name),
        type: body.type as GoalFixture['type'],
        targetAmount: String(body.targetAmount),
        progressRule: body.progressRule as GoalFixture['progressRule'],
        allocations: [
          {
            id: 'alloc-new',
            sourceType: 'MANUAL',
            allocationMode: 'PERCENTAGE',
            percentage: '40',
            contributionAmount: '400.00',
          },
        ],
        currentValue: '400.00',
        percentage: '40.00',
        remainingAmount: '600.00',
        unallocatedAmount: '600.00',
      })
      goals = [created, ...goals]
      return jsonResponse(created, 201)
    }
    if (url.includes('/goals/') && url.endsWith('/status') && method === 'POST') {
      const id = url.split('/').slice(-2)[0] ?? ''
      const body = JSON.parse(String(init.body)) as { status: GoalFixture['status'] }
      const updated = goals.find((item) => item.id === id)
      if (!updated) return jsonResponse({}, 404)
      const next = { ...updated, status: body.status }
      goals = goals.map((item) => (item.id === id ? next : item))
      return jsonResponse(next)
    }
    if (url.includes('/goals/') && method === 'DELETE') {
      const id = url.split('/').pop() ?? ''
      goals = goals.filter((item) => item.id !== id)
      return new Response(null, { status: 204 })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return {
    mock,
    get goals() {
      return goals
    },
  }
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/goals" element={<GoalsPage />} />
    </Routes>,
    { route: '/goals' },
  )
}

describe('metas financeiras', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mostra progresso, contribuições, saldo não alocado e histórico', async () => {
    stubApi([goal()])
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Metas' })).toBeInTheDocument()
    const card = await screen.findByRole('article', { name: 'Reserva de emergência' })
    expect(within(card).getByText('Ativa')).toBeInTheDocument()
    expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
    expect(within(card).getByText(/Saldo não alocado: R\$\s*500,00/)).toBeInTheDocument()
    expect(within(card).getByText(/R\$\s*300,00/)).toBeInTheDocument()
    expect(within(card).getByText(/R\$\s*200,00/)).toBeInTheDocument()
    expect(within(card).getByText(/30%/)).toBeInTheDocument()
    expect(within(card).getByText(/20%/)).toBeInTheDocument()
    expect(within(card).getByText(/Regra: Alocações explícitas/)).toBeInTheDocument()
    expect(within(card).getByText(/24\/10\/2026/)).toBeInTheDocument()
  })

  it('distingue todos os estados e mostra excesso sobre a meta', async () => {
    stubApi([
      goal({ id: 'g1', name: 'Ativa', status: 'ACTIVE' }),
      goal({ id: 'g2', name: 'Concluída', status: 'ACHIEVED', percentage: '100.00' }),
      goal({ id: 'g3', name: 'Pausada', status: 'PAUSED' }),
      goal({ id: 'g4', name: 'Arquivada', status: 'ARCHIVED' }),
      goal({ id: 'g5', name: 'Atrasada', status: 'OVERDUE' }),
      goal({ id: 'g6', name: 'Alterada', status: 'CHANGED' }),
      goal({
        id: 'g7',
        name: 'Com excesso',
        status: 'ACHIEVED',
        currentValue: '1200.00',
        percentage: '100.00',
        remainingAmount: '0.00',
        unallocatedAmount: '0.00',
        excessAmount: '200.00',
      }),
    ])
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Metas' })
    await screen.findByRole('article', { name: 'Ativa' })
    const expected = ['Ativa', 'Concluída', 'Pausada', 'Arquivada', 'Atrasada', 'Alterada']
    for (const label of expected) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
    const exceeded = screen.getByRole('article', { name: 'Com excesso' })
    expect(within(exceeded).getByText(/Excesso sobre a meta: R\$\s*200,00/)).toBeInTheDocument()
    const changed = screen.getByRole('article', { name: 'Alterada' })
    expect(within(changed).getByRole('button', { name: 'Entendi a alteração' })).toBeInTheDocument()
  })

  it('cria meta com alocação percentual', async () => {
    const { mock } = stubApi([])
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Metas' })
    await user.type(screen.getByLabelText('Nome da meta'), 'Viagem')
    await user.selectOptions(screen.getByLabelText('Tipo'), 'TRAVEL')
    await user.type(screen.getByLabelText('Valor-alvo'), '1000.00')
    await user.selectOptions(screen.getByLabelText('Regra de progresso'), 'ALLOCATIONS')
    await user.click(screen.getByRole('button', { name: 'Adicionar alocação' }))
    await user.selectOptions(screen.getByLabelText('Modo da alocação'), 'PERCENTAGE')
    await user.type(screen.getByLabelText('Percentual da alocação'), '40')
    await user.click(screen.getByRole('button', { name: 'Criar meta' }))

    await waitFor(() => {
      const post = mock.mock.calls.find(
        ([url, init]) => String(url).endsWith('/goals') && init?.method === 'POST',
      )
      expect(post).toBeDefined()
      expect(JSON.parse(String(post?.[1]?.body))).toEqual({
        name: 'Viagem',
        type: 'TRAVEL',
        targetAmount: '1000.00',
        progressRule: 'ALLOCATIONS',
        allocations: [
          {
            sourceType: 'MANUAL',
            allocationMode: 'PERCENTAGE',
            percentage: '40',
          },
        ],
      })
    })
    expect(await screen.findByRole('article', { name: 'Viagem' })).toBeInTheDocument()
  })

  it('pausa e reativa a meta', async () => {
    const { mock } = stubApi([goal()])
    const user = userEvent.setup()
    renderPage()

    const card = await screen.findByRole('article', { name: 'Reserva de emergência' })
    await user.click(within(card).getByRole('button', { name: 'Pausar' }))
    expect(await screen.findByText('Pausada')).toBeInTheDocument()
    expect(
      mock.mock.calls.some(
        ([url, init]) =>
          String(url).endsWith('/goals/goal-1/status') &&
          init?.method === 'POST' &&
          JSON.parse(String(init?.body)).status === 'PAUSED',
      ),
    ).toBe(true)

    const pausedCard = screen.getByRole('article', { name: 'Reserva de emergência' })
    await user.click(within(pausedCard).getByRole('button', { name: 'Reativar' }))
    expect(await screen.findByText('Ativa')).toBeInTheDocument()
  })

  it('não oferece CTA de transferência, compra, investimento ou ordem', async () => {
    stubApi([goal()])
    renderPage()

    await screen.findByRole('article', { name: 'Reserva de emergência' })
    const forbidden = /transferir|pagar|comprar|vender|investir|aplicar|ordem/i
    expect(screen.queryByRole('button', { name: forbidden })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: forbidden })).not.toBeInTheDocument()
    expect(screen.queryByText(/recomendação de investimento/i)).not.toBeInTheDocument()
  })

  it('mantém acessibilidade WCAG 2.1 A/AA', async () => {
    stubApi([goal()])
    renderPage()

    await screen.findByRole('article', { name: 'Reserva de emergência' })
    await expectNoA11yViolations()
  })
})
