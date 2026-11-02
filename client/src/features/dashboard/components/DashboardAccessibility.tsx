import type { ReactNode } from 'react'

/** Região viva única para anunciar mudanças de filtros, carregamento e erro. */
export function DashboardLiveRegion({ message }: { message: string }) {
  return (
    <p role="status" aria-live="polite" aria-atomic="true" className="sr-only">
      {message}
    </p>
  )
}

interface DashboardSectionProps {
  readonly id: string
  readonly title: string
  readonly description?: string
  readonly actions?: ReactNode
  readonly children: ReactNode
}

/**
 * Seção semântica com título associado.
 *
 * Mantém um landmark por assunto e garante que a navegação por cabeçalhos encontre
 * cada bloco do dashboard.
 */
export function DashboardSection({
  id,
  title,
  description,
  actions,
  children,
}: DashboardSectionProps) {
  return (
    <section
      aria-labelledby={`${id}-heading`}
      className="space-y-4 rounded-card border border-line bg-surface p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 id={`${id}-heading`} className="text-lg font-semibold text-ink">
            {title}
          </h2>
          {description ? (
            <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">{description}</p>
          ) : null}
        </div>
        {actions}
      </div>
      {children}
    </section>
  )
}
