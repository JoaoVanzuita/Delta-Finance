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
  status: 'SYNCED',
  lastSyncAt: '2026-10-23T12:00:00Z',
  accountCount: 1,
}

function stubApi() {
  const mock = vi.fn((url: string, init: RequestInit) => {
    if (url.includes('/auth/csrf')) return new Response(null, { status: 204 })
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
        ([url, init]) => String(url).includes('/connections/conn-1') && init.method === 'DELETE',
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
        ([url, init]) => String(url).includes('/connections/conn-1') && init.method === 'DELETE',
      )
      expect(String(call?.[0])).toContain('deleteHistory=true')
    })
  })
})
