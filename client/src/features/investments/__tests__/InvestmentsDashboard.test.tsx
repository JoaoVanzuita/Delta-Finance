import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { INCOME, stubInvestmentsApi } from '@/test/investmentsApiStub'
import { renderWithProviders } from '@/test/render'

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

async function region(name: string): Promise<HTMLElement> {
  return screen.findByRole('region', { name })
}

describe('dashboard de investimentos', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mostra resumo, alocação e resultado informado pelo provedor', async () => {
    stubInvestmentsApi()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    const summary = await screen.findByRole('region', { name: 'Resumo' })
    expect(within(summary).getByText('R$ 1.750,00')).toBeInTheDocument()
    expect(within(summary).queryByText('Manual')).not.toBeInTheDocument()
    expect(within(summary).getAllByText('R$ 50,00').length).toBeGreaterThanOrEqual(2)
    expect(within(summary).getByText(/informado para 1 de 3 posições ativas/i)).toBeInTheDocument()

    const allocation = await region('Alocação')
    expect(within(allocation).getAllByText('Fundos imobiliários').length).toBeGreaterThan(0)
    expect(within(allocation).getAllByText('Renda fixa').length).toBeGreaterThan(0)
    expect(within(allocation).getAllByText(/85,7%/).length).toBeGreaterThan(0)
  })

  it('abre as posições da classe ao clicar na fatia da alocação', async () => {
    stubInvestmentsApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    const allocation = await region('Alocação')
    expect(
      within(allocation).getByRole('img', { name: /Alocação por classe de investimento/i }),
    ).toBeInTheDocument()

    await user.click(within(allocation).getByRole('button', { name: /Fundos imobiliários:/ }))
    const classDialog = await screen.findByRole('dialog', { name: 'Fundos imobiliários' })
    expect(within(classDialog).getByText('FII Alfa')).toBeInTheDocument()
    expect(within(classDialog).getByText('FII Beta')).toBeInTheDocument()

    await user.click(within(classDialog).getByRole('button', { name: /FII Alfa/ }))
    expect(await screen.findByRole('dialog', { name: 'FII Alfa' })).toBeInTheDocument()
  })

  it('filtra posições ativas e encerradas', async () => {
    stubInvestmentsApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await screen.findByRole('tab', { name: 'Posições' })
    await user.click(screen.getByRole('tab', { name: 'Posições' }))
    const holdings = await region('Posições importadas')
    expect(await within(holdings).findByText('FII Alfa')).toBeInTheDocument()
    expect(within(holdings).getByText('CDB Alfa')).toBeInTheDocument()
    expect(within(holdings).queryByText('FII Gama')).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Situação'), 'CLOSED')
    expect(
      await within(await region('Posições importadas')).findByText('FII Gama'),
    ).toBeInTheDocument()
    expect(
      within(await region('Posições importadas')).queryByText('FII Alfa'),
    ).not.toBeInTheDocument()
  })

  it('mostra proventos do provedor por mês e por ativo', async () => {
    stubInvestmentsApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await screen.findByRole('tab', { name: 'Proventos' })
    await user.click(screen.getByRole('tab', { name: 'Proventos' }))
    const income = await region('Proventos')
    expect(within(income).getAllByText(/R\$\s*12,50/).length).toBeGreaterThan(0)
    expect(within(income).getAllByText('2026-10').length).toBeGreaterThan(0)
    expect(within(income).getByRole('img', { name: /Proventos mensais/i })).toBeInTheDocument()
    expect(within(income).getAllByText('FII Alfa').length).toBeGreaterThan(0)
  })

  it('mostra estado vazio quando o provedor não informa proventos', async () => {
    stubInvestmentsApi({
      income: {
        ...INCOME,
        total: '0.00',
        source: 'UNAVAILABLE',
        byMonth: [],
        items: [],
      },
    })
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await screen.findByRole('tab', { name: 'Proventos' })
    await user.click(screen.getByRole('tab', { name: 'Proventos' }))
    expect(
      within(await region('Proventos')).getByText(/nenhum provento informado pelo provedor/i),
    ).toBeInTheDocument()
  })

  it('não exibe cobertura nem cadastro de posição manual', async () => {
    stubInvestmentsApi()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    expect(
      await screen.findByRole('heading', { name: 'Cobertura da importação' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Compartilha investimentos')).toBeInTheDocument()
    expect(screen.getByText('Não compartilha investimentos')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Posições manuais' })).not.toBeInTheDocument()
  })
})
