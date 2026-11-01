import type { HTMLAttributes, ReactNode } from 'react'

import { cva, type VariantProps } from 'class-variance-authority'
import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react'

import { cn } from '@/lib/cn'

const alertVariants = cva('flex gap-3 rounded-card border p-4 text-body-sm', {
  variants: {
    variant: {
      info: 'border-info/35 bg-info-soft text-ink',
      success: 'border-income/35 bg-income-soft text-ink',
      warning: 'border-warning/35 bg-warning-soft text-ink',
      danger: 'border-expense/35 bg-expense-soft text-ink',
      neutral: 'border-line bg-surface-muted text-ink',
    },
  },
  defaultVariants: {
    variant: 'info',
  },
})

const icons = {
  info: Info,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleAlert,
  neutral: Info,
} as const

const iconTones = {
  info: 'text-info',
  success: 'text-income',
  warning: 'text-warning',
  danger: 'text-expense',
  neutral: 'text-ink-muted',
} as const

export interface AlertProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'title'>, VariantProps<typeof alertVariants> {
  readonly title?: ReactNode
  readonly children: ReactNode
  readonly role?: 'alert' | 'status' | undefined
}

export function Alert({
  className,
  variant = 'info',
  title,
  children,
  role,
  ...props
}: AlertProps) {
  const tone = variant ?? 'info'
  const Icon = icons[tone]
  return (
    <div
      role={role ?? (tone === 'danger' ? 'alert' : 'status')}
      className={cn(alertVariants({ variant: tone }), className)}
      {...props}
    >
      <Icon aria-hidden="true" className={cn('mt-0.5 size-5 shrink-0', iconTones[tone])} />
      <div className="min-w-0 space-y-1">
        {title ? <p className="font-semibold text-ink">{title}</p> : null}
        <div className="text-ink-muted [&_strong]:text-ink">{children}</div>
      </div>
    </div>
  )
}
