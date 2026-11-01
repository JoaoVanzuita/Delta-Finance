import { Link } from 'react-router'

import { Badge } from '@/components/ui/badge'
import { formatDateTime } from '@/lib/format'

import type { InvestmentSource } from '@/features/integration/api'

import { SCOPE_HINTS, SCOPE_LABELS } from '../labels'

function scopeVariant(scope: InvestmentSource['scope']) {
  if (scope === 'AVAILABLE') return 'income' as const
  if (scope === 'NOT_SHARED' || scope === 'PENDING_AUTHORIZATION') return 'warning' as const
  if (scope === 'ERROR') return 'expense' as const
  return 'neutral' as const
}

/**
 * Cobertura da importação por conexão. Ausência de posição não é saldo zero e
 * cada estado tem uma próxima ação explícita.
 */
export function InvestmentCoverage({ sources }: { readonly sources: readonly InvestmentSource[] }) {
  return (
    <section aria-labelledby="investment-coverage-heading" className="space-y-3">
      <div>
        <h2 id="investment-coverage-heading" className="text-section-title font-semibold text-ink">
          Cobertura da importação
        </h2>
        <p className="text-caption text-ink-muted">
          O que cada conexão compartilha com o provedor. Ausência de posição não significa saldo
          zero.
        </p>
      </div>

      {sources.length === 0 ? (
        <div className="rounded-card border border-warning/40 bg-warning-soft p-4">
          <p className="text-body-sm font-semibold text-ink">
            Cobertura de investimentos não informada
          </p>
          <p className="mt-1 text-caption text-ink-muted">
            Nenhuma conexão retornou informação de recursos de investimento. Verifique a integração
            antes de interpretar a ausência de posições como zero.
          </p>
          <Link
            to="/settings/integration"
            className="mt-3 inline-flex min-h-11 items-center text-body-sm font-semibold text-action underline underline-offset-4 hover:text-action-hover"
          >
            Revisar integração
          </Link>
        </div>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {sources.map((source, index) => {
            const hint = SCOPE_HINTS[source.scope] ?? 'Estado de cobertura não reconhecido.'
            return (
              <li
                key={source.connectionId}
                className="rounded-card border border-line bg-surface p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-body-sm font-semibold text-ink">Conexão {index + 1}</p>
                    <p className="mt-1 text-caption text-ink-muted">{hint}</p>
                  </div>
                  <Badge variant={scopeVariant(source.scope)}>
                    {SCOPE_LABELS[source.scope] ?? 'Cobertura desconhecida'}
                  </Badge>
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-caption sm:grid-cols-4">
                  <div>
                    <dt className="text-ink-muted">Recursos</dt>
                    <dd className="numeric font-semibold text-ink">{source.resourceCount}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Disponíveis</dt>
                    <dd className="numeric font-semibold text-ink">
                      {source.availableResourceCount}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Pendentes</dt>
                    <dd className="numeric font-semibold text-ink">
                      {source.pendingResourceCount}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-muted">Indisponíveis</dt>
                    <dd className="numeric font-semibold text-ink">
                      {source.unavailableResourceCount}
                    </dd>
                  </div>
                </dl>
                <p className="mt-2 text-caption text-ink-muted">
                  Última leitura: {formatDateTime(source.lastSyncedAt)}
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
