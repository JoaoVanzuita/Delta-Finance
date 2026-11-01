import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { stubInvestmentsApi } from '@/test/investmentsApiStub'
import { renderWithProviders } from '@/test/render'

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/investments" element={<InvestmentsPage />} />
    </Routes>,
    { route: '/investments' },
  )
}

describe('proventos manuais e custo informado', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('registra, edita e exclui provento manual', async () => {
    const mock = stubInvestmentsApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await screen.findByRole('tab', { name: 'Proventos' })
    await user.click(screen.getByRole('tab', { name: 'Proventos' }))
    await user.click(await screen.findByRole('button', { name: 'Registrar provento' }))
    const createDialog = await screen.findByRole('dialog', { name: 'Registrar provento manual' })
    await user.selectOptions(within(createDialog).getByLabelText('Posição'), 'holding-1')
    await user.type(within(createDialog).getByLabelText('Valor (BRL)'), '7.50')
    await user.type(within(createDialog).getByLabelText('Descrição (opcional)'), 'Dividendo manual')
    await user.click(within(createDialog).getByRole('button', { name: 'Registrar provento' }))

    await waitFor(() => {
      const post = mock.mock.calls.find(
        ([url, init]) => String(url).endsWith('/investments/income') && init?.method === 'POST',
      )
      expect(post).toBeDefined()
      expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({
        providerInvestmentId: 'holding-1',
        incomeType: 'DIVIDEND',
        amount: '7.50',
        description: 'Dividendo manual',
      })
    })

    const income = await screen.findByRole('region', { name: 'Proventos' })
    expect(await within(income).findByRole('cell', { name: 'Dividendo' })).toBeInTheDocument()
    expect(within(income).getByText('Manual')).toBeInTheDocument()

    const manualRow = within(income).getByRole('cell', { name: 'Dividendo' }).closest('tr')
    expect(manualRow).not.toBeNull()
    await user.click(within(manualRow as HTMLElement).getByRole('button', { name: 'Editar' }))
    const editDialog = await screen.findByRole('dialog', { name: 'Editar provento manual' })
    const amount = within(editDialog).getByLabelText('Valor (BRL)')
    await user.clear(amount)
    await user.type(amount, '8.00')
    await user.click(within(editDialog).getByRole('button', { name: 'Salvar provento' }))

    await waitFor(() => {
      const patch = mock.mock.calls.find(
        ([url, init]) => String(url).includes('/investments/income/') && init?.method === 'PATCH',
      )
      expect(patch).toBeDefined()
      expect(JSON.parse(String(patch?.[1]?.body))).toMatchObject({ amount: '8.00' })
    })

    const editedRow = within(await screen.findByRole('region', { name: 'Proventos' }))
      .getByRole('cell', { name: 'Dividendo' })
      .closest('tr')
    expect(editedRow).not.toBeNull()
    await user.click(within(editedRow as HTMLElement).getByRole('button', { name: 'Excluir' }))
    await user.click(within(editedRow as HTMLElement).getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => {
      expect(
        mock.mock.calls.some(
          ([url, init]) =>
            String(url).includes('/investments/income/') && init?.method === 'DELETE',
        ),
      ).toBe(true)
    })
    await waitFor(() => {
      expect(
        within(screen.getByRole('region', { name: 'Proventos' })).queryByRole('cell', {
          name: 'Dividendo',
        }),
      ).not.toBeInTheDocument()
    })
  })

  it('salva custo informado e classificacao do usuario', async () => {
    const mock = stubInvestmentsApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Investimentos' })
    await screen.findByRole('tab', { name: 'Posições' })
    await user.click(screen.getByRole('tab', { name: 'Posições' }))
    const card = (await screen.findByRole('heading', { level: 3, name: 'FII Alfa' })).closest(
      'li',
    ) as HTMLElement
    await user.click(within(card).getByRole('button', { name: 'Ver detalhes' }))
    const drawer = await screen.findByRole('dialog', { name: 'FII Alfa' })
    await user.type(within(drawer).getByLabelText('Custo informado (BRL)'), '900.00')
    await user.click(within(drawer).getByRole('button', { name: 'Salvar custo' }))

    await waitFor(() => {
      const patch = mock.mock.calls.find(
        ([url, init]) =>
          String(url).endsWith('/holdings/holding-1/cost-basis') && init?.method === 'PATCH',
      )
      expect(patch).toBeDefined()
      expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ costBasis: '900.00' })
    })
    expect(await within(drawer).findByText('Custo informado atualizado.')).toBeInTheDocument()

    await user.selectOptions(within(drawer).getByLabelText('Classificação'), 'ACAO')
    await user.click(within(drawer).getByRole('button', { name: 'Salvar classificação' }))
    await waitFor(() => {
      const patch = mock.mock.calls.find(
        ([url, init]) =>
          String(url).endsWith('/holdings/holding-1/asset-class') && init?.method === 'PATCH',
      )
      expect(patch).toBeDefined()
      expect(JSON.parse(String(patch?.[1]?.body))).toEqual({ assetClass: 'ACAO' })
    })
  })
})
