import { Suspense } from 'react'

import { Outlet } from 'react-router'

import { RouteLoading } from '@/components/layout/RouteLoading'

import { SettingsNav } from '../components/SettingsNav'

/**
 * Moldura das áreas administrativas.
 *
 * Preferências, Integração, Privacidade e Conta compartilham a navegação
 * secundária, mas preservam título próprio e URL independente. Nenhuma
 * informação financeira é duplicada entre as áreas.
 */
export function SettingsLayout() {
  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      <SettingsNav />
      <Suspense fallback={<RouteLoading />}>
        <Outlet />
      </Suspense>
    </div>
  )
}
