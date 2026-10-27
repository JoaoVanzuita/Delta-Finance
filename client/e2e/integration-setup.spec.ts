import { expect, test, type Page } from '@playwright/test'

/**
 * E2E da configuração da integração (stub de API).
 *
 * Prova que o Client Secret é write-only, que o itemId é mascarado, que o
 * limite é exibido com data e que não existe campo/instrução de Connect Token.
 */

interface StubState {
  configured: boolean
  invalidNextUpdate: boolean
  connections: Array<Record<string, unknown>>
}

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

async function installStub(page: Page): Promise<StubState> {
  const state: StubState = { configured: false, invalidNextUpdate: false, connections: [] }

  await page.context().addCookies([
    {
      name: 'XSRF-TOKEN',
      value: 'csrf-token',
      domain: '127.0.0.1',
      path: '/',
    },
  ])

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/csrf') {
      await route.fulfill({ status: 204 })
      return
    }
    if (path === '/auth/me' || path === '/auth/login') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(USER),
      })
      return
    }
    if (path === '/auth/logout') {
      await route.fulfill({ status: 204 })
      return
    }

    if (path === '/integrations/pluggy' && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'integration-1',
          provider: 'MEU_PLUGGY',
          clientIdMasked: state.configured ? 'clie…lice' : undefined,
          status: state.configured ? 'ACTIVE' : 'INCOMPLETE',
          connectionCount: state.connections.length,
          connectionLimit: 5,
          connectionLimitCheckedAt: '2026-10-22',
        }),
      })
      return
    }
    if (path === '/integrations/pluggy' && method === 'PUT') {
      if (state.invalidNextUpdate) {
        state.invalidNextUpdate = false
        await route.fulfill({
          status: 502,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'urn:delta-finance:error:provider_auth_failed',
            title: 'Bad Gateway',
            status: 502,
            code: 'PROVIDER_AUTH_FAILED',
            detail: 'Não foi possível validar as credenciais com o provedor.',
          }),
        })
        return
      }
      state.configured = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'integration-1',
          provider: 'MEU_PLUGGY',
          clientIdMasked: 'clie…lice',
          status: 'INCOMPLETE',
          connectionCount: state.connections.length,
          connectionLimit: 5,
          connectionLimitCheckedAt: '2026-10-22',
        }),
      })
      return
    }
    if (path === '/integrations/pluggy/validate' && method === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          valid: true,
          message: 'Credenciais válidas.',
          itemValidated: false,
        }),
      })
      return
    }
    if (path === '/connections' && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: state.connections, hasMore: false }),
      })
      return
    }
    if (path === '/connections' && method === 'POST') {
      const connection = {
        id: 'connection-1',
        itemIdMasked: 'item…-001',
        institutionName: 'Banco Alfa',
        status: 'SYNCED',
        consentExpiresAt: '2027-10-22T12:00:00Z',
        lastSyncAt: '2026-10-22T12:00:00Z',
        accountCount: 2,
      }
      state.connections = [connection]
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(connection),
      })
      return
    }
    if (path.startsWith('/connections/') && method === 'DELETE') {
      state.connections = []
      await route.fulfill({ status: 204 })
      return
    }
    if (path === '/accounts' && method === 'GET') {
      const items = state.configured
        ? [
            {
              id: 'acc-1',
              type: 'BANK',
              subtype: 'CHECKING_ACCOUNT',
              origin: 'IMPORTED',
              institutionName: 'Banco Alfa',
              alias: 'Conta Alfa',
              currency: 'BRL',
              currentBalance: '1500.00',
              status: 'ACTIVE',
              lastSyncAt: '2026-10-22T12:00:00Z',
            },
            {
              id: 'acc-2',
              type: 'CREDIT',
              subtype: 'CREDIT_CARD',
              origin: 'IMPORTED',
              institutionName: 'Banco Alfa',
              alias: 'Cartão Alfa',
              currency: 'BRL',
              currentBalance: '-250.00',
              status: 'ACTIVE',
              lastSyncAt: '2026-10-22T12:00:00Z',
              cardProfile: {
                accountId: 'acc-2',
                closingDay: 5,
                dueDay: 15,
                creditLimit: '5000.00',
                creditBalance: '0.00',
              },
            },
            {
              id: 'acc-3',
              type: 'BANK',
              origin: 'IMPORTED',
              institutionName: 'Banco Dólar',
              alias: 'Conta Dólar',
              currency: 'USD',
              currentBalance: '10.00',
              status: 'ACTIVE',
            },
          ]
        : []
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items }),
      })
      return
    }
    if (path === '/transactions' && method === 'GET') {
      const items = state.configured
        ? [
            {
              id: 'tx-1',
              accountId: 'acc-1',
              source: 'IMPORTED',
              type: 'DEBIT',
              status: 'POSTED',
              postedOn: '2026-10-20',
              amount: '-12.50',
              currency: 'BRL',
              description: 'Padaria',
            },
            {
              id: 'tx-2',
              accountId: 'acc-1',
              source: 'IMPORTED',
              type: 'DEBIT',
              status: 'PENDING',
              postedOn: '2026-10-21',
              amount: '-20.00',
              currency: 'BRL',
              description: 'Farmácia',
            },
          ]
        : []
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items, hasMore: false }),
      })
      return
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'urn:delta-finance:error:not_found',
        title: 'Not Found',
        status: 404,
        code: 'NOT_FOUND',
      }),
    })
  })

  return state
}

test.describe('configuração da integração', () => {
  test('salva credenciais, mascara itemId e exibe limite datado', async ({ page }) => {
    const state = await installStub(page)
    await page.goto('/settings/integration')

    await expect(page.getByRole('heading', { name: 'Meu Pluggy' })).toBeVisible()
    await expect(page.getByText(/Connect Token/i)).toHaveCount(0)

    await page.getByLabel('Client ID').fill('client-alice')
    await page.getByLabel('Client Secret').fill('secret-alice')
    await page.getByRole('button', { name: 'Salvar credenciais' }).click()

    await expect(page.getByText('Credenciais validadas e salvas.')).toBeVisible()
    await expect(page.getByText('clie…lice')).toBeVisible()
    await expect(page.getByLabel('Client Secret')).toHaveValue('')
    await expect(page.getByText(/0 de 5 conexões/)).toBeVisible()

    await page.getByLabel('itemId da conexão').fill('item-1')
    await page.getByRole('button', { name: 'Adicionar conexão' }).click()

    await expect(page.getByText('Banco Alfa')).toBeVisible()
    await expect(page.getByText(/item…-001/)).toBeVisible()
    await expect(page.getByText(/1 de 5 conexões/)).toBeVisible()
    expect(state.connections).toHaveLength(1)
  })

  test('valida credenciais sem salvar', async ({ page }) => {
    await installStub(page)
    await page.goto('/settings/integration')

    await page.getByLabel('Client ID').fill('client-alice')
    await page.getByLabel('Client Secret').fill('secret-alice')
    await page.getByRole('button', { name: 'Validar sem salvar' }).click()

    await expect(
      page.getByText('Credenciais válidas. Salve para ativar a integração.'),
    ).toBeVisible()
  })

  test('mantém a configuração anterior quando a atualização é inválida', async ({ page }) => {
    const state = await installStub(page)
    await page.goto('/settings/integration')

    await page.getByLabel('Client ID').fill('client-alice')
    await page.getByLabel('Client Secret').fill('secret-alice')
    await page.getByRole('button', { name: 'Salvar credenciais' }).click()
    await expect(page.getByText('clie…lice')).toBeVisible()

    state.invalidNextUpdate = true
    await page.getByLabel('Client ID').fill('client-invalido')
    await page.getByLabel('Client Secret').fill('segredo-invalido')
    await page.getByRole('button', { name: 'Salvar credenciais' }).click()

    await expect(
      page.getByText('Não foi possível validar as credenciais com o provedor.'),
    ).toBeVisible()
    await expect(page.getByText('clie…lice')).toBeVisible()
  })

  test('lista contas e transações importadas com sinais de moeda e pendência', async ({ page }) => {
    await installStub(page)
    await page.goto('/settings/integration')
    await page.getByLabel('Client ID').fill('client-alice')
    await page.getByLabel('Client Secret').fill('secret-alice')
    await page.getByRole('button', { name: 'Salvar credenciais' }).click()
    await expect(page.getByText('Credenciais validadas e salvas.')).toBeVisible()

    await page.goto('/accounts')
    await expect(page.getByRole('heading', { name: 'Contas e cartões' })).toBeVisible()
    await expect(page.getByText('Conta Alfa')).toBeVisible()
    await expect(page.getByText('Cartão Alfa')).toBeVisible()
    await expect(page.getByText(/Moeda USD/)).toBeVisible()
    await expect(page.getByText(/dia 5 \/ dia 15/)).toBeVisible()

    await page.goto('/transactions')
    await expect(page.getByRole('heading', { name: 'Transações', exact: true })).toBeVisible()
    await expect(page.getByText('Padaria')).toBeVisible()
    await expect(page.getByText('Farmácia')).toBeVisible()
    await expect(
      page.getByRole('listitem').filter({ hasText: 'Farmácia' }).getByText('Pendente'),
    ).toBeVisible()
  })
})
