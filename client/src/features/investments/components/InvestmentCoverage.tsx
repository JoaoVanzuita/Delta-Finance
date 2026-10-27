import { formatDateTime } from '@/features/accounts/format'
import type { Connection, InvestmentSource } from '@/features/integration/api'

import { SCOPE_HINTS, SCOPE_LABELS } from '../labels'

interface InvestmentCoverageProps {
  readonly sources: readonly InvestmentSource[]
  readonly connections: readonly Connection[]
}

/** Cobertura de investimentos por conexão, sem confundir falta de compartilhamento com erro. */
export function InvestmentCoverage({ sources, connections }: InvestmentCoverageProps) {
  const names = new Map(connections.map((connection) => [connection.id, connection]))
  const available = sources.filter((source) => source.scope === 'AVAILABLE').length

  return (
    <div className="space-y-3">
      <p className="text-sm text-ink-muted">
        {available} de {sources.length} conexão(ões) compartilha(m) investimentos. A cobertura vem
        dos recursos declarados pela instituição; `products` sozinho não é prova.
      </p>
      {sources.length === 0 ? (
        <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
          Nenhuma conexão verificada ainda. Sincronize uma conexão para consultar a cobertura.
        </p>
      ) : (
        <ul className="space-y-2">
          {sources.map((source) => {
            const connection = names.get(source.connectionId)
            const label = connection?.institutionName ?? 'Conexão sem nome'
            return (
              <li
                key={source.connectionId}
                className="rounded-card border border-line bg-surface p-3"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink">{label}</p>
                    <p className="mt-1 text-xs text-ink-muted">
                      {SCOPE_HINTS[source.scope] ?? 'Cobertura não informada.'}
                    </p>
                  </div>
                  <span
                    className={
                      source.scope === 'AVAILABLE'
                        ? 'rounded-card border border-income/30 bg-income/5 px-2 py-1 text-xs font-medium text-income'
                        : source.scope === 'PENDING_AUTHORIZATION'
                          ? 'rounded-card border border-warning/40 bg-warning/5 px-2 py-1 text-xs font-medium text-warning'
                          : 'rounded-card border border-line bg-surface-muted px-2 py-1 text-xs font-medium text-ink-muted'
                    }
                  >
                    {SCOPE_LABELS[source.scope] ?? source.scope}
                  </span>
                </div>
                <p className="mt-2 text-xs text-ink-muted">
                  {source.resourceCount} recurso(s) de investimento ·{' '}
                  {source.availableResourceCount} disponível(is) · {source.pendingResourceCount}{' '}
                  pendente(s) · última leitura {formatDateTime(source.lastSyncedAt)}
                </p>
                {source.errorCode ? (
                  <p className="mt-1 text-xs text-warning">Código: {source.errorCode}</p>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
