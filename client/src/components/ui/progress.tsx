import { cn } from '@/lib/cn'

export interface ProgressProps {
  readonly value: number
  readonly label: string
  readonly max?: number
  readonly showValue?: boolean
  readonly tone?: 'primary' | 'income' | 'expense' | 'warning'
  readonly className?: string
}

const tones = {
  primary: 'bg-primary',
  income: 'bg-income',
  expense: 'bg-expense',
  warning: 'bg-warning',
} as const

/** Barra de progresso determinada, com nome acessível e valor anunciado. */
export function Progress({
  value,
  label,
  max = 100,
  showValue = true,
  tone = 'primary',
  className,
}: ProgressProps) {
  const safeMax = max > 0 ? max : 100
  const safeValue = Math.max(0, Math.min(safeMax, Number.isFinite(value) ? value : 0))
  const percent = Math.round((safeValue / safeMax) * 100)

  return (
    <div className={cn('space-y-1.5', className)}>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-caption font-medium text-ink-muted">{label}</span>
        {showValue ? <span className="numeric text-caption text-ink">{percent}%</span> : null}
      </div>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={safeMax}
        aria-valuenow={safeValue}
        aria-valuetext={`${percent}%`}
        className="h-2 w-full overflow-hidden rounded-pill bg-surface-muted"
      >
        <div
          className={cn(
            'h-full rounded-pill transition-[width] duration-[var(--motion-base)] ease-[var(--ease-standard)]',
            tones[tone],
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}
