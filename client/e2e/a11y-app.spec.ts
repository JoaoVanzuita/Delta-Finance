import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

import { AUDIT_ROUTES } from './auditRoutes'
import { installMockApi } from '../screenshots/mockApi'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

/**
 * Auditoria axe da aplicação completa, incluindo detalhes dinâmicos.
 *
 * Usa o banco fictício de screenshots para que faturas, cartões, investimentos e
 * contas tenham dados reais de teste em vez de páginas vazias. Cada rota é
 * verificada no tema claro e no escuro, com exatamente um `h1`.
 */
for (const route of AUDIT_ROUTES) {
  test(`axe e hierarquia em ${route.name} (claro e escuro)`, async ({ page }) => {
    await installMockApi(page, undefined, { authenticated: route.authenticated })
    await page.goto(route.path)
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
    await expect(page.getByRole('main')).toBeVisible()

    const light = await new AxeBuilder({ page: page as never }).withTags(WCAG_TAGS).analyze()
    expect(light.violations, JSON.stringify(light.violations, null, 2)).toEqual([])

    await page.emulateMedia({ colorScheme: 'dark' })
    await page.reload()
    await page.waitForLoadState('networkidle')

    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    const dark = await new AxeBuilder({ page: page as never }).withTags(WCAG_TAGS).analyze()
    expect(dark.violations, JSON.stringify(dark.violations, null, 2)).toEqual([])
  })
}

test('estados interativos mantêm acessibilidade', async ({ page }) => {
  await installMockApi(page)

  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')
  await page.getByRole('button', { name: 'Feira' }).first().click()
  await page.getByText('Categoria: Feira').waitFor({ timeout: 5_000 })
  const drawer = await new AxeBuilder({ page: page as never }).withTags(WCAG_TAGS).analyze()
  expect(drawer.violations, JSON.stringify(drawer.violations, null, 2)).toEqual([])

  await page.goto('/invoices/inv-open')
  await page.waitForLoadState('networkidle')
  await page.getByRole('button', { name: 'Adicionar item' }).click()
  await page.getByRole('dialog').waitFor({ timeout: 5_000 })
  const dialog = await new AxeBuilder({ page: page as never }).withTags(WCAG_TAGS).analyze()
  expect(dialog.violations, JSON.stringify(dialog.violations, null, 2)).toEqual([])

  await page.goto('/transactions')
  await page.waitForLoadState('networkidle')
  await page
    .getByRole('button', { name: /Ações de/ })
    .first()
    .click()
  await page.getByRole('dialog', { name: /Ações de/ }).waitFor({ timeout: 5_000 })
  const menu = await new AxeBuilder({ page: page as never }).withTags(WCAG_TAGS).analyze()
  expect(menu.violations, JSON.stringify(menu.violations, null, 2)).toEqual([])
})
