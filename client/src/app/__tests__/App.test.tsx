import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '@/app/App'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response(null, { status: 401 }))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renderiza a página inicial com o nome do produto', async () => {
    render(<App />)

    const heading = await screen.findByRole('heading', {
      level: 1,
      name: 'Delta Finance',
    })

    expect(heading).toBeInTheDocument()
  })

  it('apresenta os módulos disponíveis na página inicial', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'O que você encontra' })).toBeInTheDocument()
  })
})
