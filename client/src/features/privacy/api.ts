import { api } from '@/lib/api/client'

export const privacyApi = {
  exportData: () => api.download('/me/export'),
  deleteAccount: (password: string, confirmation: string) =>
    api.post<void>('/me/delete', { password, confirmation }),
}
