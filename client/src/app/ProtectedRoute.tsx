import type { ReactNode } from 'react'

import { Navigate, useLocation } from 'react-router'

import { ApiError } from '@/lib/api/problem'

import { useSession } from '@/features/auth/hooks/useSession'

/**
 * Protege rotas autenticadas.
 *
 * - 401/ausência de sessão: redireciona para /login guardando o destino;
 * - 403: mostra acesso negado;
 * - outros erros: estado recuperável com nova tentativa.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { data: user, isLoading, isError, error, refetch } = useSession()

  if (isLoading) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mx-auto max-w-md py-10 text-sm text-ink-muted"
      >
        Carregando sua sessão…
      </div>
    )
  }

  if (isError) {
    const forbidden = error instanceof ApiError && error.status === 403
    return (
      <section
        role="alert"
        className="mx-auto max-w-md space-y-3 rounded-card border border-expense/30 bg-expense/5 p-5"
      >
        <h1 className="text-lg font-semibold text-ink">
          {forbidden ? 'Acesso negado' : 'Não foi possível carregar sua sessão'}
        </h1>
        <p className="text-sm text-ink-muted">
          {forbidden
            ? 'Sua conta não tem permissão para acessar este recurso.'
            : 'Ocorreu uma falha temporária. Tente novamente.'}
        </p>
        {error instanceof ApiError && error.requestId ? (
          <p className="text-xs text-ink-muted">Código de suporte: {error.requestId}</p>
        ) : null}
        {!forbidden ? (
          <button
            type="button"
            onClick={() => void refetch()}
            className="inline-flex min-h-11 items-center justify-center rounded-card bg-primary px-4 text-sm font-semibold text-surface transition-colors hover:bg-primary-hover"
          >
            Tentar novamente
          </button>
        ) : null}
      </section>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
