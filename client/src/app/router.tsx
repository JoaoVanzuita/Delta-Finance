import { createBrowserRouter } from 'react-router'

import { HomePage } from './HomePage'
import { RootLayout } from './RootLayout'

/**
 * Tabela de rotas.
 *
 * As rotas de negócio (/login, /dashboard, /accounts, /transactions, /invoices,
 * /reconciliation, /settings/integration, /settings/privacy) são adicionadas nas
 * histórias correspondentes (T044+). Elas não são criadas vazias aqui para que a
 * navegação não anuncie funcionalidades ainda inexistentes.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
    ],
  },
])
