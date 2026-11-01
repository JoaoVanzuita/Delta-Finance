import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { TransactionsPage } from '@/features/transactions/pages/TransactionsPage'
import { renderWithProviders } from '@/test/render'

const MANUAL = {
  id: 'manual-1',
  accountId: 'acc-1',
  categoryId: 'cat-1',
  categoryName: 'Casa',
  source: 'MANUAL',
  type: 'DEBIT',
  status: 'POSTED',
  postedOn: '2026-10-20',
  amount: '-45.00',
  currency: 'BRL',
  description: 'Conta de luz',
  editedFields: [],
  reconciliationState: 'NONE',
  notes: null,
  adjustmentReason: null,
  relatedTransactionId: null,
  counterpartTransactionId: null,
  invoiceId: null,
}

const IMPORTED = {
  ...MANUAL,
  id: 'imported-1',
  source: 'IMPORTED',
  description: 'Compra importada',
  amount: '-30.00',
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.search}</output>
}

function renderTransactions(route = '/transactions') {
  return renderWithProviders(
    <>
      <TransactionsPage />
      <LocationProbe />
    </>,
    { route },
  )
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(deleteResponse: () => Response = () => jsonResponse(null, 204)) {
  const mock = vi.fn((url: string, init: RequestInit) => {
    if (url.includes('/transactions/manual-1') && init.method === 'DELETE') {
      return deleteResponse()
    }
    if (url.includes('/transactions')) {
      return jsonResponse({ items: [MANUAL, IMPORTED], hasMore: false })
    }
    if (url.includes('/accounts')) {
      return jsonResponse({
        items: [
          {
            id: 'acc-1',
            type: 'BANK',
            origin: 'MANUAL',
            alias: 'Conta Casa',
            currency: 'BRL',
            currentBalance: '500.00',
            status: 'ACTIVE',
          },
        ],
      })
    }
    if (url.includes('/categories')) {
      return jsonResponse({
        items: [{ id: 'cat-1', name: 'Casa', kind: 'EXPENSE', status: 'ACTIVE' }],
      })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('exclusão de lançamento manual', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('só oferece exclusão para lançamento manual e exige confirmação', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderTransactions()

    await screen.findByText('Conta de luz')
    await screen.findByText('Compra importada')

    await user.click(screen.getByText('Compra importada'))
    let drawer = await screen.findByRole('dialog', { name: 'Detalhes da transação' })
    expect(
      within(drawer).queryByRole('button', { name: 'Excluir lançamento' }),
    ).not.toBeInTheDocument()
    await user.keyboard('{Escape}')

    await user.click(screen.getByText('Conta de luz'))
    drawer = await screen.findByRole('dialog', { name: 'Detalhes da transação' })
    await user.click(within(drawer).getByRole('button', { name: 'Excluir lançamento' }))
    expect(
      within(drawer).getByText(/Transferências manuais removem as duas pernas/),
    ).toBeInTheDocument()

    await user.click(within(drawer).getByRole('button', { name: 'Confirmar exclusão' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/transactions/manual-1',
        expect.objectContaining({ method: 'DELETE' }),
      )
    })
    expect(await screen.findByText('Lançamento excluído.')).toBeInTheDocument()
  })

  it('mostra o erro do servidor ao recusar a exclusão', async () => {
    stubApi(() =>
      jsonResponse(
        {
          type: 'about:blank',
          title: 'Conflito',
          status: 409,
          code: 'TRANSACTION_HAS_RELATED_ENTRIES',
          detail: 'Exclua primeiro os reembolsos/chargebacks vinculados a esta transação.',
        },
        409,
      ),
    )
    const user = userEvent.setup()
    renderTransactions()

    await user.click(await screen.findByText('Conta de luz'))
    const drawer = await screen.findByRole('dialog', { name: 'Detalhes da transação' })
    await user.click(within(drawer).getByRole('button', { name: 'Excluir lançamento' }))
    await user.click(within(drawer).getByRole('button', { name: 'Confirmar exclusão' }))

    expect(
      await screen.findByText(
        'Exclua primeiro os reembolsos/chargebacks vinculados a esta transação.',
      ),
    ).toBeInTheDocument()
  })

  it('busca apenas nos registros carregados', async () => {
    stubApi()
    const user = userEvent.setup()
    renderTransactions()

    await screen.findByText('Conta de luz')
    await user.type(screen.getByLabelText('Buscar nas transações carregadas'), 'luz')

    expect(screen.getAllByText('Conta de luz').length).toBeGreaterThan(0)
    expect(screen.queryByText('Compra importada')).not.toBeInTheDocument()
    expect(screen.getByText(/Busca local em 2 transação/)).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Buscar nas transações carregadas'))
    expect(await screen.findByText('Compra importada')).toBeInTheDocument()
  })

  it('aplica filtros avançados e preserva na URL', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderTransactions()

    await screen.findByText('Conta de luz')
    await user.click(screen.getByRole('button', { name: /Filtros/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Filtros de transações' })
    await user.selectOptions(within(dialog).getByLabelText('Estado'), 'PENDING')
    await user.selectOptions(within(dialog).getByLabelText('Tipo'), 'DEBIT')
    await user.click(within(dialog).getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      const location = screen.getByTestId('location').textContent ?? ''
      expect(location).toContain('status=PENDING')
      expect(location).toContain('type=DEBIT')
    })
    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(
          ([url]) =>
            String(url).includes('/transactions') &&
            String(url).includes('status=PENDING') &&
            String(url).includes('type=DEBIT'),
        ),
      ).toBe(true)
    })
    expect(screen.getByText('Estado: Pendente')).toBeInTheDocument()
  })
})
