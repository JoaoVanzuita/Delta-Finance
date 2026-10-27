import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
    renderWithProviders(<TransactionsPage />)

    await screen.findByText('Conta de luz')
    await screen.findByText('Compra importada')

    const importedRow = screen.getByText('Compra importada').closest('li')
    const manualRow = screen.getByText('Conta de luz').closest('li')
    expect(importedRow).not.toBeNull()
    expect(manualRow).not.toBeNull()
    expect(
      within(importedRow as HTMLElement).queryByRole('button', { name: 'Excluir lançamento' }),
    ).not.toBeInTheDocument()

    await user.click(
      within(manualRow as HTMLElement).getByRole('button', { name: 'Excluir lançamento' }),
    )
    expect(screen.getByText(/Transferências manuais removem as duas pernas/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))

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
    renderWithProviders(<TransactionsPage />)

    await screen.findByText('Conta de luz')
    await user.click(screen.getByRole('button', { name: 'Excluir lançamento' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))

    expect(
      await screen.findByText(
        'Exclua primeiro os reembolsos/chargebacks vinculados a esta transação.',
      ),
    ).toBeInTheDocument()
  })
})
