import { useId } from 'react'

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
  const hintId = `${id}-hint`
  const errorId = `${id}-error`

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      {hint ? (
        <p id={hintId} className="mt-1 text-xs text-ink-muted">
          {hint}
        </p>
      ) : null}
      <input
        id={id}
        type="password"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        disabled={disabled}
        aria-invalid={Boolean(error)}
        aria-describedby={[hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ')}
        className="mt-1 block w-full rounded-card border border-line bg-surface px-3 py-2 text-sm text-ink shadow-sm outline-none transition-colors focus:border-focus disabled:opacity-60"
      />
      {error ? (
        <p id={errorId} className="mt-1 text-sm text-expense">
          {error}
        </p>
      ) : null}
    </div>
  )
}
