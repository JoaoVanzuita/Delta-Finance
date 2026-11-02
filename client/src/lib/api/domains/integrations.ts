import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Integration = components['schemas']['Integration']
export type ValidationResult = components['schemas']['ValidationResult']
export type Connection = components['schemas']['Connection']
export type ConnectionPage = components['schemas']['ConnectionPage']
export type SyncStatus = components['schemas']['SyncStatus']
export type SyncRun = components['schemas']['SyncRun']
export type SyncRunPage = components['schemas']['SyncRunPage']
export type SyncAccepted = components['schemas']['SyncAccepted']

export interface CredentialsInput {
  readonly clientId: string
  readonly clientSecret: string
}

export const integrationsApi = {
  integration: () => api.get<Integration>('/integrations/pluggy'),
  saveCredentials: (input: CredentialsInput) => api.put<Integration>('/integrations/pluggy', input),
  validateCredentials: (input: CredentialsInput) =>
    api.post<ValidationResult>('/integrations/pluggy/validate', input),
  disconnect: (deleteImportedData = false) =>
    api.delete<void>(`/integrations/pluggy?deleteImportedData=${deleteImportedData}`),
}

export const connectionsApi = {
  connections: () => api.get<ConnectionPage>('/connections'),
  addConnection: (itemId: string) => api.post<Connection>('/connections', { itemId }),
  updateConnection: (id: string, itemId: string) =>
    api.patch<Connection>(`/connections/${id}`, { itemId }),
  renameConnection: (id: string, displayName: string) =>
    api.put<Connection>(`/connections/${id}/display-name`, { displayName }),
  resetConnectionName: (id: string) => api.delete<Connection>(`/connections/${id}/display-name`),
  removeConnection: (id: string, deleteHistory = false) =>
    api.delete<void>(`/connections/${id}?deleteHistory=${deleteHistory}`),
  syncConnection: (id: string) => api.post<SyncAccepted>(`/connections/${id}/sync`),
  updateConnectionReservedBalanceTreatment: (
    id: string,
    treatment: components['schemas']['Account']['reservedBalanceTreatment'],
  ) => api.put<Connection>(`/connections/${id}/reserved-balance-treatment`, { treatment }),
}

export const syncApi = {
  syncStatus: () => api.get<SyncStatus>('/sync/status'),
  syncRuns: (cursor?: string, limit = 20) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return api.get<SyncRunPage>(`/sync/runs?${params.toString()}`)
  },
}
