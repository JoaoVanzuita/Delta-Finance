import { useId } from 'react'

import { Field } from '@/components/ui/field'
import { Input } from '@/components/ui/input'

interface SecretFieldProps {
  readonly label: string
  readonly value: string
  readonly onChange: (value: string) => void
  readonly hint?: string
  readonly error?: string
  readonly disabled?: boolean
  readonly autoComplete?: string
}

/**
 * Campo de segredo write-only.
 *
 * - nunca é preenchido com valor vindo do servidor (o backend só devolve máscara);
 * - não usa localStorage;
 * - limpo pelo formulário após envio bem-sucedido.
 */
export function SecretField({
  label,
  value,
  onChange,
  hint,
  error,
  disabled,
  autoComplete = 'off',
}: SecretFieldProps) {
  const id = useId()

  return (
    <Field id={id} label={label} hint={hint} error={error}>
      <Input
        id={id}
        type="password"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        aria-describedby={[hint ? `${id}-hint` : null, error ? `${id}-error` : null]
          .filter(Boolean)
          .join(' ')}
      />
    </Field>
  )
}
