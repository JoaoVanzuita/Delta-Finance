import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type InvestmentOverview = components['schemas']['InvestmentOverview']
export type InvestmentAllocation = components['schemas']['InvestmentAllocation']
export type InvestmentHolding = components['schemas']['InvestmentHolding']
export type InvestmentHoldingList = components['schemas']['InvestmentHoldingList']
export type InvestmentSource = components['schemas']['InvestmentSource']
export type InvestmentSourceList = components['schemas']['InvestmentSourceList']
export type InvestmentIncome = components['schemas']['InvestmentIncome']
export type InvestmentIncomeItem = components['schemas']['InvestmentIncomeItem']
export type InvestmentIncomeEntry = components['schemas']['InvestmentIncomeEntry']
export type InvestmentIncomeInput = components['schemas']['InvestmentIncomeRequest']
export type InvestmentContribution = components['schemas']['InvestmentContribution']
export type InvestmentContributionMonth = components['schemas']['InvestmentContributionMonth']
export type InvestmentContributionFlow = components['schemas']['InvestmentContributionFlow']
export type InvestmentContributionSource = components['schemas']['InvestmentContributionSource']
export type InvestmentCostBasisInput = components['schemas']['InvestmentCostBasisRequest']
export type InvestmentAssetClassInput = components['schemas']['InvestmentAssetClassRequest']
export type InvestmentHistoryPoint = components['schemas']['InvestmentHistoryPoint']
export type InvestmentClassSlice = components['schemas']['InvestmentClassSlice']

export const investmentsApi = {
  investmentOverview: () => api.get<InvestmentOverview>('/investments/overview'),
  investmentHoldings: (status?: string, assetClass?: string) => {
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    if (assetClass) params.set('assetClass', assetClass)
    const query = params.toString()
    return api.get<InvestmentHoldingList>(`/investments/holdings${query ? `?${query}` : ''}`)
  },
  investmentHolding: (id: string) => api.get<InvestmentHolding>(`/investments/holdings/${id}`),
  investmentSources: () => api.get<InvestmentSourceList>('/investments/sources'),
  investmentIncome: (from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return api.get<InvestmentIncome>(`/investments/income${query ? `?${query}` : ''}`)
  },
  investmentContributions: (from?: string, to?: string, connectionId?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    if (connectionId) params.set('connectionId', connectionId)
    const query = params.toString()
    return api.get<InvestmentContribution>(`/investments/contributions${query ? `?${query}` : ''}`)
  },
  createInvestmentIncome: (input: InvestmentIncomeInput) =>
    api.post<InvestmentIncomeEntry>('/investments/income', input),
  updateInvestmentIncome: (id: string, input: InvestmentIncomeInput) =>
    api.patch<InvestmentIncomeEntry>(`/investments/income/${id}`, input),
  deleteInvestmentIncome: (id: string) => api.delete<void>(`/investments/income/${id}`),
  updateInvestmentCostBasis: (id: string, input: InvestmentCostBasisInput) =>
    api.patch<void>(`/investments/holdings/${id}/cost-basis`, input),
  updateInvestmentAssetClass: (id: string, input: InvestmentAssetClassInput) =>
    api.patch<void>(`/investments/holdings/${id}/asset-class`, input),
  investmentHistory: (from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return api.get<InvestmentHistoryPoint[]>(`/investments/history${query ? `?${query}` : ''}`)
  },
}
