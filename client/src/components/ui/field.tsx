import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface LabelProps {
  readonly htmlFor: string
  readonly children: ReactNode
  readonly required?: boolean
  readonly className?: string
}

export function Label({ htmlFor, children, required = false, className }: LabelProps) {
  return (
    <label htmlFor={htmlFor} className={cn('block text-body-sm font-medium text-ink', className)}>
      {children}
      {required ? (
        <span className="ml-1 text-expense" aria-hidden="true">
          *
        </span>
      ) : null}
      {required ? <span className="sr-only"> (obrigatório)</span> : null}
    </label>
  )
}

export interface FormErrorProps {
  readonly id?: string
  readonly children: ReactNode
  readonly className?: string
}

export function FormError({ id, children, className }: FormErrorProps) {
  return (
    <p id={id} className={cn('mt-1 text-body-sm text-expense', className)}>
      {children}
    </p>
  )
}

export interface FieldProps {
  readonly id: string
  readonly label: string
  readonly hint?: ReactNode
  readonly error?: ReactNode
  readonly required?: boolean
  readonly children: ReactNode
  readonly className?: string
  /** Quando o controle já usa outro id para o erro. */
  readonly errorId?: string
  readonly hintId?: string
}

/**
 * Campo acessível: rótulo, dica e erro ligados ao controle por id.
 *
 * O controle filho deve usar `id` e `aria-describedby` com os ids informados;
 * `Field` mantém a estrutura visual e o erro anunciado de forma consistente.
 */
export function Field({
  id,
  label,
  hint,
  error,
  required = false,
  children,
  className,
  errorId = `${id}-error`,
  hintId = `${id}-hint`,
}: FieldProps) {
  return (
    <div className={cn('space-y-1', className)}>
      <Label htmlFor={id} required={required}>
        {label}
      </Label>
      {hint ? (
        <p id={hintId} className="text-caption text-ink-muted">
          {hint}
        </p>
      ) : null}
      {children}
      {error ? <FormError id={errorId}>{error}</FormError> : null}
    </div>
  )
}
