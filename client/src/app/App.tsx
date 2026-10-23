import { RouterProvider } from 'react-router'

import { AppProviders } from './providers'
import { router } from './router'

/**
 * Composição raiz da SPA: providers globais + roteador.
 *
 * O layout de navegação (AppShell) e os tokens visuais entram em T034; nesta fase
 * a aplicação apenas garante que o roteador, o cache de servidor e o bundle
 * funcionam.
 */
export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  )
}
