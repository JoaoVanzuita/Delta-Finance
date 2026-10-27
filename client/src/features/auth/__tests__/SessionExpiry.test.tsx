import type { ReactElement } from 'react'

import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  resetSessionClockForTesting,
  SESSION_QUERY_KEY,
  useLogin,
  useSession,
  useSessionExpiryListener,
} from '@/features/auth/hooks/useSession'
import { markSessionCleared, UNAUTHENTICATED_EVENT } from '@/lib/api/client'

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const FINANCIAL_KEY = ['accounts']

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function problemResponse(status: number, code: string): Response {
  return new Response(
    JSON.stringify({
      type: `urn:delta-finance:error:${code.toLowerCase()}`,
      title: 'Unauthorized',
      status,
      code,
      detail: 'Autenticação necessária.',
    }),
    { status, headers: { 'content-type': 'application/problem+json' } },
  )
}

function createClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Number.POSITIVE_INFINITY, staleTime: 0 },
      mutations: { retry: false },
    },
  })
}

function ListenerOnly() {
  useSessionExpiryListener()
  return <p>pronto</p>
}

function SessionAndListener() {
  useSessionExpiryListener()
  const { data } = useSession()
  return <p>{data ? data.username : 'anônimo'}</p>
}

function LoginHarness() {
  const login = useLogin()
  return (
    <button type="button" onClick={() => login.mutate({ username: 'alice', password: 'senha' })}>
      Entrar
    </button>
  )
}

function renderWithClient(ui: ReactElement, client: QueryClient) {
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('expiração de sessão no cliente', () => {
  beforeEach(() => {
    markSessionCleared()
    resetSessionClockForTesting()
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('expira após 30 minutos sem atividade e limpa o cache financeiro', () => {
    vi.useFakeTimers()
    const client = createClient()
    client.setQueryData(SESSION_QUERY_KEY, USER)
    client.setQueryData(FINANCIAL_KEY, { balance: '100.00' })
    renderWithClient(<ListenerOnly />, client)

    act(() => {
      vi.advanceTimersByTime(30 * 60 * 1000 + 15 * 1000)
    })

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
    expect(client.getQueryData(FINANCIAL_KEY)).toBeUndefined()
  })

  it('expira após 12 horas mesmo com atividade constante', () => {
    vi.useFakeTimers()
    const client = createClient()
    client.setQueryData(SESSION_QUERY_KEY, USER)
    renderWithClient(<ListenerOnly />, client)

    for (let index = 0; index < 36; index++) {
      act(() => {
        window.dispatchEvent(new Event('pointerdown'))
        vi.advanceTimersByTime(20 * 60 * 1000)
      })
    }
    act(() => {
      vi.advanceTimersByTime(15 * 1000)
    })

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
  })

  it('limpa o cache financeiro ao receber 401 de sessão conhecida', () => {
    const client = createClient()
    client.setQueryData(SESSION_QUERY_KEY, USER)
    client.setQueryData(FINANCIAL_KEY, { balance: '100.00' })
    renderWithClient(<ListenerOnly />, client)

    act(() => {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT))
    })

    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
    expect(client.getQueryData(FINANCIAL_KEY)).toBeUndefined()
  })

  it('revalida ao voltar o foco e limpa o cache se o servidor responder 401', async () => {
    let calls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url.endsWith('/auth/me')) {
          calls += 1
          return calls === 1 ? jsonResponse(USER) : problemResponse(401, 'UNAUTHENTICATED')
        }
        return problemResponse(404, 'NOT_FOUND')
      }),
    )
    const client = createClient()
    renderWithClient(<SessionAndListener />, client)

    expect(await screen.findByText(USER.username)).toBeVisible()
    client.setQueryData(FINANCIAL_KEY, { balance: '100.00' })

    act(() => {
      window.dispatchEvent(new Event('focus'))
    })

    await waitFor(() => expect(calls).toBeGreaterThanOrEqual(2))
    await waitFor(() => expect(client.getQueryData(FINANCIAL_KEY)).toBeUndefined())
    expect(client.getQueryData(SESSION_QUERY_KEY)).toBeNull()
  })

  it('limpa o cache antigo ao entrar com outro usuário', async () => {
    const client = createClient()
    client.setQueryData(SESSION_QUERY_KEY, { ...USER, username: 'bob.antigo' })
    client.setQueryData(FINANCIAL_KEY, { balance: '999.00' })
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(USER)),
    )

    const user = userEvent.setup()
    renderWithClient(<LoginHarness />, client)
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() =>
      expect(client.getQueryData<{ username: string }>(SESSION_QUERY_KEY)?.username).toBe(
        USER.username,
      ),
    )
    expect(client.getQueryData(FINANCIAL_KEY)).toBeUndefined()
  })
})
