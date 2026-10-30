import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Connection } from '@/features/integration/api'
import { ConnectionsList } from '@/features/integration/components/ConnectionsList'
import { renderWithProviders } from '@/test/render'

const CONNECTION: Connection = {
  id: 'conn-1',
  itemIdMasked: 'item…0001',
  institutionName: 'Banco Alfa',
  displayName: 'Banco da família',
  nameCustomized: true,
  status: 'SYNCED',
  lastSyncAt: '2026-10-23T12:00:00Z',
  accountCount: 1,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi() {
  const mock = vi.fn((url: string, init: RequestInit = {}) => {
    if (url.includes('/auth/csrf')) return new Response(null, { status: 204 })
    if (url.includes('/connections/conn-1/display-name') && init.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as { displayName: string }
      return jsonResponse({ ...CONNECTION, displayName: body.displayName })
    }
    if (url.includes('/connections/conn-1/display-name') && init.method === 'DELETE') {
      return jsonResponse({
        ...CONNECTION,
        displayName: CONNECTION.institutionName,
        nameCustomized: false,
      })
    }
    if (url.includes('/connections/conn-1') && init.method === 'DELETE') {
      return new Response(null, { status: 204 })
    }
    return new Response('{}', { status: 404 })
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

describe('remoção de conexão', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('desativa a conexão preservando histórico e dados', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ConnectionsList connections={[CONNECTION]} configured />)

    await user.click(screen.getByRole('button', { name: 'Remover' }))
    await user.click(screen.getByRole('button', { name: 'Desativar e manter dados' }))

    expect(await screen.findByText(/Conexão desativada/)).toBeInTheDocument()
    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) => String(url).includes('/connections/conn-1') && init?.method === 'DELETE',
      )
      expect(String(call?.[0])).not.toContain('deleteHistory=true')
    })
  })

  it('exclui a conexão e apenas os dados importados por ela', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ConnectionsList connections={[CONNECTION]} configured />)

    await user.click(screen.getByRole('button', { name: 'Remover' }))
    await user.click(screen.getByRole('button', { name: 'Excluir conexão e dados' }))

    expect(await screen.findByText(/Conexão e dados importados removidos/)).toBeInTheDocument()
    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([url, init]) => String(url).includes('/connections/conn-1') && init?.method === 'DELETE',
      )
      expect(String(call?.[0])).toContain('deleteHistory=true')
    })
  })

  it('renomeia e restaura o item preservando a instituição original', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderWithProviders(<ConnectionsList connections={[CONNECTION]} configured />)

    expect(screen.getByText('Instituição no Pluggy: Banco Alfa')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Renomear' }))
    const input = screen.getByLabelText('Nome de exibição')
    await user.clear(input)
    await user.type(input, 'Banco novo')
    await user.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Nome de exibição atualizado.')).toBeInTheDocument()
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/connections/conn-1/display-name'),
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ displayName: 'Banco novo' }),
        }),
      )
    })

    await user.click(screen.getByRole('button', { name: 'Restaurar nome original do Pluggy' }))

    expect(await screen.findByText('Nome original restaurado.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/connections/conn-1/display-name'),
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})
