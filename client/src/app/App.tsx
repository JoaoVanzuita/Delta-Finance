import { RouterProvider } from 'react-router'

import { AppProviders } from './providers'
import { router } from './router'

/**
 * Composição raiz da SPA: providers globais + roteador.
 *
 * O roteador contém as telas de negócio; a aplicação mantém o cache de servidor, o tratamento de
 * sessão e o bundle em um único ponto de composição.
 */
export function App() {
  return (
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>
  )
}
