import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { AppShell } from '@/components/layout/AppShell'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

describe('AppShell', () => {
  it('expõe skip link, navegação e landmark principal', () => {
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
    )

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute(
      'href',
      '#conteudo',
    )
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveAttribute('id', 'conteudo')
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(
      <AppShell>
        <h1>Conteúdo</h1>
      </AppShell>,
    )

    await expectNoA11yViolations()
  })
})
