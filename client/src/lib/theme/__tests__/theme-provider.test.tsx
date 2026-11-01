import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '@/app/ThemeProvider'
import { useTheme } from '@/app/theme-context'
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

function Harness() {
  const { theme, accent, resolvedTheme, namespace, setTheme, setAccent } = useTheme()
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <span data-testid="accent">{accent}</span>
      <span data-testid="resolved">{resolvedTheme}</span>
      <span data-testid="namespace">{namespace}</span>
      <button type="button" onClick={() => setTheme('dark')}>
        Escuro
      </button>
      <button type="button" onClick={() => setTheme('light')}>
        Claro
      </button>
      <button type="button" onClick={() => setAccent('green')}>
        Verde
      </button>
    </div>
  )
}

function renderThemeProvider() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <ThemeProvider>
          <Harness />
        </ThemeProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('infraestrutura de temas', () => {
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

  it('persiste tema e paleta no namespace guest e reaplica ao recarregar', async () => {
    stubUnauthenticated()
    const user = userEvent.setup()
    const first = renderThemeProvider()

    await waitFor(() => expect(screen.getByTestId('namespace')).toHaveTextContent('guest'))
    await user.click(screen.getByRole('button', { name: 'Escuro' }))
    await user.click(screen.getByRole('button', { name: 'Verde' }))

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(document.documentElement).toHaveAttribute('data-accent', 'green')
    expect(document.documentElement).toHaveAttribute('data-theme-preference', 'dark')
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(JSON.parse(window.localStorage.getItem('delta.prefs.guest') ?? '{}')).toEqual({
      theme: 'dark',
      accent: 'green',
    })

    first.unmount()
    renderThemeProvider()

    await waitFor(() => expect(screen.getByTestId('theme')).toHaveTextContent('dark'))
    expect(screen.getByTestId('accent')).toHaveTextContent('green')
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })

  it('usa namespace do usuário e não compartilha preferências entre contas', async () => {
    stubAuthenticated()
    const user = userEvent.setup()
    renderThemeProvider()

    await waitFor(() => expect(screen.getByTestId('namespace')).toHaveTextContent(USER.id))
    await user.click(screen.getByRole('button', { name: 'Escuro' }))

    expect(window.localStorage.getItem(`delta.prefs.${USER.id}`)).not.toBeNull()
    expect(window.localStorage.getItem('delta.prefs.guest')).toBeNull()
    expect(window.localStorage.getItem('delta.prefs.active')).toBe(USER.id)
  })

  it('resolve o tema do sistema para dark quando o sistema prefere escuro', async () => {
    stubUnauthenticated()
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('prefers-color-scheme: dark'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    renderThemeProvider()

    await waitFor(() => expect(screen.getByTestId('theme')).toHaveTextContent('system'))
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark')
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })

  it('a troca de paleta não altera os estados semânticos', async () => {
    stubUnauthenticated()
    const user = userEvent.setup()
    renderThemeProvider()

    await waitFor(() => expect(screen.getByTestId('namespace')).toHaveTextContent('guest'))
    await user.click(screen.getByRole('button', { name: 'Verde' }))

    expect(document.documentElement).toHaveAttribute('data-accent', 'green')
    // A paleta é aplicada por atributo; os tokens semânticos de receita/despesa
    // continuam declarados independentemente de `data-accent` no CSS.
    expect(document.documentElement).not.toHaveAttribute('data-income')
  })
})
