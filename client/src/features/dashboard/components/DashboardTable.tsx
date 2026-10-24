import { formatMoney } from '@/features/accounts/format'
import type { ReportEntry } from '@/features/dashboard/api'
import { formatDate } from '@/features/dashboard/format'

interface DashboardTableProps {
  readonly title: string
  readonly entries: readonly ReportEntry[]
  readonly categoryNames: ReadonlyMap<string, string>
  readonly accountNames: ReadonlyMap<string, string>
  readonly isLoading: boolean
  readonly isFetchingNextPage: boolean
  readonly isError: boolean
  readonly hasMore: boolean
  readonly onLoadMore: () => void
  readonly onClose: () => void
}

const sourceLabels: Record<string, string> = {
  TRANSACTION: 'Transação',
  INVOICE_ITEM: 'Item de fatura',
}

/**
 * Drill-down responsivo.
 *
 * Em telas largas usa tabela; em telas estreitas a mesma informação vira cards.
 * O botão de carregar mais preserva o cursor opaco e o foco permanece visível.
 */
export function DashboardTable({
  title,
  entries,
  categoryNames,
  accountNames,
  isLoading,
  isFetchingNextPage,
  isError,
  hasMore,
  onLoadMore,
  onClose,
}: DashboardTableProps) {
  return (
    <section
      aria-labelledby="drill-down-heading"
      className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="drill-down-heading" className="text-lg font-semibold text-ink">
            Detalhamento
          </h2>
          <p className="text-sm text-ink-muted">{title}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-3 text-sm font-medium text-ink transition-colors hover:bg-surface-muted"
        >
          Fechar detalhamento
        </button>
      </div>

      {isLoading ? (
        <p role="status" aria-live="polite" className="text-sm text-ink-muted">
          Carregando entradas…
        </p>
      ) : null}

      {isError ? (
        <p role="alert" className="text-sm text-expense">
          Não foi possível carregar o detalhamento. Tente novamente.
        </p>
      ) : null}

      {!isLoading && !isError && entries.length === 0 ? (
        <p className="rounded-card border border-line bg-surface-muted p-4 text-sm text-ink-muted">
          Nenhuma entrada compõe este total no período e com os filtros atuais.
        </p>
      ) : null}

      {entries.length > 0 ? (
        <>
          <div className="hidden overflow-x-auto sm:block">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Entradas que compõem o total selecionado</caption>
              <thead className="border-b border-line bg-surface-muted text-xs uppercase">
                <tr>
                  <th scope="col" className="px-3 py-2">
                    Data
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Descrição
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Origem
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Categoria
                  </th>
                  <th scope="col" className="px-3 py-2">
                    Conta
                  </th>
                  <th scope="col" className="px-3 py-2 text-right">
                    Valor
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr
                    key={`${entry.source}-${entry.id}`}
                    className="border-b border-line last:border-0"
                  >
                    <td className="px-3 py-2 text-ink-muted">{formatDate(entry.occurredOn)}</td>
                    <td className="px-3 py-2">
                      <span className="block font-medium text-ink">{entry.description || '—'}</span>
                      <span className="block text-xs text-ink-muted">
                        {sourceLabels[entry.source] ?? entry.source}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-ink">
                      {entry.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                    </td>
                    <td className="px-3 py-2 text-ink">
                      {entry.categoryId
                        ? (categoryNames.get(entry.categoryId) ?? 'Sem categoria')
                        : 'Sem categoria'}
                    </td>
                    <td className="px-3 py-2 text-ink">
                      {entry.accountId ? (accountNames.get(entry.accountId) ?? 'Conta') : '—'}
                    </td>
                    <td
                      className={[
                        'numeric px-3 py-2 text-right font-semibold',
                        Number(entry.amount) < 0 ? 'text-expense' : 'text-income',
                      ].join(' ')}
                    >
                      {formatMoney(entry.amount, 'BRL')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <ul className="space-y-3 sm:hidden">
            {entries.map((entry) => (
              <li
                key={`${entry.source}-${entry.id}`}
                className="rounded-card border border-line p-3"
              >
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-sm font-medium text-ink">
                    {entry.description || 'Sem descrição'}
                  </span>
                  <span
                    className={[
                      'numeric text-sm font-semibold',
                      Number(entry.amount) < 0 ? 'text-expense' : 'text-income',
                    ].join(' ')}
                  >
                    {formatMoney(entry.amount, 'BRL')}
                  </span>
                </div>
                <p className="mt-1 text-xs text-ink-muted">
                  {formatDate(entry.occurredOn)} · {sourceLabels[entry.source] ?? entry.source} ·{' '}
                  {entry.origin === 'IMPORTED' ? 'Importada' : 'Manual'}
                </p>
                <p className="mt-1 text-xs text-ink-muted">
                  {entry.categoryId
                    ? (categoryNames.get(entry.categoryId) ?? 'Sem categoria')
                    : 'Sem categoria'}
                  {entry.accountId ? ` · ${accountNames.get(entry.accountId) ?? 'Conta'}` : ''}
                </p>
              </li>
            ))}
          </ul>

          <p role="status" className="text-xs text-ink-muted">
            {entries.length} entrada(s) carregada(s)
          </p>

          {hasMore ? (
            <button
              type="button"
              onClick={onLoadMore}
              disabled={isFetchingNextPage}
              className="inline-flex min-h-11 items-center justify-center rounded-card border border-line bg-surface px-4 text-sm font-semibold text-ink transition-colors hover:bg-surface-muted disabled:opacity-60"
            >
              {isFetchingNextPage ? 'Carregando…' : 'Carregar mais'}
            </button>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
