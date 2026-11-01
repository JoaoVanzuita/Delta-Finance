import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AppShell } from '@/components/layout/AppShell'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

describe('AppShell', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('expõe skip link, navegação agrupada e landmark principal', () => {
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute(
      'href',
      '#conteudo',
    )
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveAttribute('id', 'conteudo')
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
  })

  it('organiza os destinos em cinco grupos visíveis no desktop', () => {
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    for (const group of ['Visão geral', 'Meu dinheiro', 'Planejar', 'Organizar', 'Configurações']) {
      expect(screen.getByText(group)).toBeInTheDocument()
    }
    const navigation = screen.getByRole('navigation', { name: 'Navegação principal' })
    expect(navigation.querySelectorAll('a')).toHaveLength(13)
    expect(screen.getByRole('link', { name: 'Preferências' })).toHaveAttribute(
      'href',
      '/settings/preferences',
    )
  })

  it('mostra indicador de ação necessária quando disponível', () => {
    renderWithProviders(
      <AppShell actionCounts={{ '/transactions': 3 }}>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    expect(screen.getByText('Ação necessária:')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('usa drawer em mobile e devolve o foco ao abrir e fechar', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    const user = userEvent.setup()
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    expect(
      screen.queryByRole('navigation', { name: 'Navegação principal' }),
    ).not.toBeInTheDocument()
    const trigger = screen.getByRole('button', { name: 'Abrir navegação' })
    expect(trigger).toHaveAttribute('aria-expanded', 'false')

    await user.click(trigger)
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('dialog', { name: 'Navegação' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
  })

  it('fecha o drawer ao navegar', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    const user = userEvent.setup()
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    await user.click(screen.getByRole('button', { name: 'Abrir navegação' }))
    await user.click(screen.getByRole('link', { name: 'Contas' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
      { route: '/dashboard' },
    )

    await expectNoA11yViolations()
  })
})
