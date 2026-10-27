import { api } from '@/lib/api/client'
import type { components } from '@/lib/api/schema'

export type DashboardSummary = components['schemas']['DashboardSummary']
export type DashboardCategory = components['schemas']['DashboardCategory']
export type DashboardBucket = components['schemas']['DashboardBucket']
export type ReportEntry = components['schemas']['ReportEntry']
export type ReportEntryPage = components['schemas']['ReportEntryPage']

/** Categoria sintética devolvida pelo servidor para lançamentos sem categoria. */
export const UNCATEGORIZED_CATEGORY_ID = '00000000-0000-0000-0000-000000000000'

export interface DashboardFilters {
  readonly period?: 'WEEK' | 'MONTH' | 'YEAR'
  readonly from?: string
  readonly to?: string
  readonly accountIds?: readonly string[]
  readonly categoryId?: string
  readonly type?: string
  readonly origin?: string
  readonly minAmount?: string
  readonly maxAmount?: string
}

function queryString(filters: DashboardFilters, extra: Record<string, string> = {}): string {
  const params = new URLSearchParams()
  if (filters.period) params.set('period', filters.period)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  for (const accountId of filters.accountIds ?? []) params.append('accountId', accountId)
  if (filters.categoryId) params.set('categoryId', filters.categoryId)
  if (filters.type) params.set('type', filters.type)
  if (filters.origin) params.set('origin', filters.origin)
  if (filters.minAmount) params.set('minAmount', filters.minAmount)
  if (filters.maxAmount) params.set('maxAmount', filters.maxAmount)
  for (const [key, value] of Object.entries(extra)) {
    if (value) params.set(key, value)
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const dashboardApi = {
  summary: (filters: DashboardFilters) =>
    api.get<DashboardSummary>(`/dashboard/summary${queryString(filters)}`),
  categories: (filters: DashboardFilters) =>
    api.get<DashboardCategory[]>(`/dashboard/categories${queryString(filters)}`),
  timeseries: (filters: DashboardFilters) =>
    api.get<DashboardBucket[]>(`/dashboard/timeseries${queryString(filters)}`),
  entries: (filters: DashboardFilters, cursor?: string, limit = 25) =>
    api.get<ReportEntryPage>(
      `/reports/entries${queryString(filters, {
        cursor: cursor ?? '',
        limit: String(limit),
      })}`,
    ),
}
