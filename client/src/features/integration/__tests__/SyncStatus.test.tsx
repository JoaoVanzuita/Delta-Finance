import { screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { Connection, SyncRunPage, SyncStatus } from '@/features/integration/api'
import { SyncStatusPanel } from '@/features/integration/components/SyncStatusPanel'
import { renderWithProviders } from '@/test/render'

const CONNECTION: Connection = {
  id: 'conn-1',
  itemIdMasked: 'item…0001',
  institutionName: 'Banco Alfa',
  status: 'SYNCED',
  lastSyncAt: '2026-10-23T12:00:00Z',
  accountCount: 1,
}

const STATUS: SyncStatus = {
  integrations: [
    {
      id: 'integration-1',
      provider: 'MEU_PLUGGY',
      clientIdMasked: 'clie…0001',
      status: 'SYNCED',
      lastSyncAttemptAt: '2026-10-23T12:00:00Z',
      lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
      connectionCount: 1,
    },
  ],
  connections: [CONNECTION],
  pendingJobs: 2,
}

const RUNS: SyncRunPage = {
  items: [
    {
      id: 'run-1',
      integrationId: 'integration-1',
      connectionId: 'conn-1',
      status: 'SUCCESS',
      startedAt: '2026-10-23T12:00:00Z',
      finishedAt: '2026-10-23T12:00:05Z',
      readCount: 12,
      createdCount: 3,
      updatedCount: 9,
      skippedCount: 0,
      failedCount: 0,
    },
  ],
  hasMore: false,
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(options: {
  readonly status?: SyncStatus
  readonly runs?: SyncRunPage
  readonly fail?: boolean
}) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (options.fail) {
        return new Response(JSON.stringify({ code: 'INTERNAL_ERROR' }), {
          status: 500,
          headers: { 'content-type': 'application/problem+json' },
        })
      }
      if (url.includes('/sync/status')) {
        return jsonResponse(options.status ?? STATUS)
      }
      if (url.includes('/sync/runs')) {
        return jsonResponse(options.runs ?? RUNS)
      }
      return new Response('{}', { status: 404, headers: { 'content-type': 'application/json' } })
    }),
  )
}

describe('painel de sincronização', () => {
  beforeEach(() => {
    stubApi({})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exibe estado, última tentativa, sucesso e fila sem prometer tempo real', async () => {
    renderWithProviders(<SyncStatusPanel connections={[CONNECTION]} />)

    expect(await screen.findByText('Sincronizada')).toBeInTheDocument()
    expect(screen.getByText(/não força a instituição de origem a atualizar/i)).toBeInTheDocument()

    const queue = screen.getByText('Consultas na fila').closest('div')
    expect(queue).not.toBeNull()
    expect(within(queue as HTMLElement).getByText('2')).toBeInTheDocument()

    expect(await screen.findByText('Concluída')).toBeInTheDocument()
    expect(screen.getByText(/12 lida\(s\)/i)).toBeInTheDocument()
    expect(screen.getByText(/3 nova\(s\)/i)).toBeInTheDocument()
  })

  it('mostra orientação acionável para consentimento expirado', async () => {
    const expiredConnection: Connection = {
      ...CONNECTION,
      status: 'AUTHORIZATION_EXPIRED',
      lastErrorCode: 'CONSENT_EXPIRED',
    }
    stubApi({
      status: {
        integrations: [
          {
            id: 'integration-1',
            provider: 'MEU_PLUGGY',
            status: 'AUTHORIZATION_EXPIRED',
            lastSyncAttemptAt: '2026-10-23T12:00:00Z',
            lastErrorCode: 'CONSENT_EXPIRED',
            connectionCount: 1,
          },
        ],
        connections: [expiredConnection],
        pendingJobs: 0,
      },
      runs: {
        items: [
          {
            id: 'run-2',
            status: 'AUTH_ERROR',
            startedAt: '2026-10-23T12:00:00Z',
            errorCode: 'CONSENT_EXPIRED',
          },
        ],
        hasMore: false,
      },
    })

    renderWithProviders(<SyncStatusPanel connections={[expiredConnection]} />)

    expect(await screen.findByText('Ação necessária')).toBeInTheDocument()
    expect(screen.getAllByText(/consentimento bancário expirou/i).length).toBeGreaterThan(0)
    expect(await screen.findByText('Falha de autorização')).toBeInTheDocument()
    expect(screen.getAllByText(/consentimento bancário expirou/i).length).toBeGreaterThan(0)
  })

  it('exibe estado vazio de histórico e erro de carregamento', async () => {
    stubApi({ runs: { items: [], hasMore: false } })
    const { unmount } = renderWithProviders(<SyncStatusPanel connections={[]} />)

    expect(await screen.findByText('Nenhuma sincronização registrada ainda.')).toBeInTheDocument()
    unmount()

    stubApi({ fail: true })
    renderWithProviders(<SyncStatusPanel connections={[]} />)
    await waitFor(() => expect(screen.getAllByRole('alert').length).toBeGreaterThan(0))
  })
})
