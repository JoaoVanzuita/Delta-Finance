import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, Route, RouterProvider, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ProtectedRoute } from '@/app/ProtectedRoute'
import { AccountSettingsPage } from '@/features/auth/pages/AccountSettingsPage'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { SessionExpiredBanner } from '@/features/auth/components/SessionExpiredBanner'
import { markSessionCleared, UNAUTHENTICATED_EVENT } from '@/lib/api/client'
import { createTestQueryClient, renderWithProviders } from '@/test/render'

const USER = {
  id: '11111111-1111-1111-1111-111111111111',
  username: 'alice.familia',
  createdAt: '2026-10-22T12:00:00Z',
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function problemResponse(
  status: number,
  code: string,
  detail: string,
  errors?: Array<{ field: string; message: string }>,
): Response {
  return new Response(
    JSON.stringify({
      type: `urn:delta-finance:error:${code.toLowerCase()}`,
      title: status === 401 ? 'Unauthorized' : 'Unprocessable Entity',
      status,
      code,
      detail,
      requestId: 'req-test',
      errors,
    }),
    {
      status,
      headers: { 'content-type': 'application/problem+json' },
    },
  )
}

type FetchHandler = (url: string, init: RequestInit) => Response | Promise<Response>

function stubFetch(handler: FetchHandler) {
  const mock = vi.fn(handler)
  vi.stubGlobal('fetch', mock)
  return mock
}

function renderLoginPage(options: { from?: string } = {}) {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/dashboard', element: <h1>Dashboard</h1> },
      { path: '/accounts', element: <h1>Contas</h1> },
      { path: '/account', element: <h1>Conta</h1> },
    ],
    {
      initialEntries: [
        {
          pathname: '/login',
          state: options.from ? { from: options.from } : undefined,
        },
      ],
    },
  )
  render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return router
}

function renderRoutes(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<h1>Entrar</h1>} />
      <Route
        path="/account"
        element={
          <ProtectedRoute>
            <AccountSettingsPage />
          </ProtectedRoute>
        }
      />
    </Routes>,
    { route },
  )
}

describe('fluxo de autenticação', () => {
  beforeEach(() => {
    markSessionCleared()
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('valida a política de senha no cadastro antes de chamar a API', async () => {
    const fetchMock = stubFetch(() => jsonResponse(USER, 201))
    const user = userEvent.setup()
    renderRoutes('/register')

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'curta123456')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(
      (await screen.findAllByText('A senha deve ter pelo menos 12 caracteres.')).length,
    ).toBeGreaterThan(0)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveFocus())
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('exibe erro de senha comum vindo do servidor com resumo focável', async () => {
    const fetchMock = stubFetch((url, init) => {
      if (url.endsWith('/auth/register') && init.method === 'POST') {
        return problemResponse(422, 'VALIDATION_FAILED', 'Um ou mais campos são inválidos.', [
          { field: 'password', message: 'Escolha uma senha menos comum.' },
        ])
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderRoutes('/register')

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'unbelievable')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect((await screen.findAllByText('Escolha uma senha menos comum.')).length).toBeGreaterThan(0)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveFocus())

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(init.credentials).toBe('include')
    expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('token-de-teste')
  })

  it('após cadastro bem-sucedido navega para o login', async () => {
    stubFetch(() => jsonResponse(USER, 201))
    const user = userEvent.setup()
    renderRoutes('/register')

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeVisible()
  })

  it('preserva o deep link original do cadastro até o login', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/register') && init.method === 'POST') return jsonResponse(USER, 201)
      if (url.endsWith('/auth/login') && init.method === 'POST') return jsonResponse(USER)
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const router = createMemoryRouter(
      [
        { path: '/register', element: <RegisterPage /> },
        { path: '/login', element: <LoginPage /> },
        { path: '/dashboard', element: <h1>Dashboard</h1> },
        { path: '/accounts', element: <h1>Contas</h1> },
      ],
      { initialEntries: [{ pathname: '/register', state: { from: '/accounts' } }] },
    )
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeVisible()
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Contas' })).toBeVisible()
  })

  it('após login bem-sucedido navega para o Dashboard por padrão', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/login') && init.method === 'POST') {
        return jsonResponse(USER)
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  it('preserva o deep link original em state.from após autenticar', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/login') && init.method === 'POST') {
        return jsonResponse(USER)
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderLoginPage({ from: '/accounts' })

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Contas' })).toBeVisible()
  })

  it('ignora destino externo em state.from e usa o Dashboard', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/login') && init.method === 'POST') {
        return jsonResponse(USER)
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderLoginPage({ from: '//example.com/phishing' })

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'cavalo correto bateria grampo')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeVisible()
  })

  it('mostra mensagem genérica de credenciais inválidas no login', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/login') && init.method === 'POST') {
        return problemResponse(401, 'INVALID_CREDENTIALS', 'Usuário ou senha inválidos.')
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'senha errada bem longa')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Usuário ou senha inválidos.')
    expect(alert).not.toHaveTextContent('alice.familia')
  })

  it('explica o bloqueio temporário após muitas tentativas', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/login') && init.method === 'POST') {
        return new Response(
          JSON.stringify({
            type: 'urn:delta-finance:error:rate_limited',
            title: 'Too Many Requests',
            status: 429,
            code: 'RATE_LIMITED',
            detail: 'Muitas tentativas de login.',
          }),
          {
            status: 429,
            headers: { 'content-type': 'application/problem+json', 'Retry-After': '900' },
          },
        )
      }
      return problemResponse(404, 'NOT_FOUND', 'Não encontrado.')
    })
    const user = userEvent.setup()
    renderLoginPage()

    await user.type(screen.getByLabelText('Nome de usuário'), 'alice.familia')
    await user.type(screen.getByLabelText('Senha'), 'senha errada bem longa')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText(/Tente novamente em 15 minuto/)).toBeVisible()
  })

  it('redireciona visitante anônimo de rota protegida para o login', async () => {
    stubFetch(() => problemResponse(401, 'UNAUTHENTICATED', 'Autenticação necessária.'))
    renderRoutes('/account')

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeVisible()
    expect(screen.queryByText('Configurações da conta')).not.toBeInTheDocument()
  })

  it('permite acesso à rota protegida quando a sessão existe', async () => {
    stubFetch(() => jsonResponse(USER))
    renderRoutes('/account')

    expect(await screen.findByRole('heading', { name: 'Configurações da conta' })).toBeVisible()
    expect(screen.getByText('alice.familia')).toBeVisible()
  })

  it('valida a política de senha na troca de senha sem chamar a API', async () => {
    const fetchMock = stubFetch(() => jsonResponse(USER))
    const user = userEvent.setup()
    renderRoutes('/account')

    await screen.findByRole('heading', { name: 'Configurações da conta' })
    await user.type(screen.getByLabelText('Senha atual'), 'senha atual longa')
    await user.type(screen.getByLabelText('Nova senha'), 'curta')
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'curta')
    await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }))

    expect(
      (await screen.findAllByText('A senha deve ter pelo menos 12 caracteres.')).length,
    ).toBeGreaterThan(0)
    expect(fetchMock.mock.calls.filter(([, init]) => init.method === 'PUT')).toHaveLength(0)
  })

  it('exibe erro do servidor na troca de senha', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/password') && init.method === 'PUT') {
        return problemResponse(422, 'VALIDATION_FAILED', 'Um ou mais campos são inválidos.', [
          { field: 'newPassword', message: 'Escolha uma senha menos comum.' },
        ])
      }
      return jsonResponse(USER)
    })
    const user = userEvent.setup()
    renderRoutes('/account')

    await screen.findByRole('heading', { name: 'Configurações da conta' })
    await user.type(screen.getByLabelText('Senha atual'), 'cavalo correto bateria grampo')
    await user.type(screen.getByLabelText('Nova senha'), 'unbelievable')
    await user.type(screen.getByLabelText('Confirmar nova senha'), 'unbelievable')
    await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }))

    expect((await screen.findAllByText('Escolha uma senha menos comum.')).length).toBeGreaterThan(0)
  })

  it('encerra a sessão e volta ao login ao sair', async () => {
    stubFetch((url, init) => {
      if (url.endsWith('/auth/logout') && init.method === 'POST') {
        return new Response(null, { status: 204 })
      }
      return jsonResponse(USER)
    })
    const user = userEvent.setup()
    renderRoutes('/account')

    await screen.findByRole('heading', { name: 'Configurações da conta' })
    await user.click(screen.getByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeVisible()
  })

  it('mostra aviso de sessão expirada ao receber 401 de sessão conhecida', async () => {
    renderWithProviders(<SessionExpiredBanner />)

    window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT))

    expect(await screen.findByText(/Sua sessão expirou/)).toBeVisible()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})
