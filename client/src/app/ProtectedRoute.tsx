import type { ReactNode } from 'react'

import { Navigate, useLocation } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
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
        className="mx-auto max-w-md py-10 text-body-sm text-ink-muted"
      >
        Carregando sua sessão…
      </div>
    )
  }

  if (isError) {
    const forbidden = error instanceof ApiError && error.status === 403
    return (
      <section className="mx-auto max-w-md space-y-3">
        <Alert
          variant={forbidden ? 'warning' : 'danger'}
          title={forbidden ? 'Acesso negado' : 'Não foi possível carregar sua sessão'}
        >
          <p>
            {forbidden
              ? 'Sua conta não tem permissão para acessar este recurso.'
              : 'Ocorreu uma falha temporária. Tente novamente.'}
          </p>
          {error instanceof ApiError && error.requestId ? (
            <p className="text-caption">Código de suporte: {error.requestId}</p>
          ) : null}
        </Alert>
        {!forbidden ? <Button onClick={() => void refetch()}>Tentar novamente</Button> : null}
      </section>
    )
  }

  if (!user) {
    return (
      <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
    )
  }

  return <>{children}</>
}
