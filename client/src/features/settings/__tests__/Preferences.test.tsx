import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '@/app/ThemeProvider'
import { SettingsLayout } from '@/features/settings/pages/SettingsLayout'
import { PreferencesPage } from '@/features/settings/pages/PreferencesPage'
import { expectNoA11yViolations } from '@/test/axe'
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

function stubAuthenticated() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (String(url).includes('/auth/me')) return jsonResponse(USER)
      return new Response(null, { status: 204 })
    }),
  )
}

function renderPreferences(route = '/settings/preferences') {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={[route]}>
        <ThemeProvider>
          <Routes>
            <Route element={<SettingsLayout />}>
              <Route path="/settings/preferences" element={<PreferencesPage />} />
              <Route path="/settings/integration" element={<h1>Integração</h1>} />
            </Route>
          </Routes>
        </ThemeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('preferências visuais', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-theme-preference')
    document.documentElement.removeAttribute('data-accent')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('exibe tema e paletas e aplica a escolha imediatamente', async () => {
    stubAuthenticated()
    const user = userEvent.setup()
    renderPreferences()

    await waitFor(() =>
      expect(screen.getByRole('heading', { level: 1, name: 'Preferências' })).toBeVisible(),
    )
    await waitFor(() => expect(window.localStorage.getItem('delta.prefs.active')).toBe(USER.id))
    expect(screen.getByRole('radio', { name: /Sistema/ })).toBeChecked()
    expect(screen.getByRole('radio', { name: /Azul/ })).toBeChecked()

    await user.click(screen.getByRole('radio', { name: /Escuro/ }))
    await user.click(screen.getByRole('radio', { name: /Verde/ }))

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(document.documentElement).toHaveAttribute('data-theme-preference', 'dark')
    expect(document.documentElement).toHaveAttribute('data-accent', 'green')
    expect(JSON.parse(window.localStorage.getItem(`delta.prefs.${USER.id}`) ?? '{}')).toEqual({
      theme: 'dark',
      accent: 'green',
    })
  })

  it('oferece prévia sem apresentar valores como dados reais', async () => {
    stubAuthenticated()
    renderPreferences()

    expect(await screen.findByText('Pré-visualização')).toBeVisible()
    expect(screen.getByText('Valores ilustrativos')).toBeVisible()
    expect(screen.getByText('Saldo em contas · exemplo')).toBeVisible()
    expect(screen.getByText(/separadas por usuário autenticado/i)).toBeVisible()
  })

  it('marca a área atual na navegação secundária e preserva URLs próprias', async () => {
    stubAuthenticated()
    renderPreferences()

    const nav = await screen.findByRole('navigation', { name: 'Áreas das configurações' })
    expect(nav.querySelector('a[aria-current="page"]')).toHaveAttribute(
      'href',
      '/settings/preferences',
    )
    expect(screen.getByRole('link', { name: /Integração/ })).toHaveAttribute(
      'href',
      '/settings/integration',
    )
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    stubAuthenticated()
    renderPreferences()

    await screen.findByRole('heading', { level: 1, name: 'Preferências' })
    await expectNoA11yViolations()
  })
})
