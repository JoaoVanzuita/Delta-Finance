import { Skeleton } from '@/components/ui/skeleton'

/**
 * Fallback de carregamento de rotas com code splitting.
 *
 * Mantém a semântica de status e um esqueleto estável para evitar salto visual.
 * É usado dentro dos layouts para preservar sidebar e navegação secundária.
 */
export function RouteLoading() {
  return (
    <div role="status" aria-live="polite" className="mx-auto w-full max-w-5xl space-y-4 py-6">
      <span className="sr-only">Carregando página…</span>
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-4 w-80 max-w-full" />
      <div className="grid gap-4 pt-2 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
      </div>
      <Skeleton className="h-64" />
    </div>
  )
}
