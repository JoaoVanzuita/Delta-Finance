import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type InternalMatch = components['schemas']['InternalMatch']
export type InternalMatchPage = components['schemas']['InternalMatchPage']
export type InternalMatchStatus = InternalMatch['status']

export const reconciliationApi = {
  internalMatches: (status: InternalMatchStatus = 'PENDING') =>
    api.get<InternalMatchPage>(`/internal-matches?status=${status}&limit=50`),
  confirmMatch: (id: string) => api.post<InternalMatch>(`/internal-matches/${id}/confirm`),
  rejectMatch: (id: string) => api.post<InternalMatch>(`/internal-matches/${id}/reject`),
}
