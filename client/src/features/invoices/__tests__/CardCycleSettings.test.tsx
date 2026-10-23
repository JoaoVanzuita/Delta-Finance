import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CardCycleSettings } from '@/features/invoices/components/CardCycleSettings'
import { renderWithProviders } from '@/test/render'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('configuração do ciclo do cartão', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mostra estado de cartão sem ciclo e salva valores válidos', async () => {
    const fetchMock = vi.fn((url: string, init: RequestInit) => {
      if (url.includes('/cards/acc-1/profile') && init.method === 'PUT') {
        return jsonResponse({
          accountId: 'acc-1',
          closingDay: 5,
          dueDay: 15,
          creditLimit: '1000.00',
          creditBalance: '0.00',
        })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderWithProviders(<CardCycleSettings accountId="acc-1" profile={null} />)

    expect(screen.getByText(/ainda não tem ciclo configurado/i)).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Dia de fechamento'))
    await user.type(screen.getByLabelText('Dia de fechamento'), '5')
    await user.clear(screen.getByLabelText('Dia de vencimento'))
    await user.type(screen.getByLabelText('Dia de vencimento'), '15')
    await user.type(screen.getByLabelText('Limite (R$)'), '1000,00')
    await user.click(screen.getByRole('button', { name: 'Salvar ciclo' }))

    expect(
      await screen.findByText(/Faturas já fechadas ou pagas não são reescritas/),
    ).toBeInTheDocument()
    const putCall = fetchMock.mock.calls.find(([, init]) => init.method === 'PUT')
    expect(putCall).toBeDefined()
    expect(JSON.parse(String(putCall?.[1].body))).toMatchObject({
      closingDay: 5,
      dueDay: 15,
      creditLimit: '1000.00',
    })
  })

  it('valida dia 1–31 e limite não negativo antes de chamar a API', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderWithProviders(<CardCycleSettings accountId="acc-1" profile={null} />)

    await user.clear(screen.getByLabelText('Dia de fechamento'))
    await user.type(screen.getByLabelText('Dia de fechamento'), '0')
    await user.click(screen.getByRole('button', { name: 'Salvar ciclo' }))
    expect(
      await screen.findByText('O dia de fechamento deve estar entre 1 e 31.'),
    ).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Dia de fechamento'))
    await user.type(screen.getByLabelText('Dia de fechamento'), '5')
    await user.type(screen.getByLabelText('Limite (R$)'), '-1')
    await user.click(screen.getByRole('button', { name: 'Salvar ciclo' }))
    expect(await screen.findByText('O limite deve ser maior ou igual a zero.')).toBeInTheDocument()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('pré-carrega o perfil existente', () => {
    renderWithProviders(
      <CardCycleSettings
        accountId="acc-1"
        profile={{
          accountId: 'acc-1',
          closingDay: 10,
          dueDay: 20,
          creditLimit: '2500.00',
          creditBalance: '15.00',
        }}
      />,
    )

    expect(screen.getByLabelText('Dia de fechamento')).toHaveValue(10)
    expect(screen.getByLabelText('Dia de vencimento')).toHaveValue(20)
    expect(screen.getByLabelText('Limite (R$)')).toHaveValue('2500.00')
  })
})
