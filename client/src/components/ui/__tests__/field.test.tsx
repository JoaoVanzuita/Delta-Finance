import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { expectNoA11yViolations } from '@/test/axe'

describe('controles de formulário', () => {
  it('associa rótulo, dica e erro ao input', async () => {
    const user = userEvent.setup()
    render(
      <Field
        id="nome"
        label="Nome da conta"
        hint="Como aparece nas listas."
        error="Informe um nome."
        required
      >
        <Input id="nome" aria-invalid aria-describedby="nome-hint nome-error" defaultValue="" />
      </Field>,
    )

    const input = screen.getByLabelText(/Nome da conta/)
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(/Como aparece nas listas/)
    expect(screen.getByText('Informe um nome.')).toHaveAttribute('id', 'nome-error')

    await user.type(input, 'Carteira')
    expect(input).toHaveValue('Carteira')
  })

  it('renderiza select e textarea com o mesmo tratamento visual', () => {
    render(
      <div>
        <Field id="tipo" label="Tipo">
          <Select id="tipo" defaultValue="CASH">
            <option value="CASH">Carteira</option>
          </Select>
        </Field>
        <Field id="notas" label="Observações">
          <Textarea id="notas" defaultValue="Sem observações" />
        </Field>
      </div>,
    )

    expect(screen.getByLabelText('Tipo')).toHaveValue('CASH')
    expect(screen.getByLabelText('Observações')).toHaveValue('Sem observações')
  })

  it('não tem violações WCAG nos estados de erro', async () => {
    render(
      <Field id="valor" label="Valor" error="Informe um valor positivo." required>
        <Input id="valor" aria-invalid aria-describedby="valor-error" />
      </Field>,
    )
    await expectNoA11yViolations()
  })
})
