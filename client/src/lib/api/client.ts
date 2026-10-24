import type { components } from './schema'

import { ApiError, isProblemDetails, parseRetryAfter, type ProblemDetails } from './problem'

/**
 * Cliente HTTP único da SPA.
 *
 * Regras:
 * - sessão exclusivamente por cookie HttpOnly (`credentials: 'include'`);
 * - CSRF por cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` em métodos mutáveis;
 * - nenhum segredo, token ou dado financeiro em `localStorage`;
 * - erros sempre como `ApiError` com `code`, `requestId` e erros por campo;
 * - 401 dispara `delta:unauthenticated` para que a camada de rotas reaja.
 */

const API_BASE = '/api/v1'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const UNAUTHENTICATED_EVENT = 'delta:unauthenticated'

export type ApiSchemas = components['schemas']
export type User = ApiSchemas['User']

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

interface RequestOptions {
  readonly method?: HttpMethod
  readonly body?: unknown
  readonly headers?: HeadersInit
  readonly signal?: AbortSignal
}

let csrfBootstrap: Promise<void> | null = null
let sessionEstablished = false

/**
 * Marca que existe uma sessão conhecida. O evento de sessão expirada só é
 * emitido nesse caso: a sondagem inicial de `/auth/me` para visitante anônimo
 * não deve exibir "sessão expirada".
 */
export function markSessionEstablished(): void {
  sessionEstablished = true
}

export function markSessionCleared(): void {
  sessionEstablished = false
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null
  }
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const entry = part.trim()
    if (entry.startsWith(prefix)) {
      return decodeURIComponent(entry.slice(prefix.length))
    }
  }
  return null
}

async function fetchCsrfToken(): Promise<void> {
  const response = await fetch(`${API_BASE}/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok && response.status !== 204) {
    throw new ApiError(
      response.status,
      'CSRF_BOOTSTRAP_FAILED',
      'Não foi possível preparar a proteção CSRF.',
    )
  }
}

/** Garante que o cookie CSRF exista antes de uma mutação. */
export async function ensureCsrfToken(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) {
    return
  }
  if (!csrfBootstrap) {
    csrfBootstrap = fetchCsrfToken().finally(() => {
      csrfBootstrap = null
    })
  }
  await csrfBootstrap
}

/** Renova o cookie CSRF (após logout ou token inválido). */
export async function refreshCsrfToken(): Promise<void> {
  csrfBootstrap = fetchCsrfToken().finally(() => {
    csrfBootstrap = null
  })
  await csrfBootstrap
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfterSeconds = parseRetryAfter(response.headers.get('Retry-After'))
  const contentType = response.headers.get('content-type') ?? ''
  let problem: ProblemDetails | undefined

  if (contentType.includes('json') || contentType.includes('problem')) {
    try {
      const parsed: unknown = await response.json()
      if (isProblemDetails(parsed)) {
        problem = parsed
      }
    } catch {
      problem = undefined
    }
  }

  return new ApiError(
    response.status,
    problem?.code ?? `HTTP_${response.status}`,
    problem?.detail ?? problem?.title ?? 'Não foi possível concluir a operação.',
    {
      requestId: problem?.requestId ?? response.headers.get('X-Request-Id') ?? undefined,
      fieldErrors: problem?.errors,
      retryAfterSeconds,
      problem,
    },
  )
}

async function send<T>(path: string, options: RequestOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const unsafe = method !== 'GET'
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (unsafe) {
    const token = readCookie(CSRF_COOKIE)
    if (token) {
      headers.set(CSRF_HEADER, token)
    }
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      credentials: 'include',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    throw ApiError.network(cause)
  }

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.status === 401 && sessionEstablished && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT))
    }
    throw error
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('json')) {
    return (await response.text()) as unknown as T
  }
  return (await response.json()) as T
}

/** Baixa um recurso binário (ex.: ZIP de exportação) com tratamento de erro da API. */
async function download(path: string): Promise<Blob> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/zip' },
    })
  } catch (cause) {
    throw ApiError.network(cause)
  }

  if (!response.ok) {
    const error = await toApiError(response)
    if (error.status === 401 && sessionEstablished && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT))
    }
    throw error
  }
  return response.blob()
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const unsafe = method !== 'GET'

  if (unsafe) {
    await ensureCsrfToken()
  }

  try {
    return await send<T>(path, options)
  } catch (error) {
    // Token CSRF expirado/rotacionado: renova uma vez e repete a mutação.
    if (unsafe && error instanceof ApiError && error.code === 'CSRF_INVALID') {
      await refreshCsrfToken()
      return send<T>(path, options)
    }
    throw error
  }
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => apiRequest<T>(path, { method: 'GET', signal }),
  post: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => apiRequest<T>(path, { method: 'DELETE' }),
  download: (path: string) => download(path),
}

export { UNAUTHENTICATED_EVENT }
