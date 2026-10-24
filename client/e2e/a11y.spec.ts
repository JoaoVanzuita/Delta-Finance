import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

/**
 * Auditoria de acessibilidade (T177).
 *
 * Executa axe-core em todas as telas do MVP, verifica marcos/landmarks, hierarquia
 * de títulos, link de pular conteúdo e navegação por teclado. O stub de API mantém
 * o teste determinístico e sem backend.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const SUMMARY = {
  periodStart: '2026-10-01',
  periodEnd: '2026-10-31',
  periodResult: '0.00',
  currentBalance: '0.00',
  currentBalanceReferenceAt: null,
  income: '0.00',
  expenses: '0.00',
  pendingCount: 0,
  pendingIncome: '0.00',
  pendingExpenses: '0.00',
  pendingInternalMatches: 0,
  lastSuccessfulSyncAt: null,
  cardOutstandingBalance: '0.00',
}

async function installStub(page: Page) {
  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/api/v1', '')
    const emptyPage = { items: [], nextCursor: null, hasMore: false }

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/dashboard/summary') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(SUMMARY),
      })
    }
    if (path === '/dashboard/categories' || path === '/dashboard/timeseries') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    }
    if (path === '/integrations/pluggy') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'integration-1',
          provider: 'MEU_PLUGGY',
          clientIdMasked: null,
          status: 'INCOMPLETE',
          lastSyncAttemptAt: null,
          lastSuccessfulSyncAt: null,
          lastErrorCode: null,
          connectionCount: 0,
        }),
      })
    }
    if (path === '/sync/status') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ integrations: [], connections: [], pendingCount: 0 }),
      })
    }
    if (
      path === '/accounts' ||
      path === '/transactions' ||
      path === '/categories' ||
      path === '/sync/runs' ||
      path === '/internal-matches' ||
      path === '/reports/entries'
    ) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(emptyPage),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(emptyPage),
    })
  })
}

const ROUTES: ReadonlyArray<{ path: string; name: string }> = [
  { path: '/', name: 'início' },
  { path: '/login', name: 'login' },
  { path: '/register', name: 'registro' },
  { path: '/dashboard', name: 'dashboard' },
  { path: '/accounts', name: 'contas' },
  { path: '/accounts/new', name: 'nova conta manual' },
  { path: '/transactions', name: 'transações' },
  { path: '/transactions/new', name: 'novo lançamento' },
  { path: '/categories', name: 'categorias' },
  { path: '/reconciliation', name: 'reconciliação' },
  { path: '/investments', name: 'investimentos' },
  { path: '/settings/integration', name: 'integração' },
  { path: '/settings/privacy', name: 'privacidade' },
  { path: '/account', name: 'conta' },
]

for (const route of ROUTES) {
  test(`axe sem violações WCAG 2.1 A/AA em ${route.name}`, async ({ page }) => {
    await installStub(page)
    await page.goto(route.path)
    await page.waitForLoadState('networkidle')

    const results = await new AxeBuilder({ page: page as never })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze()
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([])
  })
}

test('landmarks, hierarquia de títulos e link de pular conteúdo', async ({ page }) => {
  await installStub(page)
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')

  await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible()
  await expect(page.getByRole('main')).toBeVisible()
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)

  const skipLink = page.getByRole('link', { name: 'Pular para o conteúdo' })
  await expect(skipLink).toBeAttached()
  await page.keyboard.press('Tab')
  await expect(skipLink).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main')).toBeFocused()
})

test('navegação principal funciona apenas com teclado e expõe a página atual', async ({ page }) => {
  await installStub(page)
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')

  const current = page.getByRole('link', { name: 'Dashboard', exact: true })
  await expect(current).toHaveAttribute('aria-current', 'page')

  await page.getByRole('link', { name: 'Privacidade', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/settings\/privacy$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Privacidade e dados' })).toBeVisible()
})
