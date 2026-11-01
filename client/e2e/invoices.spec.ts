import { expect, test, type Page } from '@playwright/test'

/**
 * E2E do fluxo de faturas (stub de API).
 *
 * Prova configuração de ciclo, detalhe, item manual e pagamento parcial sem
 * duplicar compras.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const CARD = {
  id: 'card-1',
  type: 'CREDIT',
  origin: 'IMPORTED',
  alias: 'Cartão Alfa',
  institutionName: 'Banco Alfa',
  currency: 'BRL',
  currentBalance: '0.00',
  status: 'ACTIVE',
}

const BANK = {
  id: 'acc-bank',
  type: 'BANK',
  origin: 'IMPORTED',
  alias: 'Conta Alfa',
  currency: 'BRL',
  currentBalance: '1000.00',
  status: 'ACTIVE',
}

interface State {
  invoice: Record<string, unknown>
  openInvoice: Record<string, unknown>
}

async function installStub(page: Page): Promise<State> {
  const state: State = {
    openInvoice: {
      id: 'invoice-open',
      cardAccountId: 'card-1',
      cycleStart: '2026-10-06',
      cycleEnd: '2026-11-05',
      closingDate: '2026-11-05',
      dueDate: '2026-11-15',
      status: 'OPEN',
      total: '0.00',
      paymentsTotal: '0.00',
      outstanding: '0.00',
      appliedCredit: '0.00',
      source: 'LOCAL',
      hasAdjustments: false,
      adjustmentCount: 0,
      items: [],
      payments: [],
    },
    invoice: {
      id: 'invoice-1',
      cardAccountId: 'card-1',
      cycleStart: '2026-09-06',
      cycleEnd: '2026-10-05',
      closingDate: '2026-10-05',
      dueDate: '2026-11-15',
      status: 'CLOSED',
      total: '100.00',
      paymentsTotal: '0.00',
      outstanding: '100.00',
      appliedCredit: '0.00',
      source: 'LOCAL',
      hasAdjustments: false,
      adjustmentCount: 0,
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
          occurredOn: '2026-10-03',
        },
      ],
      payments: [],
    },
  }

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/accounts') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              ...CARD,
              cardProfile: {
                accountId: 'card-1',
                closingDay: 5,
                dueDay: 15,
                creditLimit: '5000.00',
                creditBalance: '0.00',
              },
            },
            BANK,
          ],
        }),
      })
    }
    if (path.startsWith('/accounts/') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...CARD,
          cardProfile: {
            accountId: 'card-1',
            closingDay: 5,
            dueDay: 15,
            creditLimit: '5000.00',
            creditBalance: '0.00',
          },
        }),
      })
    }
    if (path === '/cards/card-1/profile' && method === 'PUT') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accountId: 'card-1',
          closingDay: 5,
          dueDay: 15,
          creditLimit: '5000.00',
          creditBalance: '0.00',
        }),
      })
    }
    if (path === '/cards/card-1/invoices') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [state.openInvoice, state.invoice], hasMore: false }),
      })
    }
    if (path === '/invoices/invoice-open' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.openInvoice),
      })
    }
    if (path === '/invoices/invoice-1' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.invoice),
      })
    }
    if (path === '/transactions' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: [
            {
              id: 'tx-pendente',
              accountId: 'card-1',
              source: 'IMPORTED',
              type: 'DEBIT',
              status: 'PENDING',
              postedOn: '2026-10-20',
              amount: '-50.00',
              currency: 'BRL',
              description: 'Compra pendente',
              invoiceId: null,
            },
          ],
          hasMore: false,
        }),
      })
    }
    if (path === '/invoices/invoice-1/items' && method === 'POST') {
      const item = {
        id: 'item-2',
        invoiceId: 'invoice-1',
        kind: 'INTEREST',
        reportingEffect: 'EXPENSE_INCREASE',
        source: 'MANUAL',
        description: 'Juros',
        amount: '20.00',
        occurredOn: '2026-10-04',
      }
      state.invoice = {
        ...state.invoice,
        total: '120.00',
        outstanding: '120.00',
        hasAdjustments: true,
        adjustmentCount: 1,
        items: [...(state.invoice.items as unknown[]), item],
      }
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(item),
      })
    }
    if (path === '/invoices/invoice-1/payments' && method === 'POST') {
      state.invoice = {
        ...state.invoice,
        status: 'PARTIALLY_PAID',
        paymentsTotal: '30.00',
        outstanding: '90.00',
        payments: [
          {
            id: 'payment-1',
            invoiceId: 'invoice-1',
            paymentAccountId: 'acc-bank',
            amount: '30.00',
            paidOn: '2026-10-23',
            status: 'CONFIRMED',
            source: 'MANUAL',
          },
        ],
      }
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify((state.invoice.payments as unknown[])[0]),
      })
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'NOT_FOUND', detail: 'Não encontrado.' }),
    })
  })

  return state
}

test('mostra compras pendentes na fatura aberta', async ({ page }) => {
  await installStub(page)

  await page.goto('/invoices/invoice-open')
  await expect(page.getByRole('heading', { name: /Fatura/ })).toBeVisible()
  await expect(page.getByText('Compras pendentes neste ciclo')).toBeVisible()
  await expect(page.getByText('Compra pendente')).toBeVisible()
  await expect(page.getByText(/ainda não compõem o total/)).toBeVisible()
})

test('configura ciclo, adiciona item manual e registra pagamento', async ({ page }) => {
  await installStub(page)

  await page.goto('/cards/card-1/invoices')
  await expect(page.getByRole('heading', { name: /Faturas/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Fatura atual' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Abrir fatura atual' })).toHaveAttribute(
    'href',
    '/invoices/invoice-open',
  )

  await page.getByRole('tab', { name: 'Compras pendentes' }).click()
  await expect(page.getByText('Compra pendente')).toBeVisible()

  await page.getByRole('tab', { name: 'Configurações' }).click()
  await page.getByRole('button', { name: 'Salvar ciclo' }).click()
  await expect(
    page
      .getByRole('tabpanel', { name: 'Configurações' })
      .getByText(/Faturas já fechadas ou pagas não são reescritas/),
  ).toBeVisible()

  await page.getByRole('tab', { name: 'Histórico' }).click()
  await page.getByRole('link', { name: /2026/ }).first().click()
  await expect(page.getByText('Compra importada')).toBeVisible()

  await page.getByRole('button', { name: 'Adicionar item' }).click()
  await page.getByLabel('Descrição').fill('Juros')
  await page.getByLabel('Valor (R$)').fill('20,00')
  await page.getByRole('button', { name: 'Salvar item' }).click()
  await expect(page.getByRole('listitem').filter({ hasText: 'Juros' }).first()).toBeVisible()

  await page.getByRole('button', { name: 'Registrar pagamento' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Conta de pagamento').selectOption('acc-bank')
  await dialog.getByLabel('Valor (R$)').fill('30,00')
  await dialog.getByRole('button', { name: 'Registrar pagamento' }).click()
  await expect(page.getByText('Parcialmente paga')).toBeVisible()
})
