import type { HTMLAttributes } from 'react'

import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/lib/cn'

const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-pill border px-2.5 py-0.5 text-caption font-semibold whitespace-nowrap',
  {
    variants: {
      variant: {
        neutral: 'border-line bg-surface-muted text-ink',
        primary: 'border-primary/30 bg-primary-soft text-ink',
        income: 'border-income/35 bg-income-soft text-income',
        expense: 'border-expense/35 bg-expense-soft text-expense',
        warning: 'border-warning/45 bg-warning-soft text-ink',
        info: 'border-info/35 bg-info-soft text-info',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
)

export interface BadgeProps
  extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}
