import { accountsApi } from '@/lib/api/finance'
import { useQuery } from '@tanstack/react-query'

import { Badge } from '@/components/ui/badge'
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table'
import { formatMoney, formatMonthKeyShort } from '@/lib/format'
import { queryKeys } from '@/lib/query-keys'

function monthLabel(value: string): string {
  return formatMonthKeyShort(value)
}

/** Evolução mensal da reserva, derivada dos snapshots patrimoniais. */
export function ReservedBalanceHistory({
  reservedBalanceId,
}: {
  readonly reservedBalanceId: string
}) {
  const historyQuery = useQuery({
    queryKey: queryKeys.reservedBalanceHistory(reservedBalanceId),
    queryFn: () => accountsApi.reservedBalanceHistory(reservedBalanceId),
  })
  const points = historyQuery.data?.items ?? []

  return (
    <details className="rounded-control border border-line bg-surface-muted">
      <summary className="min-h-11 cursor-pointer px-3 py-2.5 text-body-sm font-medium text-ink">
        Evolução mensal da reserva
      </summary>
      <div className="border-t border-line p-3">
        {historyQuery.isLoading ? (
          <p role="status" className="text-caption text-ink-muted">
            Carregando histórico…
          </p>
        ) : null}
        {historyQuery.isError ? (
          <p role="alert" className="text-caption text-expense">
            Não foi possível carregar o histórico da reserva.
          </p>
        ) : null}
        {!historyQuery.isLoading && !historyQuery.isError && points.length === 0 ? (
          <p className="text-caption leading-relaxed text-ink-muted">
            O histórico mensal começa no primeiro snapshot patrimonial que incluir esta reserva.
          </p>
        ) : null}
        {points.length > 0 ? (
          <Table caption="Evolução mensal do saldo reservado">
            <THead>
              <TR>
                <TH>Mês</TH>
                <TH className="text-right">Valor</TH>
                <TH>Situação</TH>
              </TR>
            </THead>
            <TBody>
              {points.map((point) => (
                <TR key={point.referenceDate}>
                  <TH scope="row" className="text-left font-medium text-ink">
                    {monthLabel(point.referenceDate)}
                  </TH>
                  <TD className="numeric text-right text-ink">
                    {formatMoney(point.value, point.currency || 'BRL')}
                  </TD>
                  <TD>
                    {point.included ? (
                      <Badge variant="income">No patrimônio</Badge>
                    ) : (
                      <Badge variant="warning">Fora do patrimônio</Badge>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        ) : null}
      </div>
    </details>
  )
}
