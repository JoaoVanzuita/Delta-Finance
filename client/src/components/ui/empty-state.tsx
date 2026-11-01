import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

export interface EmptyStateProps {
  readonly title: string
  readonly description?: ReactNode
  readonly icon?: ReactNode
  readonly action?: ReactNode
  readonly className?: string
}

/** Ausência de dados com causa e próximo passo — nunca apenas “nenhum item”. */
export function EmptyState({ title, description, icon, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-start gap-3 rounded-card border border-dashed border-line-strong',
        'bg-surface p-6 text-left',
        className,
      )}
    >
      {icon ? (
        <span
          aria-hidden="true"
          className="flex size-10 items-center justify-center rounded-control bg-surface-muted text-ink-muted"
        >
          {icon}
        </span>
      ) : null}
      <div className="space-y-1">
        <h2 className="text-section-title text-ink">{title}</h2>
        {description ? (
          <p className="max-w-prose text-body-sm text-ink-muted">{description}</p>
        ) : null}
      </div>
      {action ? <div className="pt-1">{action}</div> : null}
    </div>
  )
}
