import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { OVERVIEW, stubInvestmentsApi } from '@/test/investmentsApiStub'
import { renderWithProviders } from '@/test/render'

import type { InvestmentPosition } from '@/features/integration/api'

function position(overrides: Partial<InvestmentPosition> = {}): InvestmentPosition {
  return {
    id: 'pos-1',
    broker: 'XP',
    assetName: 'Petrobras PN',
    ticker: 'PETR4',
    quantity: '10',
    referencePrice: '12.5',
    total: '125.00',
    referenceDate: '2026-10-20',
    origin: 'MANUAL',
    ...overrides,
  }
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

function manualSection(): HTMLElement {
  return screen.getByRole('region', { name: 'Posições manuais' })
}

describe('posições manuais de investimento', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('lista posições, total calculado e data de referência como dado do usuário', async () => {
    stubInvestmentsApi({
      positions: [
        position(),
        position({
          id: 'pos-2',
          broker: 'SANTANDER',
          assetName: 'Fundo imobiliário',
          ticker: 'HGLG11',
          quantity: '3',
          referencePrice: '33.33333333',
          total: '100.00',
          referenceDate: '2026-10-21',
        }),
      ],
      overview: {
        ...OVERVIEW,
        manualValue: '225.00',
        totalValue: '1975.00',
        manualCount: 2,
      },
    })

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    const manual = manualSection()
    expect(await within(manual).findByText('Petrobras PN')).toBeInTheDocument()
    expect(within(manual).getByText('PETR4')).toBeInTheDocument()
    expect(within(manual).getByText(/10 × R\$\s*12,50/)).toBeInTheDocument()
    expect(within(manual).getAllByText(/R\$\s*125,00/).length).toBeGreaterThan(0)
    expect(within(manual).getByText(/R\$\s*100,00/)).toBeInTheDocument()
    expect(within(manual).getByText(/20\/10\/2026/)).toBeInTheDocument()
    expect(within(manual).getAllByText(/21\/10\/2026/).length).toBeGreaterThan(0)
  })

  it('cria posição informando apenas o valor total', async () => {
    const mock = stubInvestmentsApi({ positions: [] })
    const user = userEvent.setup()

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    const manual = manualSection()
    await user.selectOptions(within(manual).getByLabelText('Corretora'), 'SANTANDER')
    await user.type(within(manual).getByLabelText('Ativo'), 'Fundo imobiliário')
    await user.type(within(manual).getByLabelText('Ticker'), 'HGLG11')
    await user.type(within(manual).getByLabelText('Quantidade'), '3')
    await user.click(within(manual).getByLabelText('Valor total'))
    await user.type(within(manual).getByLabelText('Valor total informado'), '100.00')
    fireEvent.change(within(manual).getByLabelText('Data de referência'), {
      target: { value: '2026-10-22' },
    })
    await user.click(within(manual).getByRole('button', { name: 'Adicionar posição' }))

    await waitFor(() => {
      const post = mock.mock.calls.find(
        ([url, init]) => String(url).endsWith('/investments/positions') && init?.method === 'POST',
      )
      expect(post).toBeDefined()
      expect(JSON.parse(String(post?.[1]?.body))).toEqual({
        broker: 'SANTANDER',
        assetName: 'Fundo imobiliário',
        ticker: 'HGLG11',
        quantity: '3',
        totalValue: '100.00',
        referenceDate: '2026-10-22',
      })
    })

    expect(await within(manualSection()).findByText('Fundo imobiliário')).toBeInTheDocument()
    const createdRow = within(manualSection()).getAllByRole('listitem')[0]!
    expect(within(createdRow).getByText(/R\$\s*100,00/)).toBeInTheDocument()
  })

  it('edita e exclui uma posição com confirmação explícita', async () => {
    const mock = stubInvestmentsApi({ positions: [position()] })
    const user = userEvent.setup()

    renderPage()

    const manual = manualSection()
    const row = await within(manual).findByRole('listitem')
    await user.click(within(row).getByRole('button', { name: 'Editar' }))
    const assetName = within(row).getByLabelText('Ativo')
    await user.clear(assetName)
    await user.type(assetName, 'Petrobras ON')
    await user.click(within(row).getByRole('button', { name: 'Salvar posição' }))

    await waitFor(() => {
      const patch = mock.mock.calls.find(([, init]) => init?.method === 'PATCH')
      expect(patch).toBeDefined()
      expect(JSON.parse(String(patch?.[1]?.body))).toMatchObject({
        assetName: 'Petrobras ON',
        referencePrice: '12.5',
      })
    })
    expect(await within(manualSection()).findByText('Petrobras ON')).toBeInTheDocument()

    const updatedRow = within(manualSection()).getAllByRole('listitem')[0]!
    await user.click(within(updatedRow).getByRole('button', { name: 'Excluir' }))
    await user.click(within(updatedRow).getByRole('button', { name: 'Confirmar exclusão' }))

    await waitFor(() => {
      expect(mock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
    })
    await waitFor(() => {
      expect(screen.queryByText('Petrobras ON')).not.toBeInTheDocument()
    })
  })

  it('mostra aviso de possível duplicidade com posição importada', async () => {
    stubInvestmentsApi({
      positions: [position({ id: 'pos-dup', assetName: 'Duplicada', ticker: 'AAAA11' })],
      overview: {
        ...OVERVIEW,
        duplicates: [
          {
            manualPositionId: 'pos-dup',
            code: 'AAAA11',
            providerInvestmentId: 'holding-1',
          },
        ],
      },
    })

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    expect(
      await within(manualSection()).findByText(/já existe uma posição importada com este código/i),
    ).toBeInTheDocument()
  })

  it('mostra erro de validação sem enviar quantidade ou valor inválidos', async () => {
    const mock = stubInvestmentsApi({ positions: [] })
    const user = userEvent.setup()

    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    const manual = manualSection()
    await user.type(within(manual).getByLabelText('Ativo'), 'Ativo sem quantidade')
    fireEvent.change(within(manual).getByLabelText('Data de referência'), {
      target: { value: '2026-10-22' },
    })
    await user.click(within(manual).getByRole('button', { name: 'Adicionar posição' }))

    expect(await within(manual).findByRole('alert')).toHaveTextContent(/Informe a quantidade/i)
    expect(mock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
  })
})
