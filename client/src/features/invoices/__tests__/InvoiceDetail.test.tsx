import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvoiceDetailPage } from '@/features/invoices/pages/InvoiceDetailPage'
import { renderWithProviders } from '@/test/render'

const INVOICE = {
  id: 'invoice-1',
  cardAccountId: 'card-1',
  cycleStart: '2026-09-06',
  cycleEnd: '2026-10-05',
  closingDate: '2026-10-05',
  dueDate: '2026-11-15',
  status: 'CLOSED',
  total: '120.00',
  paymentsTotal: '20.00',
  outstanding: '100.00',
  appliedCredit: '0.00',
  minimumPayment: undefined,
  source: 'LOCAL',
  hasAdjustments: true,
  adjustmentCount: 1,
  items: [
    {
      id: 'item-1',
      invoiceId: 'invoice-1',
      transactionId: 'tx-1',
      kind: 'PURCHASE',
      reportingEffect: 'NONE',
      source: 'IMPORTED',
      description: 'Compra importada',
      amount: '100.00',
      occurredOn: '2026-10-01',
      correctionCount: 0,
    },
    {
      id: 'item-2',
      invoiceId: 'invoice-1',
      kind: 'INTEREST',
      reportingEffect: 'EXPENSE_INCREASE',
      source: 'MANUAL',
      description: 'Juros',
      amount: '20.00',
      occurredOn: '2026-10-02',
      correctionCount: 2,
    },
  ],
  payments: [
    {
      id: 'payment-1',
      invoiceId: 'invoice-1',
      paymentAccountId: 'acc-bank',
      amount: '20.00',
      paidOn: '2026-10-20',
      status: 'CONFIRMED',
      source: 'MANUAL',
    },
  ],
}

const ACCOUNTS = {
  items: [
    {
      id: 'acc-bank',
      type: 'BANK',
      origin: 'MANUAL',
      alias: 'Conta Casa',
      currency: 'BRL',
      currentBalance: '500.00',
      status: 'ACTIVE',
    },
    {
      id: 'card-1',
      type: 'CREDIT',
      origin: 'IMPORTED',
      alias: 'Cartão',
      currency: 'BRL',
      currentBalance: '0.00',
      status: 'ACTIVE',
      cardProfile: {
        accountId: 'card-1',
        closingDay: 5,
        dueDay: 15,
        creditLimit: '5000.00',
        creditBalance: '35.00',
      },
    },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(invoice: typeof INVOICE = INVOICE) {
  const mock = vi.fn((url: string, init: RequestInit) => {
    if (url.includes('/invoices/invoice-1/items') && init.method === 'POST') {
      return jsonResponse({ ...INVOICE.items[1], id: 'item-3' }, 201)
    }
    if (url.includes('/invoices/invoice-1/payments') && init.method === 'POST') {
      return jsonResponse({ ...INVOICE.payments[0], id: 'payment-2' }, 201)
    }
    if (url.includes('/invoice-items/item-2/corrections')) {
      return jsonResponse([
        {
          id: 'corr-1',
          invoiceItemId: 'item-2',
          fieldName: 'amount',
          oldValue: '20.00',
          newValue: '25.00',
          changedAt: '2026-10-22T12:00:00Z',
        },
      ])
    }
    if (url.includes('/invoices/invoice-1')) return jsonResponse(invoice)
    if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
    if (url.includes('/transactions')) return jsonResponse({ items: [], hasMore: false })
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
    </Routes>,
    { route: '/invoices/invoice-1' },
  )
}

describe('detalhe da fatura', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('exibe totais, status, ajustes, itens, pagamentos e crédito separado', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: /Fatura/ })).toBeInTheDocument()
    expect(screen.getByText('Fechada')).toBeInTheDocument()
    expect(screen.getByText('1 ajuste(s)')).toBeInTheDocument()
    expect(screen.getByText('Compra importada')).toBeInTheDocument()
    expect(screen.getByText('Juros')).toBeInTheDocument()
    expect(screen.getByText(/Crédito acumulado do cartão/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*35,00/)).toBeInTheDocument()
    expect(screen.getByText('2026-10-20 · CONFIRMED')).toBeInTheDocument()
  })

  it('adiciona item manual e abre histórico de correções', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Juros')
    await user.click(screen.getByRole('button', { name: 'Adicionar item' }))
    await user.selectOptions(screen.getByLabelText('Tipo'), 'FEE')
    await user.type(screen.getByLabelText('Descrição'), 'Tarifa')
    await user.type(screen.getByLabelText('Valor (R$)'), '9,90')
    await user.click(screen.getByRole('button', { name: 'Salvar item' }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) =>
          String(url).includes('/invoices/invoice-1/items') && init.method === 'POST',
      )
      expect(call).toBeDefined()
      expect(JSON.parse(String(call?.[1].body))).toMatchObject({
        kind: 'FEE',
        reportingEffect: 'EXPENSE_INCREASE',
        description: 'Tarifa',
        amount: '9.90',
      })
    })

    await user.click(screen.getByRole('button', { name: 'Histórico (2)' }))
    expect(await screen.findByText(/25.00/)).toBeInTheDocument()
  })

  it('fatura paga permanece histórica e oculta ações de alteração', async () => {
    stubApi({
      ...INVOICE,
      status: 'PAID',
      outstanding: '0.00',
      paymentsTotal: '120.00',
    })
    renderPage()

    expect(await screen.findByText('Paga')).toBeInTheDocument()
    expect(
      screen.getByText(/Fatura paga é histórica: itens, valores e pagamentos/),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Adicionar item' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar pagamento' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remover' })).not.toBeInTheDocument()
  })

  it('registra pagamento parcial na conta escolhida', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Juros')
    await user.click(screen.getByRole('button', { name: 'Registrar pagamento' }))
    const dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText('Conta de pagamento'), 'acc-bank')
    await user.clear(within(dialog).getByLabelText('Valor (R$)'))
    await user.type(within(dialog).getByLabelText('Valor (R$)'), '40,00')
    await user.click(within(dialog).getByRole('button', { name: 'Registrar pagamento' }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) =>
          String(url).includes('/invoices/invoice-1/payments') && init.method === 'POST',
      )
      expect(call).toBeDefined()
      expect(JSON.parse(String(call?.[1].body))).toMatchObject({
        paymentAccountId: 'acc-bank',
        amount: '40.00',
      })
    })
  })
})
