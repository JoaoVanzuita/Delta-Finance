import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router'

import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/problem'

/**
 * Última barreira de renderização: evita a tela padrão em inglês do React Router e oferece
 * recuperação sem recarregar o aplicativo inteiro.
 */
export function RouteError() {
  const error = useRouteError()
  const navigate = useNavigate()
  const requestId = error instanceof ApiError ? error.requestId : null
  const detail = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : error instanceof ApiError
      ? error.message
      : 'Ocorreu um erro inesperado ao montar esta tela.'

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-4 p-6">
      <Alert variant="danger" title="Não foi possível abrir esta tela">
        <p>{detail}</p>
        {requestId ? <p className="text-caption">Código de suporte: {requestId}</p> : null}
      </Alert>
      <div className="flex flex-wrap gap-2">
        <Button type="button" onClick={() => window.location.reload()}>
          Tentar novamente
        </Button>
        <Button type="button" variant="secondary" onClick={() => void navigate('/dashboard')}>
          Ir para o dashboard
        </Button>
      </div>
    </main>
  )
}
