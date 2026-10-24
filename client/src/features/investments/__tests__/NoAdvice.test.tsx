import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const USER = {
  id: 'user-1',
  username: 'alice.invest',
  createdAt: '2026-10-20T12:00:00Z',
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
      if (url.includes('/auth/me')) return jsonResponse(USER)
      if (url.includes('/investments/positions')) {
        return jsonResponse({
          items: [
            {
              id: 'pos-1',
              broker: 'XP',
              assetName: 'Petrobras PN',
              ticker: 'PETR4',
              quantity: '10',
              referencePrice: '12.5',
              total: '125.00',
              referenceDate: '2026-10-20',
              origin: 'MANUAL',
            },
          ],
          hasMore: false,
        })
      }
      return jsonResponse({}, 404)
    }),
  )
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

describe('investimentos sem aconselhamento e sem importação automática', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('declara que não há importação automática validada', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Investimentos' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/não há importação automática de investimentos validada/i),
    ).toBeInTheDocument()
    expect(screen.getByText(/registro manual/i)).toBeInTheDocument()
  })

  it('não oferece recomendação, ranking ou chamada de compra, venda ou alocação', async () => {
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    expect(
      screen.getByText(/não oferece recomendação de compra, venda ou alocação/i),
    ).toBeInTheDocument()

    const forbidden = /comprar|vender|investir agora|melhor investimento/i
    expect(screen.queryByRole('button', { name: forbidden })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: forbidden })).not.toBeInTheDocument()
    expect(screen.queryByText(forbidden)).not.toBeInTheDocument()
  })

  it('mantém acessibilidade WCAG 2.1 A/AA', async () => {
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await expectNoA11yViolations()
  })
})
