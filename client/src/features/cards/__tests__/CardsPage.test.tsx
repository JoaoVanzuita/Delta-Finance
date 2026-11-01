import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CardsPage } from '@/features/cards/pages/CardsPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const OVERVIEW = {
  totalOutstanding: '180.00',
  totalLimit: '8000.00',
  totalUsed: '180.00',
  nextDueDate: '2026-11-15',
  items: [
    {
      accountId: 'card-1',
      alias: 'Cartão Alfa',
      institutionName: 'Banco Alfa',
      status: 'ACTIVE',
      currency: 'BRL',
      hasCycle: true,
      closingDay: 5,
      dueDay: 15,
      creditLimit: '5000.00',
      creditBalance: '0.00',
      availableCreditLimit: '4820.00',
      brand: 'VISA',
      level: 'PLATINUM',
      last4: '1234',
      usedAmount: '180.00',
      outstanding: '180.00',
      utilization: '3.60',
      nextDueDate: '2026-11-15',
      currentInvoice: {
        id: 'invoice-1',
        cardAccountId: 'card-1',
        cycleStart: '2026-09-06',
        cycleEnd: '2026-10-05',
        closingDate: '2026-10-05',
        dueDate: '2026-11-15',
        status: 'CLOSED',
        total: '180.00',
        paymentsTotal: '0.00',
        outstanding: '180.00',
        appliedCredit: '0.00',
        minimumPayment: null,
        source: 'LOCAL',
        hasAdjustments: false,
        adjustmentCount: 0,
      },
      nextDueInvoice: {
        id: 'invoice-1',
        cardAccountId: 'card-1',
        cycleStart: '2026-09-06',
        cycleEnd: '2026-10-05',
        closingDate: '2026-10-05',
        dueDate: '2026-11-15',
        status: 'CLOSED',
        total: '180.00',
        paymentsTotal: '0.00',
        outstanding: '180.00',
        appliedCredit: '0.00',
        minimumPayment: null,
        source: 'LOCAL',
        hasAdjustments: false,
        adjustmentCount: 0,
      },
      unallocatedCount: 0,
    },
    {
      accountId: 'card-2',
      alias: 'Cartão sem ciclo',
      institutionName: 'Banco Beta',
      status: 'ACTIVE',
      currency: 'BRL',
      hasCycle: false,
      closingDay: null,
      dueDay: null,
      creditLimit: null,
      creditBalance: null,
      usedAmount: '30.00',
      outstanding: '0.00',
      utilization: null,
      nextDueDate: null,
      currentInvoice: null,
      nextDueInvoice: null,
      unallocatedCount: 2,
    },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(overview = OVERVIEW) {
  const mock = vi.fn((url: string) => {
    if (url.includes('/cards/overview')) return jsonResponse(overview)
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('painel de cartões', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mostra totais, fatura, utilização e pendência de ciclo sem misturar contas', async () => {
    renderWithProviders(<CardsPage />, { route: '/cards' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cartões de crédito' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('R$ 8.000,00')).toBeInTheDocument()

    expect(screen.getAllByText('R$ 180,00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('3,6%').length).toBeGreaterThan(0)

    const cardHeading = screen.getByRole('heading', { level: 3, name: 'Cartão Alfa' })
    const card = cardHeading.closest('article')
    expect(card).not.toBeNull()
    expect(within(card as HTMLElement).getAllByText('R$ 180,00').length).toBeGreaterThan(0)
    expect(within(card as HTMLElement).getByText('Fechada')).toBeInTheDocument()
    expect(within(card as HTMLElement).getByText('Última fatura')).toBeInTheDocument()
    expect(within(card as HTMLElement).queryByText('Fatura atual')).not.toBeInTheDocument()
    expect(within(card as HTMLElement).getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '3.6',
    )
    expect(within(card as HTMLElement).getByText('VISA · PLATINUM · •••• 1234')).toBeInTheDocument()
    expect(within(card as HTMLElement).getByText('R$ 4.820,00')).toBeInTheDocument()
    expect(within(card as HTMLElement).getByRole('link', { name: 'Abrir cartão' })).toHaveAttribute(
      'href',
      '/cards/card-1',
    )
    expect(within(card as HTMLElement).getByRole('link', { name: 'Faturas' })).toHaveAttribute(
      'href',
      '/cards/card-1/invoices',
    )

    expect(screen.getByText('Ciclo não configurado')).toBeInTheDocument()
    expect(screen.getByText(/2 transação\(ões\) sem fatura/)).toBeInTheDocument()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(<CardsPage />, { route: '/cards' })
    await screen.findByText('R$ 8.000,00')
    await expectNoA11yViolations()
  })
})
