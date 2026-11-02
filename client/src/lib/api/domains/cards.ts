import type { components } from '@/lib/api/schema'

import { api } from '../client'

export type Invoice = components['schemas']['Invoice']
export type InvoicePage = components['schemas']['InvoicePage']
export type InvoiceDetail = components['schemas']['InvoiceDetail']
export type InvoiceItem = components['schemas']['InvoiceItem']
export type InvoiceItemInput = components['schemas']['InvoiceItemRequest']
export type InvoiceItemCorrection = components['schemas']['InvoiceItemCorrection']
export type ProviderBill = components['schemas']['ProviderBill']
export type ProviderBillPayment = components['schemas']['ProviderBillPayment']
export type InvoicePayment = components['schemas']['InvoicePayment']
export type InvoicePaymentInput = components['schemas']['InvoicePaymentRequest']
export type InvoiceReconciliationInput = components['schemas']['InvoiceReconciliationRequest']
export type CardProfileInput = components['schemas']['CardProfileRequest']
export type CardProfile = components['schemas']['CardProfile']
export type CardOverview = components['schemas']['CardOverview']
export type CardOverviewItem = components['schemas']['CardOverviewItem']

export const cardsApi = {
  updateCardProfile: (accountId: string, input: CardProfileInput) =>
    api.put<CardProfile>(`/cards/${accountId}/profile`, input),
  cardOverview: () => api.get<CardOverview>('/cards/overview'),
  invoices: (accountId: string) => api.get<InvoicePage>(`/cards/${accountId}/invoices`),
  invoice: (id: string) => api.get<InvoiceDetail>(`/invoices/${id}`),
  addInvoiceItem: (invoiceId: string, input: InvoiceItemInput) =>
    api.post<InvoiceItem>(`/invoices/${invoiceId}/items`, input),
  updateInvoiceItem: (itemId: string, input: InvoiceItemInput) =>
    api.patch<InvoiceItem>(`/invoice-items/${itemId}`, input),
  deleteInvoiceItem: (itemId: string) => api.delete<void>(`/invoice-items/${itemId}`),
  invoiceItemCorrections: (itemId: string) =>
    api.get<InvoiceItemCorrection[]>(`/invoice-items/${itemId}/corrections`),
  addInvoicePayment: (invoiceId: string, input: InvoicePaymentInput) =>
    api.post<InvoicePayment>(`/invoices/${invoiceId}/payments`, input),
  reconcileInvoiceProvider: (invoiceId: string, input: InvoiceReconciliationInput) =>
    api.post<InvoiceDetail>(`/invoices/${invoiceId}/reconcile-provider`, input),
}
