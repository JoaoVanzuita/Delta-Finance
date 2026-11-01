import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'

import {
  invalidateAccountViews,
  invalidateCardViews,
  invalidateCategoryViews,
  invalidateGoalViews,
  invalidateIntegrationViews,
  invalidateInvestmentViews,
  invalidateInvoiceViews,
  invalidateReconciliationViews,
  invalidateWealthViews,
  invalidateTransactionViews,
} from '@/lib/invalidation'
import { queryKeys } from '@/lib/query-keys'

function spyClient() {
  const client = new QueryClient()
  const spy = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()
  return { client, spy }
}

function invalidatedPrefixes(spy: ReturnType<typeof spyClient>['spy']): string[] {
  return spy.mock.calls.map(([filters]) => JSON.stringify(filters?.queryKey))
}

describe('invalidação compartilhada', () => {
  it('atualiza contas e dashboard ao alterar uma conta', async () => {
    const { client, spy } = spyClient()
    await invalidateAccountViews(client)
    const keys = invalidatedPrefixes(spy)
    expect(keys).toContain(JSON.stringify(queryKeys.accounts))
    expect(keys).toContain(JSON.stringify(queryKeys.accountPrefix))
    expect(keys).toContain(JSON.stringify(queryKeys.dashboardSummary))
  })

  it('atualiza contas, cartões, faturas e dashboard ao alterar transações', async () => {
    const { client, spy } = spyClient()
    await invalidateTransactionViews(client)
    const keys = invalidatedPrefixes(spy)
    for (const expected of [
      queryKeys.transactions,
      queryKeys.accounts,
      queryKeys.cards,
      queryKeys.invoices,
      queryKeys.internalMatches,
      queryKeys.dashboardEntries,
    ]) {
      expect(keys).toContain(JSON.stringify(expected))
    }
  })

  it('invalida a fatura específica e os módulos dependentes', async () => {
    const { client, spy } = spyClient()
    await invalidateInvoiceViews(client, 'invoice-1')
    const keys = invalidatedPrefixes(spy)
    expect(keys).toContain(JSON.stringify(queryKeys.invoice('invoice-1')))
    expect(keys).toContain(JSON.stringify(queryKeys.invoices))
    expect(keys).toContain(JSON.stringify(queryKeys.cards))
    expect(keys).toContain(JSON.stringify(queryKeys.dashboardSummary))
  })

  it('atualiza investimentos, patrimônio e metas nas dependências corretas', async () => {
    const { client, spy } = spyClient()
    await invalidateInvestmentViews(client)
    const keys = invalidatedPrefixes(spy)
    expect(keys).toContain(JSON.stringify(queryKeys.investments))
    expect(keys).toContain(JSON.stringify(queryKeys.wealth))
    expect(keys).toContain(JSON.stringify(queryKeys.goals))

    const wealth = spyClient()
    await invalidateWealthViews(wealth.client)
    const wealthKeys = invalidatedPrefixes(wealth.spy)
    expect(wealthKeys).toContain(JSON.stringify(queryKeys.wealth))
    expect(wealthKeys).toContain(JSON.stringify(queryKeys.goals))

    const goals = spyClient()
    await invalidateGoalViews(goals.client)
    expect(invalidatedPrefixes(goals.spy)).toContain(JSON.stringify(queryKeys.goals))
  })

  it('mantém prefixos de cartões, categorias e reconciliação', async () => {
    const { client, spy } = spyClient()
    await invalidateCardViews(client)
    await invalidateCategoryViews(client)
    await invalidateReconciliationViews(client)
    const keys = invalidatedPrefixes(spy)
    expect(keys).toContain(JSON.stringify(queryKeys.cards))
    expect(keys).toContain(JSON.stringify(queryKeys.categories))
    expect(keys).toContain(JSON.stringify(queryKeys.internalMatches))
  })

  it('atualiza integração, conexões e dados importados ao alterar conexões', async () => {
    const { client, spy } = spyClient()
    await invalidateIntegrationViews(client)
    const keys = invalidatedPrefixes(spy)
    for (const expected of [
      queryKeys.integration,
      queryKeys.connections,
      queryKeys.syncStatus,
      queryKeys.syncRuns,
      queryKeys.accounts,
      queryKeys.transactions,
      queryKeys.cards,
      queryKeys.invoices,
      queryKeys.investments,
      queryKeys.wealth,
      queryKeys.dashboardSummary,
    ]) {
      expect(keys).toContain(JSON.stringify(expected))
    }
  })
})
