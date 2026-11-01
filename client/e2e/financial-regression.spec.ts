import { expect, test } from '@playwright/test'

import { installMockApi } from '../screenshots/mockApi'

/**
 * Regressão de apresentação financeira.
 *
 * Os valores vêm do banco fictício e dos endpoints mockados; o teste garante que
 * a UI continua exibindo os totais consolidados sem recalculá-los nem misturar
 * moedas. Nenhuma regra de negócio é exercitada aqui — a autoridade continua no
 * backend e nas suítes de serviço.
 */
test('dashboard preserva resultado, receitas, despesas, saldo e dívida de cartões', async ({
  page,
}) => {
  await installMockApi(page)
  await page.goto('/dashboard')
  await page.waitForLoadState('networkidle')

  for (const value of [
    'R$ 12.627,90',
    'R$ 16.200,00',
    'R$ 3.572,10',
    'R$ 6.682,10',
    'R$ 1.070,00',
  ]) {
    await expect(page.getByText(value).first()).toBeVisible()
  }
  await expect(page.getByText('Dívida separada do saldo.')).toBeVisible()
})

test('patrimônio separa ativos, passivos, cartão e moeda não consolidada', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/wealth')
  await page.waitForLoadState('networkidle')

  for (const value of ['R$ 244.612,10', 'R$ 257.682,10', 'R$ 13.070,00']) {
    await expect(page.getByText(value).first()).toBeVisible()
  }
  await expect(page.getByText('Cartão Alfa').first()).toBeVisible()
  await expect(page.getByText('Conta Dólar').first()).toBeVisible()
  await expect(page.getByText('Moeda não suportada na consolidação (USD).').first()).toBeVisible()
  await expect(page.getByText(/Alguns itens não compõem os totais/)).toBeVisible()
})

test('investimentos preservam valor atual, proventos e resultados distintos', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/investments')
  await page.waitForLoadState('networkidle')

  await expect(page.getByText('R$ 1.500,00').first()).toBeVisible()
  await expect(page.getByText('R$ 12,50').first()).toBeVisible()
  await expect(page.getByText('R$ 50,00').first()).toBeVisible()
  await expect(page.getByText('Resultado informado pelo provedor')).toBeVisible()
  await expect(page.getByText('Resultado estimado', { exact: true })).toBeVisible()
  await expect(page.getByText(/O resultado estimado não é rendimento realizado/)).toBeVisible()
  await expect(page.getByText(/não são receita do Dashboard/i)).toBeVisible()
})

test('cartões e faturas mantêm status, saldo devedor e origem', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/cards')
  await page.waitForLoadState('networkidle')

  await expect(page.getByText('Limite total', { exact: true })).toBeVisible()
  await expect(page.getByText('R$ 1.070,00').first()).toBeVisible()
  await expect(page.getByText('Cartão Alfa').first()).toBeVisible()

  await page.goto('/invoices/inv-open')
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
  await expect(page.getByText('R$ 220,00').first()).toBeVisible()
  await expect(page.getByText('Total informado pelo provedor')).toBeVisible()
  await expect(page.getByText(/Diferença de total/)).toBeVisible()
})

test('metas preservam alvo, progresso e alocações planejadas', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/goals')
  await page.waitForLoadState('networkidle')

  await expect(page.getByRole('heading', { name: 'Reserva de emergência' })).toBeVisible()
  await expect(page.getByText('R$ 20.000,00').first()).toBeVisible()
  await expect(page.getByText('R$ 10.000,00').first()).toBeVisible()
  await expect(page.getByText('50%').first()).toBeVisible()
  await expect(page.getByText(/Alocações são planejamento/)).toBeVisible()
})
