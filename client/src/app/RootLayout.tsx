import { Outlet } from 'react-router'

import { AppShell } from '@/components/layout/AppShell'
import { SessionExpiredBanner } from '@/features/auth/components/SessionExpiredBanner'
import { useSessionExpiryListener } from '@/features/auth/hooks/useSession'

function SessionExpiryListener() {
  useSessionExpiryListener()
  return null
}

/** Raiz de todas as rotas: aplica o layout sem duplicar a navegação. */
export function RootLayout() {
  return (
    <AppShell>
      <SessionExpiryListener />
      <SessionExpiredBanner />
      <Outlet />
    </AppShell>
  )
}
