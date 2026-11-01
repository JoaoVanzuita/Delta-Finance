import { useRef, useState } from 'react'

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { Dialog } from '@/components/ui/dialog'
import { expectNoA11yViolations } from '@/test/axe'

function DialogHarness({ busy = false }: { busy?: boolean }) {
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        Abrir diálogo
      </button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Corrigir transação"
        description="A correção preserva o valor original."
        initialFocusRef={inputRef}
        busy={busy}
      >
        <label htmlFor="dialog-description">Descrição</label>
        <input ref={inputRef} id="dialog-description" />
        <button type="button">Primeira ação</button>
        <button type="button">Última ação</button>
      </Dialog>
    </div>
  )
}

describe('Dialog acessível', () => {
  it('abre com foco inicial, bloqueia o fundo e trava o scroll', async () => {
    const user = userEvent.setup()
    const { container } = render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Abrir diálogo' }))

    const dialog = screen.getByRole('dialog', { name: 'Corrigir transação' })
    expect(dialog).toBeInTheDocument()
    expect(screen.getByLabelText('Descrição')).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')
    expect(container).toHaveAttribute('inert')

    await expectNoA11yViolations()
  })

  it('fecha com Escape e devolve o foco ao gatilho', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)
    const trigger = screen.getByRole('button', { name: 'Abrir diálogo' })

    await user.click(trigger)
    fireEvent.keyDown(document, { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
    expect(document.body.style.overflow).toBe('')
  })

  it('prende o foco dentro do diálogo', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Abrir diálogo' }))
    screen.getByRole('button', { name: 'Última ação' }).focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(screen.getByRole('button', { name: 'Fechar' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(screen.getByRole('button', { name: 'Última ação' })).toHaveFocus()
  })

  it('não fecha por Escape enquanto uma submissão está em andamento', async () => {
    const user = userEvent.setup()
    render(<DialogHarness busy />)

    await user.click(screen.getByRole('button', { name: 'Abrir diálogo' }))
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Fechar' })).toBeDisabled()
  })
})
