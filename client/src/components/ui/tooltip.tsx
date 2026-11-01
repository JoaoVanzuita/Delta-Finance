import { Children, cloneElement, isValidElement, useId } from 'react'
import type { ReactElement, ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface TooltipProps {
  readonly label: string
  /** Elemento único que recebe `aria-describedby`. */
  readonly children: ReactNode
  readonly side?: 'right' | 'top'
  readonly className?: string
}

/** Tooltip acessível por foco e hover; o texto também nomeia o controle. */
export function Tooltip({ label, children, side = 'top', className }: TooltipProps) {
  const id = useId()
  const child = Children.only(children)
  if (!isValidElement(child)) {
    return <>{children}</>
  }
  const element = child as ReactElement<{ 'aria-describedby'?: string }>
  const describedBy = [element.props['aria-describedby'], id].filter(Boolean).join(' ')
  const trigger = cloneElement(element, { 'aria-describedby': describedBy })

  return (
    <span className={cn('group relative inline-flex', className)}>
      {trigger}
      <span
        id={id}
        role="tooltip"
        className={cn(
          'pointer-events-none absolute z-50 w-max max-w-48 rounded-control border border-line',
          'bg-surface-raised px-2 py-1 text-caption font-medium text-ink opacity-0 shadow-[var(--shadow-card)]',
          'transition-opacity duration-[var(--motion-fast)] group-hover:opacity-100 group-focus-within:opacity-100',
          side === 'right'
            ? 'left-full top-1/2 ml-2 -translate-y-1/2'
            : 'bottom-full left-1/2 mb-2 -translate-x-1/2',
        )}
      >
        {label}
      </span>
    </span>
  )
}
