import { forwardRef } from 'react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/cn'

const iconButtonVariants = cva(
  [
    'inline-flex size-11 shrink-0 items-center justify-center rounded-control',
    'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas',
    'disabled:pointer-events-none disabled:bg-disabled disabled:text-on-disabled',
  ],
  {
    variants: {
      variant: {
        ghost: 'text-ink-muted hover:bg-surface-muted hover:text-ink',
        secondary: 'border border-line-strong bg-surface text-ink hover:bg-surface-muted',
        primary: 'bg-primary text-on-primary hover:bg-primary-hover',
        danger: 'bg-expense text-on-expense hover:opacity-90',
      },
    },
    defaultVariants: {
      variant: 'ghost',
    },
  },
)

export interface IconButtonProps
  extends
    Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label' | 'children'>,
    VariantProps<typeof iconButtonVariants> {
  /** Nome acessível obrigatório: o ícone é sempre decorativo. */
  readonly label: string
  readonly children: ReactNode
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { className, variant, label, children, type = 'button', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      aria-label={label}
      title={label}
      className={cn(iconButtonVariants({ variant }), className)}
      {...props}
    >
      <span aria-hidden="true" className="flex items-center justify-center">
        {children}
      </span>
    </button>
  )
})
