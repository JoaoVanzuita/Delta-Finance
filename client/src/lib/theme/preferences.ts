/**
 * Preferências visuais locais.
 *
 * Regras:
 * - somente tema (`light`/`dark`/`system`) e paleta (`blue`/`green`/`purple`/`amber`);
 * - nenhum dado financeiro ou de identificação é gravado;
 * - a chave é namespaced por usuário quando há identificador estável, evitando
 *   que duas contas no mesmo navegador compartilhem preferências.
 *
 * Limitação documentada: antes da autenticação o namespace é `guest`. Em um
 * navegador compartilhado, a primeira pintura da tela de login pode usar a
 * preferência do namespace ativo anterior; após a sessão resolver, a preferência
 * do usuário autenticado é aplicada.
 */

export const THEME_PREFERENCES = ['light', 'dark', 'system'] as const
export type ThemePreference = (typeof THEME_PREFERENCES)[number]

export const ACCENTS = ['blue', 'green', 'purple', 'amber'] as const
export type Accent = (typeof ACCENTS)[number]

export type ResolvedTheme = 'light' | 'dark'

export interface VisualPreferences {
  readonly theme: ThemePreference
  readonly accent: Accent
}

export interface StoredVisualPreferences extends VisualPreferences {
  readonly namespace: string
}

export const DEFAULT_PREFERENCES: VisualPreferences = {
  theme: 'system',
  accent: 'blue',
}

const STORAGE_PREFIX = 'delta.prefs'
const ACTIVE_KEY = `${STORAGE_PREFIX}.active`
export const GUEST_NAMESPACE = 'guest'

export function preferencesKey(namespace: string): string {
  return `${STORAGE_PREFIX}.${namespace}`
}

function isTheme(value: unknown): value is ThemePreference {
  return typeof value === 'string' && (THEME_PREFERENCES as readonly string[]).includes(value)
}

function isAccent(value: unknown): value is Accent {
  return typeof value === 'string' && (ACCENTS as readonly string[]).includes(value)
}

export function normalizePreferences(value: unknown): VisualPreferences {
  if (!value || typeof value !== 'object') {
    return DEFAULT_PREFERENCES
  }
  const record = value as Record<string, unknown>
  return {
    theme: isTheme(record.theme) ? record.theme : DEFAULT_PREFERENCES.theme,
    accent: isAccent(record.accent) ? record.accent : DEFAULT_PREFERENCES.accent,
  }
}

export function resolveTheme(
  preference: ThemePreference,
  systemPrefersDark: boolean,
): ResolvedTheme {
  if (preference === 'system') {
    return systemPrefersDark ? 'dark' : 'light'
  }
  return preference
}

/** Lê as preferências apenas do namespace informado. */
export function readPreferences(namespace: string): VisualPreferences {
  try {
    const raw = window.localStorage.getItem(preferencesKey(namespace))
    return raw ? normalizePreferences(JSON.parse(raw)) : DEFAULT_PREFERENCES
  } catch {
    return DEFAULT_PREFERENCES
  }
}

export function writePreferences(namespace: string, preferences: VisualPreferences): void {
  try {
    window.localStorage.setItem(
      preferencesKey(namespace),
      JSON.stringify({
        theme: preferences.theme,
        accent: preferences.accent,
      }),
    )
  } catch {
    // Modo privado/cota esgotada: a preferência vale para a sessão atual.
  }
}

export function readActiveNamespace(): string {
  try {
    return window.localStorage.getItem(ACTIVE_KEY) || GUEST_NAMESPACE
  } catch {
    return GUEST_NAMESPACE
  }
}

export function writeActiveNamespace(namespace: string): void {
  try {
    window.localStorage.setItem(ACTIVE_KEY, namespace)
  } catch {
    // Sem persistência disponível.
  }
}

/** Namespace estável: id do usuário quando autenticado, `guest` caso contrário. */
export function namespaceForUser(userId: string | null | undefined): string {
  return userId && userId.trim() ? userId : GUEST_NAMESPACE
}

export function systemPrefersDark(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  )
}

/**
 * Aplica tema resolvido e paleta ao documento, incluindo `color-scheme` e a
 * cor de barra do navegador. Não grava nada.
 */
export function applyVisualPreferences(
  preferences: VisualPreferences,
  resolvedTheme: ResolvedTheme,
  root: HTMLElement = document.documentElement,
): void {
  root.setAttribute('data-theme', resolvedTheme)
  root.setAttribute('data-theme-preference', preferences.theme)
  root.setAttribute('data-accent', preferences.accent)
  root.style.colorScheme = resolvedTheme

  const themeColor = document.querySelector('meta[name="theme-color"]')
  if (themeColor) {
    themeColor.setAttribute('content', resolvedTheme === 'dark' ? '#0a1120' : '#f5f7fb')
  }
}
