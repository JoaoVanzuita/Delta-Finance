import { expect, test, type Page } from '@playwright/test'

/**
 * E2E de privacidade (T146).
 *
 * Prova exportação, desconexão com preservação padrão e exclusão de conta com
 * encerramento de sessão, sempre sem expor segredos na interface.
 */

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

interface State {
  disconnected: boolean
  deleted: boolean
}

async function installStub(page: Page): Promise<State> {
  const state: State = { disconnected: false, deleted: false }

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'csrf-token', domain: '127.0.0.1', path: '/' }])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/csrf') return route.fulfill({ status: 204 })
    if (path === '/auth/me') {
      if (state.deleted) {
        return route.fulfill({
          status: 401,
          contentType: 'application/problem+json',
          body: JSON.stringify({ code: 'UNAUTHENTICATED', detail: 'Sessão encerrada.' }),
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
    }
    if (path === '/integrations/pluggy' && method === 'DELETE') {
      state.disconnected = true
      return route.fulfill({ status: 204 })
    }
    if (path === '/integrations/pluggy') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'integration-1',
          provider: 'MEU_PLUGGY',
          clientIdMasked: state.disconnected ? null : 'clie…ente',
          status: state.disconnected ? 'INCOMPLETE' : 'ACTIVE',
          lastSyncAttemptAt: '2026-10-23T12:00:00Z',
          lastSuccessfulSyncAt: '2026-10-23T12:00:00Z',
          connectionCount: state.disconnected ? 0 : 1,
        }),
      })
    }
    if (path === '/me/export') {
      return route.fulfill({
        status: 200,
        contentType: 'application/zip',
        body: Buffer.from('PK\u0003\u0004delta-finance-export'),
      })
    }
    if (path === '/me/delete' && method === 'POST') {
      state.deleted = true
      return route.fulfill({ status: 202 })
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'NOT_FOUND', detail: 'Não encontrado.' }),
    })
  })

  return state
}

test('exporta, desconecta e exclui a conta sem expor segredos', async ({ page }) => {
  await installStub(page)

  await page.goto('/settings/privacy')
  await expect(page.getByRole('heading', { level: 1, name: 'Privacidade e dados' })).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar exportação' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('delta-finance-export.zip')
  await expect(page.getByText(/Exportação gerada/)).toBeVisible()

  await page.getByLabel(/Excluir também os dados importados/).check()
  await page.getByRole('button', { name: 'Desconectar integração' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: 'Confirmar desconexão' }).click()
  await expect(page.getByText(/Integração desconectada/)).toBeVisible()
  await expect(page.getByText(/Nenhuma integração com credenciais está configurada/)).toBeVisible()
  await expect(page.getByText(/secret|clientSecret|apiKey/i)).toHaveCount(0)

  await page.getByRole('button', { name: 'Excluir minha conta' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByLabel('Senha atual').fill('cavalo correto bateria grampo')
  await page.getByLabel('Digite EXCLUIR para confirmar').fill('EXCLUIR')
  await page.getByLabel(/Entendi que os dados ativos serão removidos/).check()
  await page.getByRole('button', { name: 'Excluir definitivamente' }).click()

  await expect(page).toHaveURL(/\/login$/)
})
