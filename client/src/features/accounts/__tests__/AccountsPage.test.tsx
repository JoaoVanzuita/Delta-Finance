import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AccountsPage } from '@/features/accounts/pages/AccountsPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const ACCOUNTS = {
  items: [
    {
      id: 'acc-bank',
      type: 'BANK',
      origin: 'IMPORTED',
      institutionName: 'Banco Alfa',
      alias: 'Conta Corrente',
      currency: 'BRL',
      currentBalance: '100.00',
      status: 'ACTIVE',
    },
    {
      id: 'acc-cash',
      type: 'CASH',
      origin: 'MANUAL',
      institutionName: null,
      alias: 'Carteira',
      currency: 'BRL',
      currentBalance: '50.00',
      referenceBalance: '50.00',
      balanceReferenceAt: '2026-10-01T12:00:00Z',
      status: 'ACTIVE',
    },
    {
      id: 'acc-usd',
      type: 'BANK',
      origin: 'IMPORTED',
      institutionName: 'Banco Dólar',
      alias: 'Conta Dólar',
      currency: 'USD',
      currentBalance: '25.00',
      status: 'ACTIVE',
    },
    {
      id: 'acc-inactive',
      type: 'WALLET',
      origin: 'MANUAL',
      institutionName: null,
      alias: 'Carteira antiga',
      currency: 'BRL',
      currentBalance: '999.00',
      referenceBalance: '999.00',
      status: 'INACTIVE',
    },
    {
      id: 'card-1',
      type: 'CREDIT',
      origin: 'IMPORTED',
      alias: 'Cartão',
      currency: 'BRL',
      currentBalance: '0.00',
      status: 'ACTIVE',
    },
  ],
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
      if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
      return jsonResponse({}, 404)
    }),
  )
}

describe('lista de contas', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('consolida apenas contas ativas em BRL e agrupa por instituição', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AccountsPage />, { route: '/accounts' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Contas' })).toBeInTheDocument()
    expect(await screen.findByText(/R\$\s*150,00/)).toBeInTheDocument()
    expect(screen.getByText('Banco Alfa')).toBeInTheDocument()
    expect(screen.getByText('Sem instituição informada')).toBeInTheDocument()
    expect(screen.getByText('Conta Corrente')).toBeInTheDocument()
    expect(screen.getByText('Carteira')).toBeInTheDocument()
    expect(screen.queryByText('Cartão')).not.toBeInTheDocument()

    await user.click(screen.getByText('Outras contas'))
    expect(screen.getByText('Conta Dólar')).toBeInTheDocument()
    expect(screen.getByText('Moeda USD')).toBeInTheDocument()
    expect(screen.getByText('Carteira antiga')).toBeInTheDocument()
    expect(screen.getByText(/não entram no total em BRL/)).toBeInTheDocument()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(<AccountsPage />, { route: '/accounts' })
    await screen.findByText(/R\$\s*150,00/)
    await expectNoA11yViolations()
  })
})
