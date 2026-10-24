import { createBrowserRouter } from 'react-router'

import { AccountDetailPage } from '@/features/accounts/pages/AccountDetailPage'
import { AccountsPage } from '@/features/accounts/pages/AccountsPage'
import { ManualAccountPage } from '@/features/accounts/pages/ManualAccountPage'
import { CategoriesPage } from '@/features/categories/pages/CategoriesPage'
import { DashboardPage } from '@/features/dashboard/pages/DashboardPage'
import { InvoiceDetailPage } from '@/features/invoices/pages/InvoiceDetailPage'
import { InvoicesPage } from '@/features/invoices/pages/InvoicesPage'
import { InvestmentsPage } from '@/features/investments/pages/InvestmentsPage'
import { WealthPage } from '@/features/wealth/pages/WealthPage'
import { ReconciliationPage } from '@/features/reconciliation/pages/ReconciliationPage'
import { ManualEntryPage } from '@/features/transactions/pages/ManualEntryPage'
import { AccountSettingsPage } from '@/features/auth/pages/AccountSettingsPage'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { IntegrationSetupPage } from '@/features/integration/pages/IntegrationSetupPage'
import { PrivacySettingsPage } from '@/features/privacy/pages/PrivacySettingsPage'
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
        path: 'dashboard',
        element: (
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        ),
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
        path: 'accounts/new',
        element: (
          <ProtectedRoute>
            <ManualAccountPage />
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
        path: 'accounts/:id/edit',
        element: (
          <ProtectedRoute>
            <ManualAccountPage />
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
        path: 'transactions/new',
        element: (
          <ProtectedRoute>
            <ManualEntryPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'categories',
        element: (
          <ProtectedRoute>
            <CategoriesPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'reconciliation',
        element: (
          <ProtectedRoute>
            <ReconciliationPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'cards/:accountId/invoices',
        element: (
          <ProtectedRoute>
            <InvoicesPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'invoices/:id',
        element: (
          <ProtectedRoute>
            <InvoiceDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'investments',
        element: (
          <ProtectedRoute>
            <InvestmentsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'wealth',
        element: (
          <ProtectedRoute>
            <WealthPage />
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
        path: 'settings/privacy',
        element: (
          <ProtectedRoute>
            <PrivacySettingsPage />
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
