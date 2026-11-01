import type { ReportEntry } from './api'

/** Destino de detalhamento de uma entrada do reporting. */
export function entryHref(entry: ReportEntry): string {
  if (entry.invoiceId) {
    return `/invoices/${entry.invoiceId}`
  }
  const params = new URLSearchParams()
  if (entry.accountId) params.set('accountId', entry.accountId)
  return params.size > 0 ? `/transactions?${params.toString()}` : '/transactions'
}
