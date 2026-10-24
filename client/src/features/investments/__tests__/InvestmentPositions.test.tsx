import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { renderWithProviders } from '@/test/render'

const USER = {
  id: 'user-1',
  username: 'alice.invest',
  createdAt: '2026-10-20T12:00:00Z',
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

function position(overrides: Partial<Position> = {}): Position {
  return {
    id: 'pos-1',
    broker: 'XP',
    assetName: 'Petrobras PN',
    ticker: 'PETR4',
    quantity: '10',
    referencePrice: '12.5',
    total: '125.00',
    referenceDate: '2026-10-20',
    origin: 'MANUAL',
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(initial: Position[]) {
  let positions = initial
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET'
    if (url.includes('/auth/me')) return jsonResponse(USER)
    if (url.includes('/investments/positions') && method === 'GET') {
      return jsonResponse({ items: positions, hasMore: false })
    }
    if (url.endsWith('/investments/positions') && method === 'POST') {
      const body = JSON.parse(String(init.body)) as Record<string, string>
      const created = position({
        id: 'pos-new',
        broker: body.broker as Position['broker'],
        assetName: body.assetName ?? '',
        ticker: body.ticker,
        quantity: body.quantity ?? '0',
        referencePrice: body.referencePrice ?? '33.33333333',
        total: body.totalValue ?? '100.00',
        referenceDate: body.referenceDate ?? '2026-10-22',
      })
      positions = [created, ...positions]
      return jsonResponse(created, 201)
    }
    if (url.includes('/investments/positions/') && method === 'PATCH') {
      const id = url.split('/').pop() ?? ''
      const body = JSON.parse(String(init.body)) as Record<string, string>
      const updated = position({
        id,
        broker: body.broker as Position['broker'],
        assetName: body.assetName ?? '',
        ticker: body.ticker,
        quantity: body.quantity ?? '0',
        referencePrice: body.referencePrice ?? '0',
        total: '200.00',
        referenceDate: body.referenceDate ?? '2026-10-23',
      })
      positions = positions.map((item) => (item.id === id ? updated : item))
      return jsonResponse(updated)
    }
    if (url.includes('/investments/positions/') && method === 'DELETE') {
      const id = url.split('/').pop() ?? ''
      positions = positions.filter((item) => item.id !== id)
      return new Response(null, { status: 204 })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

describe('posições manuais de investimento', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('lista posições, total calculado e data de referência como dado do usuário', async () => {
    stubApi([
      position(),
      position({
        id: 'pos-2',
        broker: 'SANTANDER',
        assetName: 'Fundo imobiliário',
        ticker: 'HGLG11',
        quantity: '3',
        referencePrice: '33.33333333',
        total: '100.00',
        referenceDate: '2026-10-21',
      }),
    ])

    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Investimentos' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Petrobras PN')).toBeInTheDocument()
    expect(screen.getByText('PETR4')).toBeInTheDocument()
    expect(screen.getByText(/10 × R\$\s*12,50/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*125,00/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*100,00/)).toBeInTheDocument()
    expect(screen.getByText(/20\/10\/2026/)).toBeInTheDocument()
    expect(screen.getAllByText(/21\/10\/2026/).length).toBeGreaterThan(0)
    expect(screen.getByText(/Total informado/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*225,00/)).toBeInTheDocument()
    expect(screen.getByText(/não é cotação de mercado/i)).toBeInTheDocument()
  })

  it('cria posição informando apenas o valor total', async () => {
    const mock = stubApi([])
    const user = userEvent.setup()

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await user.selectOptions(screen.getByLabelText('Corretora'), 'SANTANDER')
    await user.type(screen.getByLabelText('Ativo'), 'Fundo imobiliário')
    await user.type(screen.getByLabelText('Ticker'), 'HGLG11')
    await user.type(screen.getByLabelText('Quantidade'), '3')
    await user.click(screen.getByLabelText('Valor total'))
    await user.type(screen.getByLabelText('Valor total informado'), '100.00')
    fireEvent.change(screen.getByLabelText('Data de referência'), {
      target: { value: '2026-10-22' },
    })
    await user.click(screen.getByRole('button', { name: 'Adicionar posição' }))

    await waitFor(() => {
      const post = mock.mock.calls.find(
        ([url, init]) => String(url).endsWith('/investments/positions') && init?.method === 'POST',
      )
      expect(post).toBeDefined()
      expect(JSON.parse(String(post?.[1]?.body))).toEqual({
        broker: 'SANTANDER',
        assetName: 'Fundo imobiliário',
        ticker: 'HGLG11',
        quantity: '3',
        totalValue: '100.00',
        referenceDate: '2026-10-22',
      })
    })

    expect(await screen.findByText('Fundo imobiliário')).toBeInTheDocument()
    const createdRow = await screen.findByRole('listitem')
    expect(within(createdRow).getByText(/R\$\s*100,00/)).toBeInTheDocument()
  })

  it('edita e exclui uma posição com confirmação explícita', async () => {
    const mock = stubApi([position()])
    const user = userEvent.setup()

    renderPage()

    const row = await screen.findByRole('listitem')
    await user.click(within(row).getByRole('button', { name: 'Editar' }))
    const assetName = within(row).getByLabelText('Ativo')
    await user.clear(assetName)
    await user.type(assetName, 'Petrobras ON')
    await user.click(within(row).getByRole('button', { name: 'Salvar posição' }))

    await waitFor(() => {
      const patch = mock.mock.calls.find(([, init]) => init?.method === 'PATCH')
      expect(patch).toBeDefined()
      expect(JSON.parse(String(patch?.[1]?.body))).toMatchObject({
        assetName: 'Petrobras ON',
        referencePrice: '12.5',
      })
    })
    expect(await screen.findByText('Petrobras ON')).toBeInTheDocument()

    const updatedRow = screen.getByRole('listitem')
    await user.click(within(updatedRow).getByRole('button', { name: 'Excluir' }))
    await user.click(within(updatedRow).getByRole('button', { name: 'Confirmar exclusão' }))

    await waitFor(() => {
      expect(mock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
    })
    await waitFor(() => {
      expect(screen.queryByText('Petrobras ON')).not.toBeInTheDocument()
    })
  })

  it('mostra erro de validação sem enviar quantidade ou valor inválidos', async () => {
    const mock = stubApi([])
    const user = userEvent.setup()

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await user.type(screen.getByLabelText('Ativo'), 'Ativo sem quantidade')
    fireEvent.change(screen.getByLabelText('Data de referência'), {
      target: { value: '2026-10-22' },
    })
    await user.click(screen.getByRole('button', { name: 'Adicionar posição' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/Informe a quantidade/i)
    expect(mock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })
})
