import { expect, test, type BrowserContext, type Page } from '@playwright/test'

/**
 * E2E com stub de API (sem backend): valida o comportamento do cliente para
 * dois usuários independentes, route guards e mensagens.
 *
 * O isolamento de dados no servidor é provado por IsolationIT; aqui provamos que
 * a interface não mistura sessões nem expõe o espaço de outro usuário.
 */

interface StubUser {
  readonly id: string
  readonly username: string
  readonly password: string
}

const ALICE: StubUser = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  password: 'cavalo correto bateria grampo',
}

const BOB: StubUser = {
  id: '22222222-2222-2222-2222-222222222222',
  username: 'bob.familia',
  password: 'janela azul montanha serena',
}

const SESSION_COOKIE = '__Host-delta_session'

function userResponse(user: StubUser) {
  return {
    id: user.id,
    username: user.username,
    createdAt: '2026-10-22T12:00:00Z',
  }
}

function problem(status: number, code: string, detail: string) {
  return {
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({
      type: `urn:delta-finance:error:${code.toLowerCase()}`,
      title: status === 401 ? 'Unauthorized' : 'Error',
      status,
      code,
      detail,
      requestId: 'req-e2e',
    }),
  }
}

interface ApiStub {
  revokeSessions(): void
}

async function installApiStub(context: BrowserContext): Promise<ApiStub> {
  const sessions = new Set<string>()
  const passwords = new Map([
    [ALICE.username, ALICE.password],
    [BOB.username, BOB.password],
  ])

  await context.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const cookieHeader = request.headers()['cookie'] ?? ''
    const sessionValue = /__Host-delta_session=([^;]+)/.exec(cookieHeader)?.[1]
    const sessionUser =
      sessionValue && sessions.has(sessionValue)
        ? [ALICE, BOB].find((candidate) => candidate.username === sessionValue.split(':')[0])
        : undefined

    if (path === '/auth/csrf') {
      await route.fulfill({
        status: 204,
        headers: {
          'Set-Cookie': 'XSRF-TOKEN=csrf-token; Path=/; Secure; SameSite=Lax',
        },
      })
      return
    }

    if (path === '/auth/login' && request.method() === 'POST') {
      const body = request.postDataJSON() as { username: string; password: string }
      const user = [ALICE, BOB].find((candidate) => candidate.username === body.username)
      if (!user || passwords.get(body.username) !== body.password) {
        await route.fulfill(problem(401, 'INVALID_CREDENTIALS', 'Usuário ou senha inválidos.'))
        return
      }
      const value = `${user.username}:${Date.now()}`
      sessions.add(value)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Set-Cookie': `${SESSION_COOKIE}=${value}; Path=/; Secure; HttpOnly; SameSite=Lax`,
        },
        body: JSON.stringify(userResponse(user)),
      })
      return
    }

    if (path === '/auth/me') {
      if (!sessionUser) {
        await route.fulfill(problem(401, 'UNAUTHENTICATED', 'Autenticação necessária.'))
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(userResponse(sessionUser)),
      })
      return
    }

    if (path === '/auth/logout' && request.method() === 'POST') {
      if (sessionValue) {
        sessions.delete(sessionValue)
      }
      await route.fulfill({
        status: 204,
        headers: {
          'Set-Cookie': `${SESSION_COOKIE}=; Path=/; Max-Age=0`,
        },
      })
      return
    }

    if (path === '/auth/password' && request.method() === 'PUT') {
      if (!sessionUser) {
        await route.fulfill(problem(401, 'UNAUTHENTICATED', 'Autenticação necessária.'))
        return
      }
      const body = request.postDataJSON() as { currentPassword: string; newPassword: string }
      if (passwords.get(sessionUser.username) !== body.currentPassword) {
        await route.fulfill(problem(422, 'VALIDATION_FAILED', 'Senha atual incorreta.'))
        return
      }
      passwords.set(sessionUser.username, body.newPassword)
      await route.fulfill({ status: 204 })
      return
    }

    if (path === '/auth/register' && request.method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(userResponse(ALICE)),
      })
      return
    }

    await route.fulfill(problem(404, 'NOT_FOUND', 'Recurso não encontrado.'))
  })

  return {
    revokeSessions() {
      sessions.clear()
    },
  }
}

async function login(page: Page, user: StubUser): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Nome de usuário').fill(user.username)
  await page.getByLabel('Senha').fill(user.password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
  await page.goto('/account')
  await expect(page.getByRole('heading', { name: 'Configurações da conta' })).toBeVisible()
}

test.describe('isolamento entre dois usuários', () => {
  test('cada sessão vê apenas o próprio espaço e o logout não afeta o outro', async ({
    browser,
  }) => {
    const baseURL = test.info().project.use.baseURL as string
    const aliceContext = await browser.newContext({ baseURL })
    const bobContext = await browser.newContext({ baseURL })
    await installApiStub(aliceContext)
    await installApiStub(bobContext)

    const alicePage = await aliceContext.newPage()
    const bobPage = await bobContext.newPage()

    await login(alicePage, ALICE)
    await expect(alicePage.getByText(ALICE.username, { exact: true })).toBeVisible()
    await expect(alicePage.getByText(BOB.username, { exact: true })).toHaveCount(0)

    await login(bobPage, BOB)
    await expect(bobPage.getByText(BOB.username, { exact: true })).toBeVisible()
    await expect(bobPage.getByText(ALICE.username, { exact: true })).toHaveCount(0)

    await alicePage.getByRole('button', { name: 'Sair' }).click()
    await expect(alicePage.getByRole('heading', { name: 'Entrar' })).toBeVisible()

    await bobPage.reload()
    await expect(bobPage.getByRole('heading', { name: 'Configurações da conta' })).toBeVisible()
    await expect(bobPage.getByText(BOB.username, { exact: true })).toBeVisible()

    await aliceContext.close()
    await bobContext.close()
  })

  test('rota protegida exige login e rota inexistente mostra 404', async ({ page }) => {
    const context = page.context()
    await installApiStub(context)

    await page.goto('/account')
    await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()

    await page.goto('/rota-que-nao-existe')
    await expect(page.getByRole('heading', { name: 'Não encontramos este endereço' })).toBeVisible()
  })

  test('login inválido mantém mensagem genérica', async ({ page }) => {
    await installApiStub(page.context())

    await page.goto('/login')
    await page.getByLabel('Nome de usuário').fill(ALICE.username)
    await page.getByLabel('Senha').fill('senha errada bem longa')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByRole('alert')).toContainText('Usuário ou senha inválidos.')
    await expect(page.getByRole('alert')).not.toContainText(ALICE.username)
  })

  test('sessão revogada no servidor volta ao login', async ({ page }) => {
    const stub = await installApiStub(page.context())
    await login(page, ALICE)

    stub.revokeSessions()
    await page.goto('/account')

    await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  })

  test('troca de senha confirma sucesso e mantém a sessão', async ({ page }) => {
    await installApiStub(page.context())
    await login(page, ALICE)

    await page.getByLabel('Senha atual').fill(ALICE.password)
    await page.getByLabel('Nova senha', { exact: true }).fill('montanha verde silencio longo')
    await page.getByLabel('Confirmar nova senha').fill('montanha verde silencio longo')
    await page.getByRole('button', { name: 'Salvar nova senha' }).click()

    await expect(page.getByRole('status')).toContainText('Senha alterada')
    await expect(page.getByRole('heading', { name: 'Configurações da conta' })).toBeVisible()
  })
})
