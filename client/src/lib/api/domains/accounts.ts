import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Account = components['schemas']['Account']
export type AccountList = components['schemas']['AccountList']
export type ReservedBalance = components['schemas']['ReservedBalance']
export type ReservedBalanceAmount = components['schemas']['ReservedBalanceAmount']
export type ReservedBalanceList = components['schemas']['ReservedBalanceList']
export type ReservedBalanceHistory = components['schemas']['ReservedBalanceHistory']
export type ReservedBalanceHistoryPoint = ReservedBalanceHistory['items'][number]
export type ReservedBalanceTreatment = NonNullable<Account['reservedBalanceTreatment']>
export type ManualAccountInput = components['schemas']['ManualAccountRequest']
export type AccountUpdateInput = components['schemas']['AccountUpdateRequest']

export const accountsApi = {
  accounts: () => api.get<AccountList>('/accounts'),
  account: (id: string) => api.get<Account>(`/accounts/${id}`),
  createAccount: (input: ManualAccountInput) => api.post<Account>('/accounts', input),
  updateAccount: (id: string, input: AccountUpdateInput) =>
    api.patch<Account>(`/accounts/${id}`, input),
  renameAccount: (id: string, displayName: string) =>
    api.put<Account>(`/accounts/${id}/display-name`, { displayName }),
  resetAccountName: (id: string) => api.delete<Account>(`/accounts/${id}/display-name`),
  updateAccountInvestment: (id: string, investmentAccount: boolean) =>
    api.put<Account>(`/accounts/${id}/investment`, { investmentAccount }),
  reservedBalances: (accountId: string) =>
    api.get<ReservedBalanceList>(`/accounts/${accountId}/reserved-balances`),
  renameReservedBalance: (id: string, displayName: string) =>
    api.put<ReservedBalance>(`/reserved-balances/${id}/display-name`, { displayName }),
  resetReservedBalanceName: (id: string) =>
    api.delete<ReservedBalance>(`/reserved-balances/${id}/display-name`),
  reservedBalanceHistory: (id: string) =>
    api.get<ReservedBalanceHistory>(`/wealth/reserved-balances/${id}/history`),
  updateReservedBalanceTreatment: (accountId: string, treatment: ReservedBalanceTreatment) =>
    api.put<Account>(`/accounts/${accountId}/reserved-balance-treatment`, { treatment }),
}
