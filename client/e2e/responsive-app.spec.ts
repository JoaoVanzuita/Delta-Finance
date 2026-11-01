import { expect, test, type Page } from '@playwright/test'

import { AUDIT_ROUTES } from './auditRoutes'
import { installMockApi } from '../screenshots/mockApi'

const VIEWPORTS = [
  { label: 'desktop 1280', width: 1280, height: 900 },
  { label: 'desktop 1440', width: 1440, height: 900 },
  { label: 'tablet 768', width: 768, height: 1024 },
  { label: 'mobile 360', width: 360, height: 800 },
  { label: 'mobile 390', width: 390, height: 844 },
] as const

async function expectNoHorizontalOverflow(page: Page): Promise<void> {
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
  expect(
    overflow.scrollWidth,
    `${page.url()} — elementos: ${overflow.offenders.join(', ')}`,
  ).toBeLessThanOrEqual(overflow.clientWidth + 1)
}

for (const viewport of VIEWPORTS) {
  test(`todas as rotas sem overflow em ${viewport.label}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })

    for (const route of AUDIT_ROUTES) {
      await installMockApi(page, undefined, { authenticated: route.authenticated })
      await page.goto(route.path)
      await page.waitForLoadState('networkidle')

      await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
      await expectNoHorizontalOverflow(page)
    }
  })
}

test('alvos de toque principais têm ao menos 44 px em 360 e 390', async ({ page }) => {
  test.setTimeout(120_000)
  for (const viewport of VIEWPORTS.filter((item) => item.width <= 390)) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height })

    for (const route of AUDIT_ROUTES) {
      await installMockApi(page, undefined, { authenticated: route.authenticated })
      await page.goto(route.path)
      await page.waitForLoadState('networkidle')

      const smallTargets = await page.evaluate(() =>
        Array.from(document.querySelectorAll<HTMLElement>('main button, nav a'))
          .filter((element) => element.offsetParent !== null)
          .map((element) => {
            const rect = element.getBoundingClientRect()
            return {
              label:
                element.getAttribute('aria-label') ||
                element.textContent?.trim().replace(/\s+/g, ' ').slice(0, 60) ||
                element.tagName.toLowerCase(),
              width: Math.round(rect.width),
              height: Math.round(rect.height),
            }
          })
          .filter((target) => target.width < 44 || target.height < 44),
      )

      expect(smallTargets, `${route.path} em ${viewport.width}px`).toEqual([])
    }
  }
})
