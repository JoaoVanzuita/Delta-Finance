import { cn } from '@/lib/cn'

export interface BrandProps {
  readonly className?: string
  /** Quando verdadeiro, oculta o texto no rail de tablet. */
  readonly rail?: boolean
}

/** Marca do Delta Finance: símbolo Δ + nome. */
export function Brand({ className, rail = false }: BrandProps) {
  return (
    <div className={cn('flex min-w-0 items-center gap-3', className)}>
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-control border border-primary/20 bg-primary text-base font-bold text-on-primary"
      >
        Δ
      </span>
      <span className={cn('min-w-0 leading-tight', rail && 'md:sr-only lg:not-sr-only')}>
        <span className="block truncate text-body-sm font-semibold tracking-tight text-ink">
          Delta Finance
        </span>
        <span className="block truncate text-caption text-ink-muted">Finanças da família</span>
      </span>
    </div>
  )
}
