import { createBrowserRouter } from 'react-router'

import { AccountDetailPage } from '@/features/accounts/pages/AccountDetailPage'
import { AccountsPage } from '@/features/accounts/pages/AccountsPage'
import { AccountSettingsPage } from '@/features/auth/pages/AccountSettingsPage'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { IntegrationSetupPage } from '@/features/integration/pages/IntegrationSetupPage'
import { TransactionsPage } from '@/features/transactions/pages/TransactionsPage'

import { HomePage } from './HomePage'
import { NotFoundPage } from './NotFoundPage'
import { ProtectedRoute } from './ProtectedRoute'
import { RootLayout } from './RootLayout'

/**
 * Tabela de rotas.
 *
 * Rotas de negócio são adicionadas junto com as histórias correspondentes. Nesta
 * fase existem apenas identidade (login/registro/conta) e a página inicial; não
 * há links para telas ainda inexistentes.
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
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        path: 'register',
        element: <RegisterPage />,
      },
      {
        path: 'accounts',
        element: (
          <ProtectedRoute>
            <AccountsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'accounts/:id',
        element: (
          <ProtectedRoute>
            <AccountDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'transactions',
        element: (
          <ProtectedRoute>
            <TransactionsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'settings/integration',
        element: (
          <ProtectedRoute>
            <IntegrationSetupPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'account',
        element: (
          <ProtectedRoute>
            <AccountSettingsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
])
