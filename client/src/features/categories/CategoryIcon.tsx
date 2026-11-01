import { cn } from '@/lib/cn'

import { categoryVisual } from './visuals'

interface CategoryIconProps {
  readonly id: string
  readonly name: string
  readonly className?: string
  readonly size?: 'sm' | 'md'
}

/** Ícone de categoria com cor determinística. Sempre acompanhado do nome visível. */
export function CategoryIcon({ id, name, className, size = 'md' }: CategoryIconProps) {
  const visual = categoryVisual({ id, name })
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full',
        size === 'sm' ? 'size-7' : 'size-9',
        className,
      )}
      style={{ backgroundColor: `color-mix(in srgb, ${visual.colorVar} 16%, transparent)` }}
      aria-hidden="true"
    >
      <visual.icon className={cn(size === 'sm' ? 'size-3.5' : 'size-4', visual.textClass)} />
    </span>
  )
}
