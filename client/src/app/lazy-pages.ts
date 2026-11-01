import { lazy } from 'react'

/**
 * Páginas carregadas sob demanda.
 *
 * Login, cadastro e a Home pública permanecem no chunk principal por serem a
 * porta de entrada. Cada módulo de negócio vira um chunk próprio, reduzindo o
 * JavaScript necessário para a primeira pintura.
 */
export const DashboardPage = lazy(() =>
  import('@/features/dashboard/pages/DashboardPage').then((module) => ({
    default: module.DashboardPage,
  })),
)
export const AccountsPage = lazy(() =>
  import('@/features/accounts/pages/AccountsPage').then((module) => ({
    default: module.AccountsPage,
  })),
)
export const AccountDetailPage = lazy(() =>
  import('@/features/accounts/pages/AccountDetailPage').then((module) => ({
    default: module.AccountDetailPage,
  })),
)
export const ManualAccountPage = lazy(() =>
  import('@/features/accounts/pages/ManualAccountPage').then((module) => ({
    default: module.ManualAccountPage,
  })),
)
export const CardsPage = lazy(() =>
  import('@/features/cards/pages/CardsPage').then((module) => ({ default: module.CardsPage })),
)
export const CardDetailPage = lazy(() =>
  import('@/features/cards/pages/CardDetailPage').then((module) => ({
    default: module.CardDetailPage,
  })),
)
export const InvoicesPage = lazy(() =>
  import('@/features/invoices/pages/InvoicesPage').then((module) => ({
    default: module.InvoicesPage,
  })),
)
export const InvoiceDetailPage = lazy(() =>
  import('@/features/invoices/pages/InvoiceDetailPage').then((module) => ({
    default: module.InvoiceDetailPage,
  })),
)
export const TransactionsPage = lazy(() =>
  import('@/features/transactions/pages/TransactionsPage').then((module) => ({
    default: module.TransactionsPage,
  })),
)
export const ManualEntryPage = lazy(() =>
  import('@/features/transactions/pages/ManualEntryPage').then((module) => ({
    default: module.ManualEntryPage,
  })),
)
export const CategoriesPage = lazy(() =>
  import('@/features/categories/pages/CategoriesPage').then((module) => ({
    default: module.CategoriesPage,
  })),
)
export const ReconciliationPage = lazy(() =>
  import('@/features/reconciliation/pages/ReconciliationPage').then((module) => ({
    default: module.ReconciliationPage,
  })),
)
export const InvestmentsPage = lazy(() =>
  import('@/features/investments/pages/InvestmentsPage').then((module) => ({
    default: module.InvestmentsPage,
  })),
)
export const WealthPage = lazy(() =>
  import('@/features/wealth/pages/WealthPage').then((module) => ({
    default: module.WealthPage,
  })),
)
export const GoalsPage = lazy(() =>
  import('@/features/goals/pages/GoalsPage').then((module) => ({ default: module.GoalsPage })),
)
export const PreferencesPage = lazy(() =>
  import('@/features/settings/pages/PreferencesPage').then((module) => ({
    default: module.PreferencesPage,
  })),
)
export const IntegrationSetupPage = lazy(() =>
  import('@/features/integration/pages/IntegrationSetupPage').then((module) => ({
    default: module.IntegrationSetupPage,
  })),
)
export const PrivacySettingsPage = lazy(() =>
  import('@/features/privacy/pages/PrivacySettingsPage').then((module) => ({
    default: module.PrivacySettingsPage,
  })),
)
export const AccountSettingsPage = lazy(() =>
  import('@/features/auth/pages/AccountSettingsPage').then((module) => ({
    default: module.AccountSettingsPage,
  })),
)
