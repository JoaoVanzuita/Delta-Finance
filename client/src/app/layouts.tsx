import { Suspense } from 'react'

import { Navigate, Outlet } from 'react-router'

import { AppShell } from '@/components/layout/AppShell'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { RouteLoading } from '@/components/layout/RouteLoading'
import { SessionExpiredBanner } from '@/features/auth/components/SessionExpiredBanner'
import { useSession } from '@/features/auth/hooks/useSession'

import { HomePage } from './HomePage'
import { NotFoundPage } from './NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'

/** Sessão carregando: evita piscar a Home pública para quem já está autenticado. */
function SessionLoading({ label }: { label: string }) {
  return (
    <div role="status" aria-live="polite" className="py-10 text-body-sm text-ink-muted">
      {label}
    </div>
  )
}

/**
 * `/` público para visitantes e atalho para o Dashboard quando há sessão.
 * Preserva a Home pública independente para quem não entrou.
 */
export function HomeRoute() {
  const { data: user, isLoading } = useSession()

  if (isLoading) {
    return <SessionLoading label="Carregando sua sessão…" />
  }
  if (user) {
    return <Navigate to="/dashboard" replace />
  }
  return <HomePage />
}

/** 404 usa o layout correspondente ao estado da sessão. */
export function NotFoundRoute() {
  const { data: user, isLoading } = useSession()
  if (isLoading) {
    return (
      <PublicLayout>
        <NotFoundPage />
      </PublicLayout>
    )
  }
  if (user) {
    return (
      <AppShell>
        <NotFoundPage authenticated />
      </AppShell>
    )
  }
  return (
    <PublicLayout>
      <NotFoundPage />
    </PublicLayout>
  )
}

/** Rotas autenticadas: proteção + AppShell com navegação agrupada. */
export function AuthenticatedLayout() {
  return (
    <ProtectedRoute>
      <AppShell>
        <SessionExpiredBanner />
        <Suspense fallback={<RouteLoading />}>
          <Outlet />
        </Suspense>
      </AppShell>
    </ProtectedRoute>
  )
}
