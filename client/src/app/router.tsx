import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'

import { PublicLayout } from '@/components/layout/PublicLayout'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { SettingsLayout } from '@/features/settings/pages/SettingsLayout'

import { AuthenticatedLayout, HomeRoute, NotFoundRoute } from './layouts'
import {
  AccountDetailPage,
  AccountSettingsPage,
  AccountsPage,
  CardDetailPage,
  CardsPage,
  CategoriesPage,
  DashboardPage,
  GoalsPage,
  IntegrationSetupPage,
  InvoiceDetailPage,
  InvoicesPage,
  InvestmentsPage,
  ManualAccountPage,
  ManualEntryPage,
  PreferencesPage,
  PrivacySettingsPage,
  ReconciliationPage,
  TransactionsPage,
  WealthPage,
} from './lazy-pages'
import { RootLayout } from './RootLayout'

/**
 * Tabela de rotas.
 *
 * - layout público: Home, login e cadastro, sem sidebar autenticada;
 * - layout autenticado: AppShell + proteção de sessão;
 * - 404 escolhe o layout conforme o estado da sessão.
 *
 * Nenhuma rota existente foi removida. `/` autenticado é encaminhado ao
 * Dashboard por `HomeRoute`; o destino pós-login é `/dashboard`.
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        element: <PublicLayout />,
        children: [
          { index: true, element: <HomeRoute /> },
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
        ],
      },
      {
        element: <AuthenticatedLayout />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'accounts', element: <AccountsPage /> },
          { path: 'accounts/new', element: <ManualAccountPage /> },
          { path: 'accounts/:id', element: <AccountDetailPage /> },
          { path: 'accounts/:id/edit', element: <ManualAccountPage /> },
          { path: 'cards', element: <CardsPage /> },
          { path: 'cards/:accountId', element: <CardDetailPage /> },
          { path: 'cards/:accountId/invoices', element: <InvoicesPage /> },
          { path: 'invoices/:id', element: <InvoiceDetailPage /> },
          { path: 'transactions', element: <TransactionsPage /> },
          { path: 'transactions/new', element: <ManualEntryPage /> },
          { path: 'categories', element: <CategoriesPage /> },
          { path: 'reconciliation', element: <ReconciliationPage /> },
          { path: 'investments', element: <InvestmentsPage /> },
          { path: 'wealth', element: <WealthPage /> },
          { path: 'goals', element: <GoalsPage /> },
          { path: 'settings', element: <Navigate to="/settings/preferences" replace /> },
          {
            element: <SettingsLayout />,
            children: [
              { path: 'settings/preferences', element: <PreferencesPage /> },
              { path: 'settings/integration', element: <IntegrationSetupPage /> },
              { path: 'settings/privacy', element: <PrivacySettingsPage /> },
              { path: 'account', element: <AccountSettingsPage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundRoute /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
