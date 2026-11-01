import { useState } from 'react'

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { Drawer } from '@/components/ui/drawer'
import { expectNoA11yViolations } from '@/test/axe'

function DrawerHarness() {
  const [open, setOpen] = useState(false)
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        Abrir menu
      </button>
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        title="Navegação"
        description="Áreas do Delta Finance"
        side="left"
      >
        <nav aria-label="Navegação do drawer">
          <a href="#contas">Contas</a>
        </nav>
      </Drawer>
    </div>
  )
}

describe('Drawer acessível', () => {
  it('abre, expõe nome acessível e libera o fundo ao fechar', async () => {
    const user = userEvent.setup()
    const { container } = render(<DrawerHarness />)
    const trigger = screen.getByRole('button', { name: 'Abrir menu' })

    await user.click(trigger)

    const drawer = screen.getByRole('dialog', { name: 'Navegação' })
    expect(drawer).toHaveTextContent('Áreas do Delta Finance')
    expect(document.body.style.overflow).toBe('hidden')
    expect(container).toHaveAttribute('inert')

    await expectNoA11yViolations()

    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
    expect(container).not.toHaveAttribute('inert')
  })
})
