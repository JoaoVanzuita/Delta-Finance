import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CardDetailPage } from '@/features/cards/pages/CardDetailPage'
import { renderWithProviders } from '@/test/render'

const ACCOUNT = {
  id: 'card-1',
  type: 'CREDIT',
  origin: 'IMPORTED',
  alias: 'Cartão Alfa',
  providerName: 'Cartão Original',
  nameCustomized: true,
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

let currentAccount = { ...ACCOUNT }

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
    currentAccount = { ...ACCOUNT }
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init: RequestInit = {}) => {
        if (url.includes('/accounts/card-1/display-name') && init.method === 'PUT') {
          const body = JSON.parse(String(init.body)) as { displayName: string }
          currentAccount = { ...currentAccount, alias: body.displayName }
          return jsonResponse(currentAccount)
        }
        if (url.includes('/accounts/card-1')) return jsonResponse(currentAccount)
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
    expect(screen.getAllByText(/Nome original: Cartão Original/).length).toBeGreaterThan(0)
    expect(screen.getByText('Banco Alfa · VISA · PLATINUM · •••• 1234')).toBeInTheDocument()
    expect(screen.getByText('R$ 4.820,00')).toBeInTheDocument()
    expect(screen.getAllByText('R$ 50,00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Fatura atual').length).toBeGreaterThan(0)

    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: 'Transações' }))
    expect(await screen.findByText('Compra recente')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Faturas' }))
    expect(screen.getByText('Histórico')).toBeInTheDocument()
    expect(screen.getByText('Histórico incompleto')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /06\/09\/2026/ })).toHaveAttribute(
      'href',
      '/invoices/invoice-closed',
    )
  })

  it('não chama de fatura atual uma fatura fechada ou paga', async () => {
    const user = userEvent.setup()
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url.includes('/accounts/card-1')) return jsonResponse(currentAccount)
        if (url.includes('/cards/overview')) {
          return jsonResponse({
            ...OVERVIEW,
            items: [{ ...OVERVIEW.items[0], currentInvoice: CLOSED_INVOICE }],
          })
        }
        if (url.includes('/cards/card-1/invoices')) {
          return jsonResponse({ items: [CLOSED_INVOICE], hasMore: false })
        }
        if (url.includes('/transactions')) return jsonResponse({ items: [], hasMore: false })
        return jsonResponse({}, 404)
      }),
    )
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Cartão Alfa' })
    expect(screen.queryByText('Fatura atual')).not.toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Faturas' }))
    expect(await screen.findByText(/Nenhuma fatura aberta no ciclo atual/)).toBeInTheDocument()
    expect(screen.getByText('Histórico')).toBeInTheDocument()
  })

  it('permite renomear o cartão sem alterar o nome original', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cartão Alfa' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Renomear' }))
    const input = screen.getByLabelText('Nome de exibição')
    await user.clear(input)
    await user.type(input, 'Cartão Novo')
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cartão Novo' }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/Nome original: Cartão Original/).length).toBeGreaterThan(0)
  })
})
