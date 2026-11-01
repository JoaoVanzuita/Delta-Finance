import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import type { Connection, SyncStatus } from '@/features/integration/api'
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
  { categoryId: 'cat-1', categoryName: 'Feira', amount: '200.00', transactionCount: 1 },
  {
    categoryId: '00000000-0000-0000-0000-000000000000',
    categoryName: 'Sem categoria',
    amount: '410.00',
    transactionCount: 3,
  },
]

const INCOME_CATEGORIES = [
  { categoryId: 'income-salary', categoryName: 'Salário', amount: '1000.00', transactionCount: 1 },
  { categoryId: 'income-others', categoryName: 'Outros', amount: '50.00', transactionCount: 1 },
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

const CONNECTION: Connection = {
  id: 'conn-1',
  institutionName: 'Banco Alfa',
  status: 'SYNCED',
  lastSyncAt: '2026-10-23T12:00:00Z',
  accountCount: 2,
}

const SYNC_STATUS: SyncStatus = {
  integrations: [
    {
      id: 'integration-1',
      provider: 'MEU_PLUGGY',
      status: 'SYNCED',
      lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
      connectionCount: 1,
    },
  ],
  connections: [CONNECTION],
  pendingJobs: 2,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.search}</output>
}

function stubApi(
  options: {
    summaryStatus?: number
    categoriesStatus?: number
    timeseriesStatus?: number
    entriesStatus?: number
    summary?: unknown
    categories?: unknown
    timeseries?: unknown
    entries?: unknown
    syncStatus?: SyncStatus
  } = {},
) {
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
    if (url.includes('/dashboard/categories')) {
      if (options.categoriesStatus && options.categoriesStatus >= 400) {
        return jsonResponse({ code: 'INTERNAL_ERROR' }, options.categoriesStatus)
      }
      const direction = new URL(url, 'http://localhost').searchParams.get('direction')
      return jsonResponse(
        options.categories ?? (direction === 'INCOME' ? INCOME_CATEGORIES : CATEGORIES),
      )
    }
    if (url.includes('/dashboard/timeseries')) {
      if (options.timeseriesStatus && options.timeseriesStatus >= 400) {
        return jsonResponse({ code: 'INTERNAL_ERROR' }, options.timeseriesStatus)
      }
      return jsonResponse(options.timeseries ?? TIMESERIES)
    }
    if (url.includes('/reports/entries')) {
      if (options.entriesStatus && options.entriesStatus >= 400) {
        return jsonResponse({ code: 'INTERNAL_ERROR' }, options.entriesStatus)
      }
      return jsonResponse(options.entries ?? ENTRIES)
    }
    if (url.includes('/sync/status')) return jsonResponse(options.syncStatus ?? SYNC_STATUS)
    if (url.includes('/accounts')) return jsonResponse(ACCOUNTS)
    if (url.includes('/categories')) return jsonResponse(CATEGORY_LIST)
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderPage(route = '/dashboard') {
  return renderWithProviders(
    <>
      <DashboardPage />
      <LocationProbe />
    </>,
    { route },
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('dashboard', () => {
  it('hierarquiza resultado, receitas, despesas, saldo e dívida sem somar cartões ao saldo', async () => {
    stubApi()
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Resultado do período' }),
    ).toBeVisible()
    expect(screen.getByRole('heading', { level: 3, name: 'Receitas' })).toBeVisible()
    expect(screen.getByRole('heading', { level: 3, name: 'Despesas' })).toBeVisible()
    expect(screen.getByRole('heading', { level: 3, name: 'Saldo em contas' })).toBeVisible()
    expect(
      screen.getByRole('heading', { level: 3, name: 'Saldo devedor de cartões' }),
    ).toBeVisible()

    expect(screen.getAllByText(/R\$\s*390,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*1\.100,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*610,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*310,00/).length).toBeGreaterThan(0)
    expect(screen.getByText(/Dívida separada do saldo/)).toBeInTheDocument()

    const attention = screen.getByRole('heading', { name: 'Precisa de atenção' }).closest('section')
    expect(attention).not.toBeNull()
    expect(within(attention as HTMLElement).getByText(/Transações pendentes/)).toBeInTheDocument()
    expect(within(attention as HTMLElement).getByText(/R\$\s*30,00/)).toBeInTheDocument()
    expect(within(attention as HTMLElement).getByText(/Movimentos internos/)).toBeInTheDocument()
    expect(screen.queryByText(/fluxo de caixa/i)).not.toBeInTheDocument()
  })

  it('mostra evolução em gráfico com séries, tooltip acessível e tabela equivalente', async () => {
    stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Evolução financeira' })
    expect(
      screen.getByRole('img', { name: /Evolução de receitas, despesas, resultado/i }),
    ).toBeVisible()
    expect(screen.getByRole('checkbox', { name: 'Receitas' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Despesas' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Resultado' })).toBeChecked()

    const point = screen.getByRole('button', { name: /03\/10: receitas/i })
    await user.hover(point)
    expect((await screen.findAllByText('03/10')).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R\$\s*500,00/).length).toBeGreaterThan(0)

    expect(
      screen.getByRole('table', { name: /Evolução temporal de receitas/i }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Resultado' }))
    expect(screen.getByRole('checkbox', { name: 'Resultado' })).not.toBeChecked()
  })

  it('mantém o donut e agrupa excedente em Outras quando há mais de oito categorias', async () => {
    const manyCategories = Array.from({ length: 9 }, (_, index) => ({
      categoryId: `cat-${index + 1}`,
      categoryName: `Categoria ${index + 1}`,
      amount: `${100 - index * 5}.00`,
      transactionCount: 1,
    }))
    stubApi({ categories: manyCategories })
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Categorias' })
    expect(screen.getByRole('img', { name: /Composição de despesas/i })).toBeInTheDocument()
    expect(screen.getAllByText('Outras 1 categoria').length).toBeGreaterThan(0)
  })

  it('mostra a aba de receitas por categoria com donut e drill-down próprio', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Categorias' })
    await user.click(screen.getByRole('tab', { name: 'Receitas' }))

    expect(
      screen.getByRole('img', { name: /Composição de receitas por categoria/i }),
    ).toBeInTheDocument()
    expect(screen.getByText('Total de receitas no período')).toBeInTheDocument()
    expect(screen.getAllByText('Salário').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Outras receitas').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: /Salário/ }))
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: 'Categoria: Salário' })).toBeInTheDocument()
    })
    expect(screen.getAllByText(/das receitas/).length).toBeGreaterThan(0)
    expect(
      fetchMock.mock.calls.some(
        ([url]) =>
          String(url).includes('/dashboard/categories') && String(url).includes('direction=INCOME'),
      ),
    ).toBe(true)
  })

  it('abre drill-down por categoria no drawer preservando o contexto', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Categorias' })
    expect(screen.getAllByText('Sem categoria (despesas)').length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: /Feira/ }))

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: 'Categoria: Feira' })).toBeInTheDocument()
    })
    expect(screen.getAllByText('Feira da semana').length).toBeGreaterThan(0)
    expect(screen.getByText(/R\$\s*200,00 em 1 lançamento/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('categoryId=cat-1'))).toBe(
      true,
    )

    await user.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('dialog', { name: 'Categoria: Feira' })).not.toBeInTheDocument()
  })

  it('mantém filtros na URL, aplica período e permite remover chips', async () => {
    const fetchMock = stubApi()
    const user = userEvent.setup()
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Resultado do período' })

    await user.click(screen.getByRole('button', { name: 'Ano' }))
    await waitFor(() => {
      expect(screen.getByTestId('location').textContent).toContain('period=YEAR')
    })

    await user.click(screen.getByRole('button', { name: /Filtros/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Filtros do dashboard' })
    await user.click(within(dialog).getByRole('checkbox', { name: /Carteira/ }))
    await user.selectOptions(within(dialog).getByLabelText('Categoria'), 'cat-1')
    await user.selectOptions(within(dialog).getByLabelText('Tipo'), 'DEBIT')
    await user.click(within(dialog).getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      const location = screen.getByTestId('location').textContent ?? ''
      expect(location).toContain('period=YEAR')
      expect(location).toContain('accountId=acc-1')
      expect(location).toContain('categoryId=cat-1')
      expect(location).toContain('type=DEBIT')
    })

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(([url]) =>
        String(url).includes('/dashboard/summary'),
      )
      expect(
        calls.some(
          ([url]) =>
            String(url).includes('accountId=acc-1') &&
            String(url).includes('categoryId=cat-1') &&
            String(url).includes('period=YEAR'),
        ),
      ).toBe(true)
    })

    expect(screen.getByText('Conta: Carteira')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Remover filtro Conta: Carteira' }))
    await waitFor(() => {
      expect(screen.getByTestId('location').textContent).not.toContain('accountId=acc-1')
    })
  })

  it('lê filtros de uma URL recarregada e marca filtros ativos', async () => {
    const fetchMock = stubApi()
    renderPage('/dashboard?period=YEAR&accountId=acc-1&categoryId=cat-1&minAmount=10.00')

    expect(await screen.findByText('Conta: Carteira')).toBeInTheDocument()
    expect(screen.getByText('Categoria: Feira')).toBeInTheDocument()
    expect(screen.getByText('Valor mínimo: R$ 10,00')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ano' })).toHaveAttribute('aria-pressed', 'true')

    await waitFor(() => {
      const calls = fetchMock.mock.calls.filter(([url]) =>
        String(url).includes('/dashboard/summary'),
      )
      expect(
        calls.some(
          ([url]) =>
            String(url).includes('period=YEAR') &&
            String(url).includes('accountId=acc-1') &&
            String(url).includes('categoryId=cat-1') &&
            String(url).includes('minAmount=10.00'),
        ),
      ).toBe(true)
    })
  })

  it('distingue período sem dados de filtros sem resultado', async () => {
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
      entries: { items: [], hasMore: false },
      categories: [],
      timeseries: [],
    })

    const { unmount } = renderPage()
    expect(
      await screen.findByRole('heading', { name: 'Nenhuma movimentação no período' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Nenhum resultado para estes filtros/)).not.toBeInTheDocument()
    unmount()

    renderPage('/dashboard?categoryId=cat-1')
    expect(
      await screen.findByRole('heading', { name: 'Nenhum resultado para estes filtros' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Limpar filtros' })).toBeInTheDocument()
  })

  it('mostra erro parcial sem derrubar os indicadores', async () => {
    stubApi({ categoriesStatus: 500 })
    renderPage()

    expect(await screen.findByText('Distribuição de despesas indisponível')).toBeInTheDocument()
    expect(screen.getAllByText(/R\$\s*390,00/).length).toBeGreaterThan(0)
  })

  it('mostra erro recuperável quando o resumo falha', async () => {
    stubApi({ summaryStatus: 500 })
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Não foi possível carregar o dashboard' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })

  it('mostra ação necessária de sincronização considerando as conexões', async () => {
    stubApi({
      syncStatus: {
        ...SYNC_STATUS,
        connections: [
          { ...CONNECTION, status: 'SYNCED' },
          {
            id: 'conn-2',
            institutionName: 'Banco Beta',
            status: 'AUTHORIZATION_EXPIRED',
            lastErrorCode: 'CONSENT_EXPIRED',
            accountCount: 1,
          },
        ],
      },
    })
    renderPage()

    expect(await screen.findByText('Banco Beta')).toBeInTheDocument()
    expect(screen.getByText(/consentimento bancário expirou/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Revisar integração' })).toHaveAttribute(
      'href',
      '/settings/integration',
    )
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    stubApi()
    renderPage()

    await screen.findByRole('heading', { level: 2, name: 'Resultado do período' })
    await expectNoA11yViolations()
  })
})
