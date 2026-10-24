// Login/logout, CRUD/listagens e sincronização (T198).
//
//   CONNECTION_ID=<uuid> docker run --rm --network host -i grafana/k6:2.3.0 run - \
//     < server/perf/auth-crud-sync.js
//
// Metas: login/logout p95 <= 1 s; CRUD/listagens p95 <= 500 ms; sincronização de
// uma conexão concluída em até 60 s. O usuário e os dados são sintéticos.

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Trend } from 'k6/metrics'
import {
  BASE_URL,
  JSON_HEADERS,
  USERNAME,
  PASSWORD,
  login,
  mutationHeaders,
  useSession,
} from './lib.js'

const syncDuration = new Trend('sync_completion', true)

const scenarios = {
  auth: {
    executor: 'constant-vus',
    exec: 'authFlow',
    vus: Number(__ENV.PERF_AUTH_VUS || 1),
    duration: __ENV.PERF_DURATION || '1m',
  },
  crud: {
    executor: 'constant-vus',
    exec: 'crudFlow',
    vus: Number(__ENV.PERF_CRUD_VUS || 2),
    duration: __ENV.PERF_DURATION || '1m',
    startTime: '2s',
  },
}

const thresholds = {
  'http_req_duration{name:login}': ['p(95)<1000'],
  'http_req_duration{name:logout}': ['p(95)<1000'],
  'http_req_duration{name:crud-create}': ['p(95)<500'],
  'http_req_duration{name:crud-update}': ['p(95)<500'],
  'http_req_duration{name:crud-delete}': ['p(95)<500'],
  'http_req_duration{name:crud-list}': ['p(95)<500'],
  http_req_failed: ['rate<0.01'],
}

if (__ENV.CONNECTION_ID) {
  scenarios.sync = {
    executor: 'per-vu-iterations',
    exec: 'syncFlow',
    vus: 1,
    iterations: Number(__ENV.PERF_SYNC_ITERATIONS || 3),
    startTime: '5s',
    maxDuration: '5m',
  }
  thresholds['sync_completion'] = ['p(95)<60000']
}

export const options = { scenarios, thresholds }

export function setup() {
  return login()
}

function ensureSession(data) {
  useSession(data)
}

export function authFlow(data) {
  ensureSession(data)
  const csrf = http.get(`${BASE_URL}/api/v1/auth/csrf`)
  const token = csrf.cookies['XSRF-TOKEN']?.[0]?.value
  const loginResponse = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { ...JSON_HEADERS, 'X-XSRF-TOKEN': token }, tags: { name: 'login' } },
  )
  check(loginResponse, { 'login 200': (r) => r.status === 200 })
  const ownSession = loginResponse.cookies['__Host-delta_session']?.[0]?.value
  if (ownSession) {
    useSession({ session: ownSession })
  }
  const logoutResponse = http.post(`${BASE_URL}/api/v1/auth/logout`, null, {
    headers: mutationHeaders(),
    tags: { name: 'logout' },
  })
  check(logoutResponse, { 'logout 204': (r) => r.status === 204 })
}

export function crudFlow(data) {
  ensureSession(data)
  const suffix = `${__VU}-${__ITER}-${Date.now()}`
  const accountBody = JSON.stringify({
    type: 'CASH',
    alias: `Carteira perf ${suffix}`,
    referenceBalance: '100.00',
    referenceDate: '2026-10-01T12:00:00Z',
  })
  const createdAccount = http.post(`${BASE_URL}/api/v1/accounts`, accountBody, {
    headers: mutationHeaders(),
    tags: { name: 'crud-create' },
  })
  check(createdAccount, { 'conta criada': (r) => r.status === 201 })
  const accountId = createdAccount.json('id')

  const updatedAccount = http.patch(
    `${BASE_URL}/api/v1/accounts/${accountId}`,
    JSON.stringify({ alias: `Carteira perf editada ${suffix}` }),
    { headers: mutationHeaders(), tags: { name: 'crud-update' } },
  )
  check(updatedAccount, { 'conta editada': (r) => r.status === 200 })

  const createdTransaction = http.post(
    `${BASE_URL}/api/v1/transactions`,
    JSON.stringify({
      accountId,
      type: 'DEBIT',
      postedOn: '2026-10-05',
      amount: '25.50',
      description: `Compra perf ${suffix}`,
    }),
    { headers: mutationHeaders(), tags: { name: 'crud-create' } },
  )
  check(createdTransaction, { 'lançamento criado': (r) => r.status === 201 })
  const transactionId = createdTransaction.json('id')

  const listed = http.get(`${BASE_URL}/api/v1/transactions?limit=50`, {
    headers: mutationHeaders(),
    tags: { name: 'crud-list' },
  })
  check(listed, { 'listagem 200': (r) => r.status === 200 })

  const deleted = http.del(`${BASE_URL}/api/v1/transactions/${transactionId}`, null, {
    headers: mutationHeaders(),
    tags: { name: 'crud-delete' },
  })
  check(deleted, { 'lançamento removido': (r) => r.status === 204 })
}

export function syncFlow(data) {
  ensureSession(data)
  const connectionId = __ENV.CONNECTION_ID
  if (!connectionId) {
    check(null, { 'CONNECTION_ID ausente': () => false })
    return
  }
  const started = Date.now()
  const accepted = http.post(
    `${BASE_URL}/api/v1/connections/${connectionId}/sync`,
    null,
    { headers: mutationHeaders(), tags: { name: 'sync-enqueue' } },
  )
  check(accepted, { 'sync aceito': (r) => r.status === 202 })

  let pending = Number.MAX_SAFE_INTEGER
  while (Date.now() - started < 60000) {
    const status = http.get(`${BASE_URL}/api/v1/sync/status`, {
      headers: mutationHeaders(),
      tags: { name: 'sync-status' },
    })
    if (status.status === 200) {
      pending = status.json('pendingCount') ?? 0
      if (pending === 0) {
        break
      }
    }
    // Espera curta entre consultas de status; o job dura segundos.
    sleep(1)
  }
  syncDuration.add(Date.now() - started)
  check(pending, { 'sync concluído': (value) => value === 0 })
}
