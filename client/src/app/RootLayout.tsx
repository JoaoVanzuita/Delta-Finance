import { Outlet } from 'react-router'

import { AppShell } from '@/components/layout/AppShell'

/** Raiz de todas as rotas: aplica o layout sem duplicar a navegação. */
export function RootLayout() {
  return (
    <AppShell>
      <Outlet />
    </AppShell>
  )
}
