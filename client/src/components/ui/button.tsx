import { forwardRef } from 'react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { Loader2 } from 'lucide-react'
import type { VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/cn'

import { buttonVariants } from './button-variants'

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  /** Mostra progresso e mantém o rótulo para leitores de tela. */
  readonly loading?: boolean
  readonly loadingLabel?: string
  readonly leadingIcon?: ReactNode
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    className,
    variant,
    size,
    fullWidth,
    loading = false,
    loadingLabel,
    leadingIcon,
    disabled,
    children,
    type = 'button',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(buttonVariants({ variant, size, fullWidth }), className)}
      {...props}
    >
      {loading ? <Loader2 aria-hidden="true" className="size-4 animate-spin" /> : leadingIcon}
      <span>{loading ? (loadingLabel ?? children) : children}</span>
    </button>
  )
})
