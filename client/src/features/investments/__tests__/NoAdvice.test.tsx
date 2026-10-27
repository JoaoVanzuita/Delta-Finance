import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { stubInvestmentsApi } from '@/test/investmentsApiStub'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

describe('investimentos sem aconselhamento', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubInvestmentsApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('explica importação, cobertura e posições manuais sem prometer cotação', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Investimentos' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/posições importadas do Meu Pluggy/i)).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { name: 'Cobertura das conexões' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Posições manuais' })).toBeInTheDocument()
    expect(screen.getByText(/não calcula cotação em tempo real/i)).toBeInTheDocument()
  })

  it('não oferece recomendação, ranking ou chamada de compra, venda ou alocação', async () => {
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    expect(
      screen.getByText(/não há recomendação de compra, venda, alocação ou ranking/i),
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
