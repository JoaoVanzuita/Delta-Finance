import type { QueryClient } from '@tanstack/react-query'

import { queryKeys } from './query-keys'

/**
 * Invalidação compartilhada por domínio.
 *
 * Cada helper atualiza os módulos que leem o mesmo fato financeiro. A UI nunca
 * corrige totais localmente: ela invalida e relê a fonte.
 */

function invalidate(queryClient: QueryClient, queryKey: readonly unknown[]): Promise<void> {
  return queryClient.invalidateQueries({ queryKey })
}

export async function invalidateDashboard(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.dashboardSummary),
    invalidate(queryClient, queryKeys.dashboardCategories),
    invalidate(queryClient, queryKeys.dashboardTimeseries),
    invalidate(queryClient, queryKeys.dashboardEntries),
  ])
}

export async function invalidateAccountViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.accountPrefix),
    invalidateDashboard(queryClient),
  ])
}

export async function invalidateTransactionViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.transactions),
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.accountPrefix),
    invalidate(queryClient, queryKeys.cards),
    invalidate(queryClient, queryKeys.invoices),
    invalidate(queryClient, queryKeys.internalMatches),
    invalidateDashboard(queryClient),
  ])
}

export async function invalidateCardViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.cards),
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.accountPrefix),
    invalidate(queryClient, queryKeys.invoices),
    invalidateDashboard(queryClient),
  ])
}

export async function invalidateInvoiceViews(
  queryClient: QueryClient,
  invoiceId?: string,
): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.invoices),
    invoiceId
      ? invalidate(queryClient, queryKeys.invoice(invoiceId))
      : invalidate(queryClient, queryKeys.invoicePrefix),
    invalidate(queryClient, queryKeys.transactions),
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.accountPrefix),
    invalidate(queryClient, queryKeys.cards),
    invalidateDashboard(queryClient),
  ])
}

export async function invalidateCategoryViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.categories),
    invalidate(queryClient, queryKeys.transactions),
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.cards),
    invalidateDashboard(queryClient),
  ])
}

export async function invalidateInvestmentViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.investments),
    invalidate(queryClient, queryKeys.wealth),
    invalidate(queryClient, queryKeys.goals),
  ])
}

export async function invalidateWealthViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.wealth),
    invalidate(queryClient, queryKeys.goals),
  ])
}

export async function invalidateGoalViews(queryClient: QueryClient): Promise<void> {
  await invalidate(queryClient, queryKeys.goals)
}

export async function invalidateReconciliationViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.internalMatches),
    invalidate(queryClient, queryKeys.transactions),
    invalidate(queryClient, queryKeys.accounts),
    invalidate(queryClient, queryKeys.accountPrefix),
    invalidate(queryClient, queryKeys.cards),
    invalidate(queryClient, queryKeys.invoices),
    invalidateDashboard(queryClient),
  ])
}

/**
 * Integração e conexões alteram saldos, transações, cartões, faturas e
 * investimentos importados. A invalidação parte dos prefixos compartilhados.
 */
export async function invalidateIntegrationViews(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    invalidate(queryClient, queryKeys.integration),
    invalidate(queryClient, queryKeys.connections),
    invalidate(queryClient, queryKeys.syncStatus),
    invalidate(queryClient, queryKeys.syncRuns),
    invalidateAccountViews(queryClient),
    invalidateTransactionViews(queryClient),
    invalidateCardViews(queryClient),
    invalidateInvoiceViews(queryClient),
    invalidateInvestmentViews(queryClient),
    invalidateWealthViews(queryClient),
  ])
}
