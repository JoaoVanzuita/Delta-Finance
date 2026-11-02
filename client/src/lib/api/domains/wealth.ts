import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type WealthOverview = components['schemas']['WealthOverview']
export type WealthSnapshotSummary = components['schemas']['WealthSnapshotSummary']
export type WealthSnapshot = components['schemas']['WealthSnapshot']
export type WealthSnapshotItem = components['schemas']['WealthSnapshotItem']
export type WealthSnapshotCaptureInput = components['schemas']['WealthSnapshotCaptureRequest']
export type WealthItem = components['schemas']['WealthItem']
export type WealthManualItem = components['schemas']['WealthManualItem']
export type WealthManualItemList = components['schemas']['WealthManualItemList']
export type WealthManualItemInput = components['schemas']['WealthManualItemRequest']

export const wealthApi = {
  wealthOverview: () => api.get<WealthOverview>('/wealth/overview'),
  wealthHistory: (from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return api.get<WealthSnapshotSummary[]>(`/wealth/history${query ? `?${query}` : ''}`)
  },
  wealthSnapshot: (id: string) => api.get<WealthSnapshot>(`/wealth/history/${id}`),
  createWealthSnapshot: (input: WealthSnapshotCaptureInput = {}) =>
    api.post<WealthSnapshot>('/wealth/history', input),
  wealthManualItems: () => api.get<WealthManualItemList>('/wealth/manual-items'),
  createWealthManualItem: (input: WealthManualItemInput) =>
    api.post<WealthManualItem>('/wealth/manual-items', input),
  updateWealthManualItem: (id: string, input: WealthManualItemInput) =>
    api.patch<WealthManualItem>(`/wealth/manual-items/${id}`, input),
  deleteWealthManualItem: (id: string) => api.delete<void>(`/wealth/manual-items/${id}`),
}
