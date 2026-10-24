import { expect, test, type Page } from '@playwright/test'

/**
 * Auditoria responsiva (T178).
 *
 * Verifica largura mínima de 360 px e zoom de 200% (viewport CSS equivalente a
 * 640 px para um layout de 1280 px): sem rolagem horizontal da página, sem
 * conteúdo cortado, navegação alcançável e alvos de toque com pelo menos 44 px.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

const ACCOUNTS = {
  items: [
    {
      id: 'acc-cash',
      type: 'CASH',
      origin: 'MANUAL',
      alias: 'Carteira da família',
      currency: 'BRL',
      currentBalance: '1250.00',
      status: 'ACTIVE',
    },
  ],
}

async function installStub(page: Page) {
  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const emptyPage = { items: [], nextCursor: null, hasMore: false }

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/accounts') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ACCOUNTS),
      })
    }
    if (path === '/dashboard/summary') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          periodStart: '2026-10-01',
          periodEnd: '2026-10-31',
          periodResult: '0.00',
          currentBalance: '1250.00',
          currentBalanceReferenceAt: null,
          income: '0.00',
          expenses: '0.00',
          pendingCount: 0,
          pendingIncome: '0.00',
          pendingExpenses: '0.00',
          pendingInternalMatches: 0,
          lastSuccessfulSyncAt: null,
          cardOutstandingBalance: '0.00',
        }),
      })
    }
    if (path === '/dashboard/categories' || path === '/dashboard/timeseries') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    }
    if (path === '/sync/status') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ integrations: [], connections: [], pendingCount: 0 }),
      })
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
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(emptyPage),
    })
  })
}

async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => {
    const root = document.documentElement
    return {
      scrollWidth: root.scrollWidth,
      clientWidth: root.clientWidth,
      offenders: Array.from(document.querySelectorAll<HTMLElement>('body *'))
        .filter(
          (element) =>
            element.getBoundingClientRect().right - document.documentElement.clientWidth > 1,
        )
        .slice(0, 5)
        .map((element) => `${element.tagName.toLowerCase()}.${element.className}`),
    }
  })
  expect(overflow.scrollWidth, `elementos: ${overflow.offenders.join(', ')}`).toBeLessThanOrEqual(
    overflow.clientWidth + 1,
  )
}

for (const viewport of [
  { width: 360, height: 800, label: '360 px' },
  { width: 640, height: 400, label: 'zoom 200% (640 px)' },
]) {
  test(`layout sem corte em ${viewport.label}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })
    await installStub(page)

    for (const path of [
      '/dashboard',
      '/accounts',
      '/transactions',
      '/investments',
      '/settings/privacy',
    ]) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await expectNoHorizontalOverflow(page)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    }
  })
}

test('alvos de toque e navegação alcançáveis em 360 px', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 })
  await installStub(page)
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')

  const nav = page.getByRole('navigation', { name: 'Navegação principal' })
  await expect(nav).toBeVisible()
  const targets = await nav.getByRole('link').all()
  for (const target of targets) {
    const box = await target.boundingBox()
    expect(box?.height ?? 0).toBeGreaterThanOrEqual(44)
  }

  await nav.getByRole('link', { name: 'Privacidade', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Privacidade e dados' })).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
