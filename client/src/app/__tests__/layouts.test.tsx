import { render, screen, within } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppProviders } from '@/app/providers'
import { AuthenticatedLayout, HomeRoute } from '@/app/layouts'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { createTestQueryClient } from '@/test/render'

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

function stubUnauthenticated() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.resolve(new Response(null, { status: 401 }))),
  )
}

function stubAuthenticated() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => Promise.resolve(jsonResponse(USER))),
  )
}

function renderHomeRoute() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route
            path="/"
            element={
              <PublicLayout>
                <HomeRoute />
              </PublicLayout>
            }
          />
          <Route path="/dashboard" element={<h1>Dashboard</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function renderAuthenticatedLayout() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route element={<AuthenticatedLayout />}>
            <Route path="/dashboard" element={<h1>Dashboard</h1>} />
          </Route>
          <Route path="/login" element={<h1>Entrar</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('layouts público e autenticado', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('visitante vê a Home pública sem a sidebar autenticada', async () => {
    stubUnauthenticated()
    renderHomeRoute()

    expect(await screen.findByRole('heading', { level: 1, name: 'Delta Finance' })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Navegação pública' })).toBeInTheDocument()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
  })

  it('usuário autenticado em / vai para o Dashboard', async () => {
    stubAuthenticated()
    renderHomeRoute()

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Delta Finance' })).not.toBeInTheDocument()
  })

  it('rota autenticada renderiza o AppShell agrupado', async () => {
    stubAuthenticated()
    renderAuthenticatedLayout()

    expect(await screen.findByRole('navigation', { name: 'Navegação principal' })).toBeVisible()
    expect(screen.getByText('Meu dinheiro')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Contas' })).toBeInTheDocument()
  })

  it('visitante em rota protegida é enviado ao login sem sidebar', async () => {
    stubUnauthenticated()
    renderAuthenticatedLayout()

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeVisible()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
  })
})

describe('composição real de rotas', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  async function renderRealRoute(route: string) {
    const { createMemoryRouter, RouterProvider } = await import('react-router')
    const { routes } = await import('@/app/router')
    const router = createMemoryRouter(routes, { initialEntries: [route] })
    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    )
    return router
  }

  it('login usa layout público sem navegação autenticada', async () => {
    stubUnauthenticated()
    await renderRealRoute('/login')

    expect(await screen.findByRole('heading', { level: 1, name: 'Entrar' })).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Navegação pública' })).toBeInTheDocument()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
  })

  it('404 de visitante usa o layout público', async () => {
    stubUnauthenticated()
    await renderRealRoute('/endereco-inexistente')

    expect(
      await screen.findByRole('heading', { name: 'Não encontramos este endereço' }),
    ).toBeVisible()
    expect(screen.getByRole('navigation', { name: 'Navegação pública' })).toBeInTheDocument()
    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
  })

  it('404 autenticado usa o AppShell e oferece caminho de volta ao Dashboard', async () => {
    stubAuthenticated()
    await renderRealRoute('/endereco-inexistente')

    expect(
      await screen.findByRole('heading', { name: 'Não encontramos este endereço' }),
    ).toBeVisible()
    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir para o Dashboard' })).toHaveAttribute(
      'href',
      '/dashboard',
    )
  })

  it('/settings leva às preferências com navegação secundária própria', async () => {
    stubAuthenticated()
    await renderRealRoute('/settings')

    expect(await screen.findByRole('heading', { level: 1, name: 'Preferências' })).toBeVisible()
    const nav = screen.getByRole('navigation', { name: 'Áreas das configurações' })
    expect(nav.querySelector('a[aria-current="page"]')).toHaveAttribute(
      'href',
      '/settings/preferences',
    )
    expect(within(nav).getByRole('link', { name: /Conta/ })).toHaveAttribute('href', '/account')
  })
})
