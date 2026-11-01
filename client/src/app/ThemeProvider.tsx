import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { useSession } from '@/features/auth/hooks/useSession'
import {
  applyVisualPreferences,
  namespaceForUser,
  readActiveNamespace,
  readPreferences,
  resolveTheme,
  systemPrefersDark,
  writeActiveNamespace,
  writePreferences,
  type Accent,
  type ThemePreference,
  type VisualPreferences,
} from '@/lib/theme/preferences'

import { ThemeContext, type ThemeContextValue } from './theme-context'

interface ThemePreferencesProviderProps {
  readonly namespace: string
  readonly children: ReactNode
}

function ThemePreferencesProvider({ namespace, children }: ThemePreferencesProviderProps) {
  const [preferences, setPreferences] = useState<VisualPreferences>(() =>
    readPreferences(namespace),
  )
  const [systemDark, setSystemDark] = useState(systemPrefersDark)

  const resolvedTheme = resolveTheme(preferences.theme, systemDark)

  useEffect(() => {
    applyVisualPreferences(preferences, resolvedTheme)
  }, [preferences, resolvedTheme])

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    query.addEventListener?.('change', onChange)
    return () => query.removeEventListener?.('change', onChange)
  }, [])

  const update = useCallback(
    (patch: Partial<VisualPreferences>) => {
      setPreferences((current) => {
        const next = { ...current, ...patch }
        writePreferences(namespace, next)
        return next
      })
    },
    [namespace],
  )

  const setTheme = useCallback((theme: ThemePreference) => update({ theme }), [update])
  const setAccent = useCallback((accent: Accent) => update({ accent }), [update])

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme: preferences.theme,
      accent: preferences.accent,
      resolvedTheme,
      namespace,
      setTheme,
      setAccent,
    }),
    [preferences.theme, preferences.accent, resolvedTheme, namespace, setTheme, setAccent],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

/**
 * Aplica e persiste tema/paleta por namespace de usuário.
 *
 * A sessão é consultada aqui apenas para descobrir o identificador estável. A
 * preferência em si é local, namespaced e nunca contém dados financeiros.
 * Enquanto a sessão resolve, o namespace ativo é lido do armazenamento para
 * coincidir com o script de primeira pintura.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { data: user, isPending: sessionLoading } = useSession()
  const namespace = sessionLoading ? readActiveNamespace() : namespaceForUser(user?.id)

  useEffect(() => {
    if (!sessionLoading) {
      writeActiveNamespace(namespace)
    }
  }, [sessionLoading, namespace])

  return (
    <ThemePreferencesProvider key={namespace} namespace={namespace}>
      {children}
    </ThemePreferencesProvider>
  )
}
