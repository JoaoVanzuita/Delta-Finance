import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ManualEntryForm } from '@/features/transactions/components/ManualEntryForm'
import { renderWithProviders } from '@/test/render'

const ACCOUNTS = {
  items: [
    {
      id: 'acc-1',
      type: 'CASH',
      origin: 'MANUAL',
      currency: 'BRL',
      currentBalance: '100.00',
      alias: 'Carteira',
      status: 'ACTIVE',
    },
    {
      id: 'acc-2',
      type: 'BANK',
      origin: 'MANUAL',
      currency: 'BRL',
      currentBalance: '500.00',
      alias: 'Conta Casa',
      status: 'ACTIVE',
    },
  ],
}

const CATEGORIES = {
  items: [{ id: 'cat-1', name: 'Alimentação', kind: 'EXPENSE', predefined: true, active: true }],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(onCreate?: (body: unknown) => Response) {
  const mock = vi.fn((url: string, init: RequestInit) => {
    if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
    if (url.includes('/categories')) return jsonResponse(CATEGORIES)
    if (url.includes('/transactions') && init.method !== 'POST') {
      return jsonResponse({ items: [], hasMore: false })
    }
    if (url.includes('/transactions') && init.method === 'POST') {
      return onCreate ? onCreate(JSON.parse(String(init.body))) : jsonResponse({ id: 'tx-1' }, 201)
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('formulário de lançamento manual', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('valida campos obrigatórios antes de chamar a API', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ManualEntryForm />)

    await screen.findAllByRole('option', { name: 'Carteira' })
    await user.click(screen.getByRole('button', { name: 'Registrar lançamento' }))

    expect(await screen.findByText('Selecione a conta.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init.method === 'POST')).toBe(false)
  })

  it('envia despesa com conta, data e descrição', async () => {
    let posted: Record<string, unknown> | undefined
    stubApi((body) => {
      posted = body as Record<string, unknown>
      return jsonResponse({ id: 'tx-1', type: 'DEBIT' }, 201)
    })
    const user = userEvent.setup()
    renderWithProviders(<ManualEntryForm />)

    await screen.findAllByRole('option', { name: 'Carteira' })
    await user.selectOptions(screen.getByLabelText('Conta'), 'acc-1')
    await user.type(screen.getByLabelText('Valor (R$)'), '25,90')
    await user.type(screen.getByLabelText('Descrição'), 'Feira')
    await user.selectOptions(screen.getByLabelText('Categoria'), 'cat-1')
    await user.click(screen.getByRole('button', { name: 'Registrar lançamento' }))

    expect(await screen.findByText('Lançamento registrado.')).toBeInTheDocument()
    expect(posted).toMatchObject({
      accountId: 'acc-1',
      type: 'DEBIT',
      amount: '25.90',
      description: 'Feira',
      categoryId: 'cat-1',
    })
  })

  it('exige conta de destino diferente na transferência', async () => {
    stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ManualEntryForm initialType="TRANSFER" />)

    await screen.findAllByRole('option', { name: 'Carteira' })
    await user.selectOptions(screen.getByLabelText('Conta'), 'acc-1')
    await user.type(screen.getByLabelText('Valor (R$)'), '10.00')
    await user.type(screen.getByLabelText('Descrição'), 'Transferência')
    await user.click(screen.getByRole('button', { name: 'Registrar lançamento' }))

    expect(
      await screen.findByText('Selecione uma conta de destino diferente da origem.'),
    ).toBeInTheDocument()
  })

  it('exibe erro acionável do servidor', async () => {
    stubApi(
      () =>
        new Response(
          JSON.stringify({
            type: 'urn:delta-finance:error:account_inactive',
            title: 'Conflict',
            status: 409,
            code: 'ACCOUNT_INACTIVE',
            detail: 'A conta está inativa e não recebe novos lançamentos.',
            requestId: 'req-test',
          }),
          { status: 409, headers: { 'content-type': 'application/problem+json' } },
        ),
    )
    const user = userEvent.setup()
    renderWithProviders(<ManualEntryForm />)

    await screen.findAllByRole('option', { name: 'Carteira' })
    await user.selectOptions(screen.getByLabelText('Conta'), 'acc-1')
    await user.type(screen.getByLabelText('Valor (R$)'), '10')
    await user.type(screen.getByLabelText('Descrição'), 'Teste')
    await user.click(screen.getByRole('button', { name: 'Registrar lançamento' }))

    await waitFor(() =>
      expect(
        screen.getByText('A conta está inativa e não recebe novos lançamentos.'),
      ).toBeInTheDocument(),
    )
  })
})
