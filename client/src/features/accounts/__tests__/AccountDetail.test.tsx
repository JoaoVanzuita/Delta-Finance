import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AccountDetailPage } from '@/features/accounts/pages/AccountDetailPage'
import { type Account } from '@/features/integration/api'
import { renderWithProviders } from '@/test/render'

const ACCOUNT: Account = {
  id: 'acc-1',
  type: 'BANK',
  subtype: 'CHECKING_ACCOUNT',
  origin: 'IMPORTED',
  institutionName: 'Banco Alfa',
  alias: 'Conta da Casa',
  providerName: 'Conta Alfa',
  nameCustomized: true,
  currency: 'BRL',
  currentBalance: '100.00',
  referenceBalance: '100.00',
  balanceReferenceAt: '2026-10-01T12:00:00Z',
  status: 'ACTIVE',
  lastSyncAt: '2026-10-23T12:00:00Z',
  connectionStatus: 'SYNCED',
}

let currentAccount: Account = { ...ACCOUNT }

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi() {
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    if (url.includes('/accounts/acc-1/display-name') && init.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as { displayName: string }
      currentAccount = { ...currentAccount, alias: body.displayName }
      return jsonResponse(currentAccount)
    }
    if (url.includes('/accounts/acc-1/display-name') && init.method === 'DELETE') {
      currentAccount = {
        ...currentAccount,
        alias: currentAccount.providerName ?? currentAccount.institutionName,
        nameCustomized: false,
      }
      return jsonResponse(currentAccount)
    }
    if (url.includes('/accounts/acc-1')) return jsonResponse(currentAccount)
    if (url.includes('/transactions')) {
      return jsonResponse({
        items: [
          {
            id: 'tx-1',
            accountId: 'acc-1',
            source: 'MANUAL',
            type: 'DEBIT',
            status: 'POSTED',
            postedOn: '2026-10-20',
            amount: '-45.00',
            currency: 'BRL',
            description: 'Conta de luz',
            categoryName: 'Casa',
          },
        ],
        hasMore: false,
      })
    }
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/accounts/:id" element={<AccountDetailPage />} />
    </Routes>,
    { route: '/accounts/acc-1' },
  )
}

describe('detalhe da conta com nome personalizado', () => {
  beforeEach(() => {
    currentAccount = { ...ACCOUNT }
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('exibe o nome original, renomeia e restaura o nome do Pluggy', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Conta da Casa' }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/Nome original: Conta Alfa/).length).toBeGreaterThan(0)

    await user.click(screen.getByRole('tab', { name: 'Configurações' }))
    const selectedTabs = screen.getAllByRole('tab', { selected: true })
    expect(selectedTabs).toHaveLength(1)
    expect(selectedTabs[0]).toHaveTextContent('Configurações')
    await user.click(screen.getByRole('button', { name: 'Renomear' }))
    const input = screen.getByLabelText('Nome de exibição')
    await user.clear(input)
    await user.type(input, 'Conta Nova')
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Conta Nova' })).toBeInTheDocument()
    expect(await screen.findByText('Nome de exibição atualizado.')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/accounts/acc-1/display-name'),
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ displayName: 'Conta Nova' }),
        }),
      )
    })

    await user.click(screen.getByRole('button', { name: 'Restaurar nome original do Pluggy' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Conta Alfa' })).toBeInTheDocument()
    expect(screen.queryByText(/Nome original: Conta Alfa/)).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/accounts/acc-1/display-name'),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })

  it('mostra movimentações recentes em aba própria', async () => {
    stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 1, name: 'Conta da Casa' })
    await user.click(screen.getByRole('tab', { name: 'Movimentações' }))
    expect(await screen.findByText('Conta de luz')).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*45,00/)).toBeInTheDocument()
  })

  it('mantém o editor fora de contas manuais', async () => {
    currentAccount = {
      ...ACCOUNT,
      origin: 'MANUAL',
      alias: 'Carteira',
      providerName: null,
      nameCustomized: false,
    }
    stubApi()
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Carteira' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Renomear' })).not.toBeInTheDocument()
  })
})
