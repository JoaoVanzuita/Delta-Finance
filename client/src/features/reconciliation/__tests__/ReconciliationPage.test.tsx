import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ReconciliationPage } from '@/features/reconciliation/pages/ReconciliationPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const MATCH = {
  id: 'match-1',
  kind: 'TRANSFER',
  debitTransactionId: 'tx-debit',
  creditTransactionId: 'tx-credit',
  score: 100,
  status: 'PENDING',
  criteriaVersion: 'v1',
  reason: 'Valores e datas compatíveis.',
}

const DEBIT = {
  id: 'tx-debit',
  accountId: 'acc-1',
  source: 'MANUAL',
  type: 'DEBIT',
  status: 'POSTED',
  postedOn: '2026-10-20',
  amount: '-100.00',
  currency: 'BRL',
  description: 'Saída da conta',
  reconciliationState: 'PENDING_MATCH',
}

const CREDIT = {
  ...DEBIT,
  id: 'tx-credit',
  accountId: 'acc-2',
  type: 'CREDIT',
  amount: '100.00',
  description: 'Entrada na poupança',
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(transactions: readonly unknown[] = [DEBIT, CREDIT]) {
  let confirmed = false
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    if (url.includes('/internal-matches/match-1/confirm') && init.method === 'POST') {
      confirmed = true
      return jsonResponse({ ...MATCH, status: 'CONFIRMED' })
    }
    if (url.includes('/internal-matches')) {
      return jsonResponse({ items: confirmed ? [] : [MATCH], hasMore: false })
    }
    if (url.includes('/transactions')) return jsonResponse({ items: transactions, hasMore: false })
    if (url.includes('/accounts')) {
      return jsonResponse({
        items: [
          { id: 'acc-1', alias: 'Conta Casa', currency: 'BRL', status: 'ACTIVE', type: 'BANK' },
          { id: 'acc-2', alias: 'Poupança', currency: 'BRL', status: 'ACTIVE', type: 'BANK' },
        ],
      })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('reconciliação', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('compara as duas pernas, mostra diferença e confirma explicitamente', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ReconciliationPage />, { route: '/reconciliation' })

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Reconciliação' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Saída da conta')).toBeInTheDocument()
    expect(screen.getByText('Entrada na poupança')).toBeInTheDocument()
    expect(screen.getByText('Diferença entre as pernas (BRL)')).toBeInTheDocument()
    expect(screen.getByText('R$ 0,00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirmar correspondência' }))
    expect(
      await screen.findByRole('heading', { name: 'Nenhuma sugestão pendente' }),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/match-1/confirm'))).toBe(
      true,
    )
  })

  it('bloqueia confirmação quando uma perna não está disponível', async () => {
    stubApi([DEBIT])
    renderWithProviders(<ReconciliationPage />, { route: '/reconciliation' })

    expect(await screen.findByText('Transação não disponível.')).toBeInTheDocument()
    expect(screen.getByText(/Dados insuficientes para confirmar/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar correspondência' })).toBeDisabled()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    stubApi()
    renderWithProviders(<ReconciliationPage />, { route: '/reconciliation' })
    await screen.findByText('Saída da conta')
    await expectNoA11yViolations()
  })
})
