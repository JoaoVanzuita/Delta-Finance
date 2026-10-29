import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  api,
  markSessionCleared,
  markSessionEstablished,
  UNAUTHENTICATED_EVENT,
} from '@/lib/api/client'
import { ApiError } from '@/lib/api/problem'

const CSRF_COOKIE = 'XSRF-TOKEN=token-123; path=/'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('api client', () => {
  beforeEach(() => {
    markSessionCleared()
    document.cookie = CSRF_COOKIE
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('envia cookie de sessão e header CSRF em mutações', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await api.post('/test', { value: 1 })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/test')
    expect(init.credentials).toBe('include')
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('token-123')
    expect(init.body).toBe(JSON.stringify({ value: 1 }))
  })

  it('envia Idempotency-Key nas escritas financeiras críticas', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })))
    vi.stubGlobal('fetch', fetchMock)

    await api.post('/transactions', {})
    await api.patch('/transactions/tx-1', {})
    await api.delete('/transactions/tx-1')
    await api.post('/invoices/inv-1/items', {})
    await api.patch('/invoice-items/item-1', {})
    await api.delete('/invoice-items/item-1')
    await api.post('/invoices/inv-1/payments', {})

    for (const [url, init] of fetchMock.mock.calls as Array<[string, RequestInit]>) {
      expect(url).toMatch(/^\/api\/v1\//)
      expect(new Headers(init.headers).get('Idempotency-Key')).toBeTruthy()
    }
  })

  it('não envia Idempotency-Key nas demais mutações', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })))
    vi.stubGlobal('fetch', fetchMock)

    await api.post('/accounts', {})
    await api.post('/connections', {})
    await api.patch('/goals/goal-1', {})
    await api.delete('/investments/income/income-1')

    for (const [, init] of fetchMock.mock.calls as Array<[string, RequestInit]>) {
      expect(new Headers(init.headers).get('Idempotency-Key')).toBeNull()
    }
  })

  it('traduz ProblemDetail em ApiError com erros por campo', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          type: 'urn:delta-finance:error:validation_failed',
          title: 'Unprocessable Entity',
          status: 422,
          code: 'VALIDATION_FAILED',
          detail: 'Um ou mais campos são inválidos.',
          requestId: 'req-1',
          errors: [{ field: 'password', message: 'Escolha uma senha menos comum.' }],
        },
        422,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await api.post('/test', {}).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      status: 422,
      code: 'VALIDATION_FAILED',
      requestId: 'req-1',
      fieldErrors: [{ field: 'password', message: 'Escolha uma senha menos comum.' }],
    })
    expect((error as ApiError).fieldMessage('password')).toContain('menos comum')
  })

  it('emite evento de sessão expirada em 401', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          type: 'urn:delta-finance:error:unauthenticated',
          title: 'Unauthorized',
          status: 401,
          code: 'UNAUTHENTICATED',
          detail: 'Autenticação necessária.',
        },
        401,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    markSessionEstablished()
    const listener = vi.fn()
    window.addEventListener(UNAUTHENTICATED_EVENT, listener)

    await expect(api.get('/me')).rejects.toBeInstanceOf(ApiError)

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener(UNAUTHENTICATED_EVENT, listener)
  })

  it('retorna undefined em 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.post('/logout')).resolves.toBeUndefined()
  })
})
