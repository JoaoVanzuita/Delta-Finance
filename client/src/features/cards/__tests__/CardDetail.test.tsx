import { screen, within } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CardDetailPage } from '@/features/cards/pages/CardDetailPage'
import { renderWithProviders } from '@/test/render'

const ACCOUNT = {
  id: 'card-1',
  type: 'CREDIT',
  origin: 'IMPORTED',
  alias: 'Cartão Alfa',
  institutionName: 'Banco Alfa',
  currency: 'BRL',
  currentBalance: '180.00',
  status: 'ACTIVE',
  cardProfile: {
    accountId: 'card-1',
    closingDay: 5,
    dueDay: 15,
    creditLimit: '5000.00',
    creditBalance: '0.00',
    brand: 'VISA',
    level: 'PLATINUM',
    last4: '1234',
    availableCreditLimit: '4820.00',
  },
}

const OPEN_INVOICE = {
  id: 'invoice-open',
  cardAccountId: 'card-1',
  cycleStart: '2026-10-06',
  cycleEnd: '2026-11-05',
  closingDate: '2026-11-05',
  dueDate: '2026-11-15',
  status: 'OPEN',
  total: '50.00',
  paymentsTotal: '0.00',
  outstanding: '50.00',
  appliedCredit: '0.00',
  minimumPayment: '5.00',
  source: 'LOCAL',
  hasAdjustments: false,
  adjustmentCount: 0,
  providerBill: null,
  missingPurchases: false,
}

const CLOSED_INVOICE = {
  id: 'invoice-closed',
  cardAccountId: 'card-1',
  cycleStart: '2026-09-06',
  cycleEnd: '2026-10-05',
  closingDate: '2026-10-05',
  dueDate: '2026-10-15',
  status: 'PAID',
  total: '180.00',
  paymentsTotal: '180.00',
  outstanding: '0.00',
  appliedCredit: '10.00',
  minimumPayment: null,
  source: 'LOCAL',
  hasAdjustments: false,
  adjustmentCount: 0,
  providerBill: null,
  missingPurchases: true,
}

const OVERVIEW = {
  totalOutstanding: '50.00',
  totalLimit: '5000.00',
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
      outstanding: '50.00',
      utilization: '3.60',
      nextDueDate: '2026-11-15',
      currentInvoice: OPEN_INVOICE,
      nextDueInvoice: OPEN_INVOICE,
      unallocatedCount: 0,
    },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/cards/:accountId" element={<CardDetailPage />} />
    </Routes>,
    { route: '/cards/card-1' },
  )
}

describe('detalhe do cartão', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url.includes('/accounts/card-1')) return jsonResponse(ACCOUNT)
        if (url.includes('/cards/overview')) return jsonResponse(OVERVIEW)
        if (url.includes('/cards/card-1/invoices')) {
          return jsonResponse({ items: [OPEN_INVOICE, CLOSED_INVOICE], hasMore: false })
        }
        if (url.includes('/transactions')) {
          return jsonResponse({
            items: [
              {
                id: 'tx-1',
                accountId: 'card-1',
                description: 'Compra recente',
                amount: '-50.00',
                currency: 'BRL',
                postedOn: '2026-10-20',
                status: 'POSTED',
                type: 'DEBIT',
                invoiceId: 'invoice-open',
              },
            ],
            hasMore: false,
          })
        }
        return jsonResponse({}, 404)
      }),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('consolida metadados, resumo, fatura atual, histórico e transações', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cartão Alfa' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Banco Alfa · VISA · PLATINUM · •••• 1234')).toBeInTheDocument()
    expect(screen.getByText('R$ 4.820,00')).toBeInTheDocument()
    expect(screen.getAllByText('R$ 50,00').length).toBeGreaterThan(0)
    expect(screen.getByText('Compra recente')).toBeInTheDocument()

    const current = screen.getByRole('region', { name: 'Fatura atual' })
    expect(within(current).getByRole('link', { name: 'Abrir fatura atual' })).toHaveAttribute(
      'href',
      '/invoices/invoice-open',
    )

    const history = screen.getByRole('region', { name: 'Histórico de faturas' })
    expect(within(history).getByText('Histórico incompleto')).toBeInTheDocument()
    expect(within(history).getByRole('link', { name: /06\/09\/2026/ })).toHaveAttribute(
      'href',
      '/invoices/invoice-closed',
    )
  })
})
