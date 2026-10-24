// Dashboard e filtros (T174).
//
// Executa o cenário de referência (5 usuários, 10.000 transações por usuário)
// contra uma instância já semeada por deploy/scripts/perf-seed.sh.
//
//   docker run --rm --network host -i grafana/k6:2.3.0 run - \
//     < server/perf/dashboard.js
//
// Variáveis: BASE_URL, PERF_USER, PERF_PASSWORD, PERF_VUS (5), PERF_DURATION (1m).
// Metas: dashboard e drill-down com p95 <= 2 s, incluindo viradas de semana,
// mês e ano no fuso America/Sao_Paulo.

import { check } from 'k6'
import { authGet, login, useSession } from './lib.js'

export const options = {
  scenarios: {
    dashboard: {
      executor: 'constant-vus',
      vus: Number(__ENV.PERF_VUS || 5),
      duration: __ENV.PERF_DURATION || '1m',
    },
  },
  thresholds: {
    'http_req_duration{name:dashboard}': ['p(95)<2000'],
    'http_req_duration{name:dashboard-filtros}': ['p(95)<2000'],
    'http_req_duration{name:categorias}': ['p(95)<2000'],
    'http_req_duration{name:timeseries}': ['p(95)<2000'],
    'http_req_duration{name:drilldown}': ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
}

// Períodos que cruzam domingo/sábado, virada de mês e virada de ano.
const PERIODOS = [
  ['2026-10-19', '2026-10-25'],
  ['2026-09-28', '2026-10-04'],
  ['2026-12-28', '2027-01-03'],
  ['2026-10-01', '2026-10-31'],
  ['2026-01-01', '2026-12-31'],
]

let sessionReady = false

export function setup() {
  return login()
}

export default function (data) {
  if (!sessionReady) {
    useSession(data)
    sessionReady = true
  }
  const [from, to] = PERIODOS[Math.floor(Math.random() * PERIODOS.length)]
  const periodo = `from=${from}&to=${to}`

  authGet(`/api/v1/dashboard/summary?${periodo}`, 'dashboard')
  authGet(
    `/api/v1/dashboard/summary?${periodo}&type=DEBIT&origin=MANUAL&minAmount=10&maxAmount=5000`,
    'dashboard-filtros',
  )
  authGet(`/api/v1/dashboard/categories?${periodo}`, 'categorias')
  authGet(`/api/v1/dashboard/timeseries?${periodo}&period=MONTH`, 'timeseries')
  const entries = authGet(`/api/v1/reports/entries?${periodo}&limit=50`, 'drilldown')
  check(entries, { 'drilldown paginado': (r) => r.status === 200 })
}
