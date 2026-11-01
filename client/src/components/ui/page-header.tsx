import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface PageHeaderProps {
  readonly title: string
  readonly description?: ReactNode
  /** Rótulo curto de contexto; nunca um rótulo interno de roadmap. */
  readonly overline?: string
  readonly actions?: ReactNode
  readonly meta?: ReactNode
  readonly className?: string
}

/** Cabeçalho de página autenticada: um único `h1`, contexto e no máximo uma ação principal. */
export function PageHeader({
  title,
  description,
  overline,
  actions,
  meta,
  className,
}: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-4', className)}>
      <div className="min-w-0 space-y-1.5">
        {overline ? <p className="overline text-ink-muted">{overline}</p> : null}
        <h1 className="text-page-title font-bold tracking-tight text-ink">{title}</h1>
        {description ? (
          <p className="max-w-prose text-body-sm text-ink-muted">{description}</p>
        ) : null}
        {meta ? <div className="flex flex-wrap items-center gap-2 pt-1">{meta}</div> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  )
}

export interface SectionHeaderProps {
  readonly title: string
  readonly description?: ReactNode
  readonly actions?: ReactNode
  readonly level?: 2 | 3
  readonly className?: string
}

export function SectionHeader({
  title,
  description,
  actions,
  level = 2,
  className,
}: SectionHeaderProps) {
  const Heading = level === 3 ? 'h3' : 'h2'
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3', className)}>
      <div className="min-w-0 space-y-1">
        <Heading className="text-section-title font-semibold text-ink">{title}</Heading>
        {description ? <p className="text-body-sm text-ink-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  )
}
