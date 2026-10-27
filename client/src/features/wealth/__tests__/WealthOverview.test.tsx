import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { WealthPage } from '@/features/wealth/pages/WealthPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const USER = {
  id: 'user-1',
  username: 'alice.wealth',
  createdAt: '2026-10-20T12:00:00Z',
}

const OVERVIEW = {
  referenceDate: '2026-10-24',
  grossAssets: '1600.00',
  liabilities: '300.00',
  netWorth: '1300.00',
  completeness: 'PARTIAL',
  hasData: true,
  items: [
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-bank',
      description: 'Conta Alfa',
      value: '1000.00',
      included: true,
      referenceDate: '2026-10-24',
      stale: false,
    },
    {
      sourceType: 'ACCOUNT',
      sourceId: 'acc-gap',
      description: 'Conta sem saldo',
      value: '0.00',
      included: false,
      exclusionReason: 'Saldo não informado.',
      stale: false,
    },
    {
      sourceType: 'CARD',
      sourceId: 'card-1',
      description: 'Cartão Alfa',
      value: '100.00',
      included: true,
      referenceDate: '2026-10-24',
      stale: false,
    },
    {
      sourceType: 'INVESTMENT',
      sourceId: 'pos-1',
      description: 'Fundo imobiliário (HGLG11)',
      value: '100.00',
      included: true,
      referenceDate: '2026-10-24',
      stale: false,
    },
    {
      sourceType: 'MANUAL_ASSET',
      sourceId: 'item-1',
      description: 'Tesouro reserva',
      value: '500.00',
      included: true,
      referenceDate: '2026-10-23',
      stale: false,
    },
    {
      sourceType: 'MANUAL_LIABILITY',
      sourceId: 'item-2',
      description: 'Empréstimo',
      value: '200.00',
      included: true,
      referenceDate: '2026-10-23',
      stale: false,
    },
  ],
}

const MANUAL_ITEMS = [
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
]

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

interface StubState {
  manualItems: Array<Record<string, unknown>>
}

function stubApi(): {
  mock: ReturnType<typeof vi.fn>
  state: StubState
} {
  const state: StubState = { manualItems: [...MANUAL_ITEMS] }
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    const method = init.method ?? 'GET'
    if (url.includes('/auth/me')) return jsonResponse(USER)
    if (url.includes('/wealth/overview')) return jsonResponse(OVERVIEW)
    if (url.endsWith('/wealth/manual-items') && method === 'GET') {
      return jsonResponse({ items: state.manualItems })
    }
    if (url.endsWith('/wealth/manual-items') && method === 'POST') {
      const body = JSON.parse(String(init.body)) as Record<string, string>
      const created = {
        id: 'item-new',
        kind: body.kind,
        type: body.type,
        description: body.description,
        referenceValue: body.referenceValue,
        referenceDate: body.referenceDate,
        notes: body.notes,
      }
      state.manualItems = [created, ...state.manualItems]
      return jsonResponse(created, 201)
    }
    if (url.includes('/wealth/manual-items/') && method === 'DELETE') {
      const id = url.split('/').pop() ?? ''
      state.manualItems = state.manualItems.filter((item) => item.id !== id)
      return new Response(null, { status: 204 })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return { mock, state }
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/wealth" element={<WealthPage />} />
    </Routes>,
    { route: '/wealth' },
  )
}

describe('visão patrimonial', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mostra patrimônio bruto, passivos, líquido, composição e lacunas', async () => {
    stubApi()
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Patrimônio' })).toBeInTheDocument()
    const summary = await screen.findByRole('region', { name: 'Resumo patrimonial' })
    expect(within(summary).getByText(/R\$\s*1\.600,00/)).toBeInTheDocument()
    expect(within(summary).getByText(/R\$\s*300,00/)).toBeInTheDocument()
    expect(within(summary).getByText(/R\$\s*1\.300,00/)).toBeInTheDocument()
    expect(within(summary).getByText('Parcial')).toBeInTheDocument()
    expect(within(summary).getByText(/não é cotação de mercado/i)).toBeInTheDocument()

    const composition = screen.getByRole('region', { name: 'Composição' })
    expect(within(composition).getByText('Conta Alfa')).toBeInTheDocument()
    expect(within(composition).getByText('Cartão Alfa')).toBeInTheDocument()
    expect(within(composition).getByText('Fundo imobiliário (HGLG11)')).toBeInTheDocument()
    expect(within(composition).getByText('Tesouro reserva')).toBeInTheDocument()
    expect(within(composition).getByText('Empréstimo')).toBeInTheDocument()
    expect(within(composition).getByText(/Saldo não informado/)).toBeInTheDocument()
    expect(within(composition).getByText('Conta sem saldo')).toBeInTheDocument()
  })

  it('cria item manual como ativo com valor e data de referência', async () => {
    const { mock } = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Patrimônio' })
    await user.selectOptions(screen.getByLabelText('Natureza'), 'ASSET')
    await user.type(screen.getByLabelText('Tipo do item'), 'IMOVEL')
    await user.type(screen.getByLabelText('Descrição'), 'Apartamento')
    await user.type(screen.getByLabelText('Valor de referência'), '250000.00')
    fireEvent.change(screen.getByLabelText('Data de referência'), {
      target: { value: '2026-10-22' },
    })
    await user.click(screen.getByRole('button', { name: 'Adicionar item manual' }))

    await waitFor(() => {
      const post = mock.mock.calls.find(
        ([url, init]) => String(url).endsWith('/wealth/manual-items') && init?.method === 'POST',
      )
      expect(post).toBeDefined()
      expect(JSON.parse(String(post?.[1]?.body))).toEqual({
        kind: 'ASSET',
        type: 'IMOVEL',
        description: 'Apartamento',
        referenceValue: '250000.00',
        referenceDate: '2026-10-22',
      })
    })
    expect(await screen.findByText('Apartamento')).toBeInTheDocument()
  })

  it('mantém acessibilidade WCAG 2.1 A/AA', async () => {
    stubApi()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Patrimônio' })
    await expectNoA11yViolations()
  })
})
