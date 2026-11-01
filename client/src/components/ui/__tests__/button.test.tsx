import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Button } from '@/components/ui/button'
import { IconButton } from '@/components/ui/icon-button'

describe('Button', () => {
  it('dispara a ação e respeita o estado desabilitado', async () => {
    const onClick = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(<Button onClick={onClick}>Salvar</Button>)
    expect(screen.getByRole('button', { name: 'Salvar' })).toHaveClass('text-on-primary')

    await user.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(onClick).toHaveBeenCalledTimes(1)

    rerender(
      <Button onClick={onClick} disabled>
        Salvar
      </Button>,
    )
    await user.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('expõe estado de carregamento com rótulo preservado', () => {
    render(
      <Button loading loadingLabel="Salvando…">
        Salvar alterações
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Salvando…' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('IconButton exige nome acessível e ícone decorativo', () => {
    render(
      <IconButton label="Abrir navegação">
        <svg aria-hidden="true" />
      </IconButton>,
    )
    expect(screen.getByRole('button', { name: 'Abrir navegação' })).toHaveAttribute(
      'title',
      'Abrir navegação',
    )
  })
})
