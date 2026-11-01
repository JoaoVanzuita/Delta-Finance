import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvoicesPage } from '@/features/invoices/pages/InvoicesPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const ACCOUNT = {
  id: 'card-1',
  type: 'CREDIT',
  origin: 'IMPORTED',
  alias: 'Cartão Alfa',
  institutionName: 'Banco Alfa',
  currency: 'BRL',
  currentBalance: '0.00',
  status: 'ACTIVE',
  cardProfile: { accountId: 'card-1', closingDay: 5, dueDay: 15, creditLimit: '5000.00' },
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
  source: 'LOCAL',
  hasAdjustments: false,
  adjustmentCount: 0,
}

const CLOSED_INVOICE = {
  ...OPEN_INVOICE,
  id: 'invoice-closed',
  cycleStart: '2026-09-06',
  cycleEnd: '2026-10-05',
  status: 'PAID',
  total: '180.00',
  paymentsTotal: '180.00',
  outstanding: '0.00',
}

const TRANSACTIONS = {
  items: [
    {
      id: 'tx-pending',
      accountId: 'card-1',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'PENDING',
      postedOn: '2026-10-20',
      amount: '-30.00',
      currency: 'BRL',
      description: 'Compra pendente',
    },
    {
      id: 'tx-unallocated-purchase',
      accountId: 'card-1',
      source: 'IMPORTED',
      type: 'DEBIT',
      status: 'POSTED',
      postedOn: '2026-10-18',
      amount: '-40.00',
      currency: 'BRL',
      description: 'Compra sem fatura',
    },
    {
      id: 'tx-unallocated-credit',
      accountId: 'card-1',
      source: 'IMPORTED',
      type: 'CREDIT',
      status: 'POSTED',
      postedOn: '2026-10-19',
      amount: '15.00',
      currency: 'BRL',
      description: 'Estorno sem fatura',
    },
  ],
  hasMore: false,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (url.includes('/accounts/card-1')) return jsonResponse(ACCOUNT)
      if (url.includes('/cards/card-1/invoices')) {
        return jsonResponse({ items: [OPEN_INVOICE, CLOSED_INVOICE], hasMore: false })
      }
      if (url.includes('/transactions')) return jsonResponse(TRANSACTIONS)
      return jsonResponse({}, 404)
    }),
  )
}

function renderPage(route = '/cards/card-1/invoices') {
  return renderWithProviders(
    <Routes>
      <Route path="/cards/:accountId/invoices" element={<InvoicesPage />} />
    </Routes>,
    { route },
  )
}

describe('faturas do cartão', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('separa compras pendentes, compras não alocadas e créditos não alocados', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: /Faturas/ })
    await user.click(screen.getByRole('tab', { name: 'Compras pendentes' }))

    expect(await screen.findByText(/Compra pendente/)).toBeInTheDocument()
    const purchaseGroup = screen
      .getByRole('heading', { name: /Compras não alocadas/ })
      .closest('div')
    expect(purchaseGroup).not.toBeNull()
    expect(screen.getByText(/Compra sem fatura/)).toBeInTheDocument()

    const creditHeading = screen.getByRole('heading', { name: /Créditos não alocados/ })
    expect(creditHeading).toBeInTheDocument()
    expect(screen.getByText(/Estorno sem fatura/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*15,00/)).toBeInTheDocument()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderPage('/cards/card-1/invoices?tab=pending')
    await screen.findByText(/Compra pendente/)
    await expectNoA11yViolations()
  })
})
