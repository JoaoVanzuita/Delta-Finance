import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { App } from '@/app/App'

describe('App', () => {
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

    expect(await screen.findByRole('heading', { name: 'Módulos disponíveis' })).toBeInTheDocument()
  })
})
