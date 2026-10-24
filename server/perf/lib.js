// Utilitários compartilhados dos cenários k6 (T174/T198).
//
// A autenticação usa a mesma sessão por cookie e CSRF do navegador. Nenhum
// segredo é impresso nos resultados; as credenciais vêm de variáveis de
// ambiente do operador.

import http from 'k6/http'
import { check } from 'k6'

export const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
export const USERNAME = __ENV.PERF_USER || 'perf.dashboard'
export const PASSWORD = __ENV.PERF_PASSWORD || 'montanha verde silencio longo'
export const JSON_HEADERS = { 'Content-Type': 'application/json' }
let ACTIVE_SESSION = ''

function sessionHeaders(extra = {}) {
  return ACTIVE_SESSION
    ? { ...extra, Cookie: `__Host-delta_session=${ACTIVE_SESSION}` }
    : { ...extra }
}

function csrfToken(response) {
  const cookies = response.cookies['XSRF-TOKEN']
  return cookies && cookies.length > 0 ? cookies[0].value : null
}

/** Registra (se necessário) e autentica o usuário de performance. */
export function login() {
  const csrf = http.get(`${BASE_URL}/api/v1/auth/csrf`)
  const token = csrfToken(csrf)
  check(csrf, { 'csrf emitido': (r) => r.status === 204 && Boolean(token) })

  const register = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { ...JSON_HEADERS, 'X-XSRF-TOKEN': token } },
  )
  check(register, { 'registro idempotente': (r) => r.status === 201 || r.status === 409 })

  const login = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { ...JSON_HEADERS, 'X-XSRF-TOKEN': token } },
  )
  const session = login.cookies['__Host-delta_session']
  check(login, { 'login ok': (r) => r.status === 200 && Boolean(session) })
  return { session: session?.[0]?.value ?? null, csrf: token }
}

/** Copia a sessão do setup para o cookie jar da VU (cada VU tem jar próprio). */
export function useSession(data) {
  if (data && data.session) {
    ACTIVE_SESSION = data.session
  }
}

/** Cabeçalhos de mutação com o token CSRF atual do cookie jar. */
export function mutationHeaders() {
  const response = http.get(`${BASE_URL}/api/v1/auth/csrf`, { headers: sessionHeaders() })
  const token = csrfToken(response)
  return {
    ...JSON_HEADERS,
    'X-XSRF-TOKEN': token,
    Cookie: ACTIVE_SESSION
      ? `__Host-delta_session=${ACTIVE_SESSION}; XSRF-TOKEN=${token}`
      : `XSRF-TOKEN=${token}`,
  }
}

export function authGet(path, name, tags = {}) {
  const response = http.get(`${BASE_URL}${path}`, {
    headers: sessionHeaders(),
    tags: { name, ...tags },
  })
  check(response, { [`${name} 200`]: (r) => r.status === 200 })
  return response
}
