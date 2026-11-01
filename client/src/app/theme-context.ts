import { createContext, useContext } from 'react'

import type { Accent, ResolvedTheme, ThemePreference } from '@/lib/theme/preferences'

export interface ThemeContextValue {
  readonly theme: ThemePreference
  readonly accent: Accent
  readonly resolvedTheme: ResolvedTheme
  readonly namespace: string
  readonly setTheme: (theme: ThemePreference) => void
  readonly setAccent: (accent: Accent) => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)

/** Variante tolerante para componentes que podem renderizar sem o provider em testes. */
export function useOptionalTheme(): ThemeContextValue | null {
  return useContext(ThemeContext)
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme deve ser usado dentro de ThemeProvider')
  }
  return context
}
