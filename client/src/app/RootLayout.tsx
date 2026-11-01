import { Suspense } from 'react'

import { Outlet } from 'react-router'

import { RouteLoading } from '@/components/layout/RouteLoading'

import { useSessionExpiryListener } from '@/features/auth/hooks/useSession'

function SessionExpiryListener() {
  useSessionExpiryListener()
  return null
}

/**
 * Raiz de todas as rotas.
 *
 * Apenas efeitos globais de sessão; cada rota escolhe explicitamente entre o
 * layout público e o layout autenticado. O `Suspense` é a última barreira para
 * páginas carregadas sob demanda; os layouts internos têm fallback próprio para
 * preservar a navegação.
 */
export function RootLayout() {
  return (
    <>
      <SessionExpiryListener />
      <Suspense fallback={<RouteLoading />}>
        <Outlet />
      </Suspense>
    </>
  )
}
