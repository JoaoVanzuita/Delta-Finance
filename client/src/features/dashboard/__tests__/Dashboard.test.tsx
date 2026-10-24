import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const SUMMARY = {
  periodStart: '2026-10-01',
  periodEnd: '2026-10-31',
  periodResult: '390.00',
  currentBalance: '1100.00',
  currentBalanceReferenceAt: '2026-10-23T12:00:00Z',
  income: '1000.00',
  expenses: '610.00',
  pendingCount: 1,
  pendingIncome: '0.00',
  pendingExpenses: '30.00',
  pendingInternalMatches: 2,
  lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
  cardOutstandingBalance: '310.00',
}

const CATEGORIES = [
  {
    categoryId: 'cat-1',
    categoryName: 'Feira',
    amount: '200.00',
    transactionCount: 1,
  },
  {
    categoryId: '00000000-0000-0000-0000-000000000000',
    categoryName: 'Sem categoria',
    amount: '410.00',
    transactionCount: 3,
  },
]

const TIMESERIES = [
  {
    label: '03/10',
    start: '2026-10-03',
    end: '2026-10-04',
    income: '1000.00',
    expenses: '500.00',
    result: '500.00',
  },
  {
    label: '08/10',
    start: '2026-10-08',
    end: '2026-10-09',
    income: '0.00',
    expenses: '110.00',
    result: '-110.00',
  },
]

const ENTRIES = {
  items: [
    {
      id: 'entry-1',
      source: 'TRANSACTION',
      sourceId: 'entry-1',
      origin: 'MANUAL',
      occurredOn: '2026-10-03',
      amount: '-200.00',
      description: 'Feira da semana',
      categoryId: 'cat-1',
      accountId: 'acc-1',
      type: 'DEBIT',
    },
    {
      id: 'entry-2',
      source: 'INVOICE_ITEM',
      sourceId: 'entry-2',
      origin: 'MANUAL',
      occurredOn: '2026-10-07',
      amount: '-20.00',
      description: 'Juros da fatura',
      accountId: 'card-1',
      invoiceId: 'invoice-1',
      type: 'INTEREST',
    },
  ],
  hasMore: false,
}

const ACCOUNTS = {
  items: [
    {
      id: 'acc-1',
      type: 'CASH',
      origin: 'MANUAL',
      alias: 'Carteira',
      currency: 'BRL',
      currentBalance: '100.00',
      status: 'ACTIVE',
    },
    {
      id: 'card-1',
      type: 'CREDIT',
      origin: 'IMPORTED',
      alias: 'Cartão',
      currency: 'BRL',
      currentBalance: '0.00',
      status: 'ACTIVE',
    },
  ],
}

const CATEGORY_LIST = {
  items: [
    { id: 'cat-1', name: 'Feira', kind: 'EXPENSE', predefined: false, active: true },
    { id: 'cat-2', name: 'Salário', kind: 'INCOME', predefined: true, active: true },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi(options: { summaryStatus?: number; summary?: unknown } = {}) {
  const mock = vi.fn((url: string) => {
    if (url.includes('/dashboard/summary')) {
      if (options.summaryStatus && options.summaryStatus >= 400) {
        return jsonResponse(
          { code: 'INTERNAL_ERROR', detail: 'Falha no relatório.' },
          options.summaryStatus,
        )
      }
      return jsonResponse(options.summary ?? SUMMARY)
    }
    if (url.includes('/dashboard/categories')) return jsonResponse(CATEGORIES)
    if (url.includes('/dashboard/timeseries')) return jsonResponse(TIMESERIES)
    if (url.includes('/reports/entries')) return jsonResponse(ENTRIES)
    if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
    if (url.includes('/categories')) return jsonResponse(CATEGORY_LIST)
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage() {
  return renderWithProviders(<DashboardPage />, { route: '/dashboard' })
}

describe('dashboard', () => {
  beforeEach(() => {
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('mostra resultado, saldo atual, cartões e pendentes com rótulos distintos', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 3, name: 'Resultado do período' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Visão do período' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 3, name: 'Saldo atual consolidado' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 3, name: 'Cartões de crédito' }),
    ).toBeInTheDocument()

    expect(screen.getAllByText(/R\$\s*390,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*1\.100,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*610,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*310,00/).length).toBeGreaterThan(0)

    const pending = screen.getByRole('heading', { name: 'Transações pendentes' }).closest('section')
    expect(pending).not.toBeNull()
    expect(within(pending as HTMLElement).getByText(/1/)).toBeInTheDocument()
    expect(within(pending as HTMLElement).getByText(/R\$\s*30,00/)).toBeInTheDocument()
    expect(within(pending as HTMLElement).queryByText('Total líquido')).not.toBeInTheDocument()
    expect(within(pending as HTMLElement).queryByText(/Total pendente/i)).not.toBeInTheDocument()

    expect(screen.queryByText(/fluxo de caixa/i)).not.toBeInTheDocument()
    expect(screen.getByText(/2 movimento\(s\) interno\(s\)/)).toBeInTheDocument()
  })

  it('exibe categorias, série temporal e abre o drill-down com a mesma projeção', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('button', { name: /Feira/ })
    expect(screen.getByRole('table', { name: /Despesas por categoria/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Detalhar 03\/10/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Feira/ }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/reports/entries'))
      expect(call).toBeDefined()
      expect(String(call?.[0])).toContain('categoryId=cat-1')
    })
    expect((await screen.findAllByText('Feira da semana')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Juros da fatura').length).toBeGreaterThan(0)
    expect(screen.getByText('Categoria: Feira')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Fechar detalhamento' }))
    expect(screen.queryByText('Feira da semana')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Detalhar 03\/10/ }))
    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(([url]) => String(url).includes('/reports/entries'))
      expect(
        calls.some(
          ([url]) =>
            String(url).includes('from=2026-10-03') && String(url).includes('to=2026-10-03'),
        ),
      ).toBe(true)
    })
    expect(await screen.findByText('Período: 03/10')).toBeInTheDocument()
  })

  it('aplica filtros de conta e período sem trocar os rótulos financeiros', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 3, name: 'Resultado do período' })
    await user.selectOptions(screen.getByLabelText('Conta ou cartão'), 'acc-1')
    await user.selectOptions(screen.getByLabelText('Período'), 'YEAR')
    await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(([url]) =>
        String(url).includes('/dashboard/summary'),
      )
      expect(
        calls.some(
          ([url]) => String(url).includes('accountId=acc-1') && String(url).includes('period=YEAR'),
        ),
      ).toBe(true)
    })
  })

  it('mostra estado vazio quando não há lançamentos no período', async () => {
    stubApi({
      summary: {
        ...SUMMARY,
        income: '0.00',
        expenses: '0.00',
        periodResult: '0.00',
        pendingCount: 0,
        pendingExpenses: '0.00',
        pendingInternalMatches: 0,
        cardOutstandingBalance: '0.00',
      },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (url.includes('/dashboard/categories')) return jsonResponse([])
        if (url.includes('/dashboard/timeseries')) return jsonResponse([])
        if (url.includes('/dashboard/summary')) {
          return jsonResponse({
            ...SUMMARY,
            income: '0.00',
            expenses: '0.00',
            periodResult: '0.00',
            pendingCount: 0,
            pendingExpenses: '0.00',
            pendingInternalMatches: 0,
            cardOutstandingBalance: '0.00',
          })
        }
        if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
        if (url.includes('/categories')) return jsonResponse(CATEGORY_LIST)
        return jsonResponse({}, 404)
      }),
    )

    renderPage()

    expect(await screen.findByText(/Nenhum lançamento efetivado no período/)).toBeInTheDocument()
    expect(screen.getByText(/Sem despesas por categoria no período/)).toBeInTheDocument()
    expect(screen.getByText(/Sem evolução temporal para o período selecionado/)).toBeInTheDocument()
  })

  it('mostra erro recuperável quando o resumo falha', async () => {
    stubApi({ summaryStatus: 500 })
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível carregar o dashboard' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Visão do período' })
    await expectNoA11yViolations()
  })
})
