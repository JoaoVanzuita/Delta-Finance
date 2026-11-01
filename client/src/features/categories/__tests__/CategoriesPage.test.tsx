import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { CategoriesPage } from '@/features/categories/pages/CategoriesPage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

const CATEGORIES = {
  items: [
    {
      id: 'cat-moradia',
      name: 'Moradia',
      kind: 'EXPENSE',
      predefined: true,
      active: true,
    },
    {
      id: 'cat-feira',
      name: 'Feira',
      kind: 'EXPENSE',
      predefined: false,
      active: true,
    },
    {
      id: 'cat-salario',
      name: 'Salário',
      kind: 'INCOME',
      predefined: true,
      active: true,
    },
    {
      id: 'cat-transfer',
      name: 'Transferências',
      kind: 'TRANSFER',
      predefined: true,
      active: true,
    },
  ],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

function stubApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init: RequestInit = {}) => {
      if (url.includes('/categories') && (!init.method || init.method === 'GET')) {
        return jsonResponse(CATEGORIES)
      }
      return jsonResponse({}, 404)
    }),
  )
}

describe('categorias', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste; path=/'
    stubApi()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('agrupa por tipo, busca localmente e explica o impacto de desativar', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CategoriesPage />, { route: '/categories' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Categorias' })).toBeInTheDocument()
    expect(await screen.findByText('Moradia')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Despesas' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Receitas' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Transferências' })).toBeInTheDocument()
    expect(screen.getAllByText('Predefinida').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Personalizada').length).toBeGreaterThan(0)

    await user.type(screen.getByLabelText('Buscar categoria'), 'feira')
    expect(screen.getByText('Feira')).toBeInTheDocument()
    expect(screen.queryByText('Moradia')).not.toBeInTheDocument()
    expect(screen.queryByText('Salário')).not.toBeInTheDocument()

    const row = screen.getByText('Feira').closest('li')
    expect(row).not.toBeNull()
    await user.click(within(row as HTMLElement).getByRole('button', { name: 'Desativar' }))

    const dialog = await screen.findByRole('dialog', { name: /Desativar “Feira”/ })
    expect(within(dialog).getByText(/Nenhuma transação é apagada/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Reatribuir transações para')).toBeInTheDocument()
    expect(
      within(dialog).getByRole('button', { name: 'Confirmar desativação' }),
    ).toBeInTheDocument()
  })

  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(<CategoriesPage />, { route: '/categories' })
    await screen.findByText('Moradia')
    await expectNoA11yViolations()
  })
})
