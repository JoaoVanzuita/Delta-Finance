import { useEffect } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'

import {
  markSessionCleared,
  markSessionEstablished,
  refreshCsrfToken,
  UNAUTHENTICATED_EVENT,
  type User,
} from '@/lib/api/client'
import { ApiError } from '@/lib/api/problem'
import { clearSessionExpired, markSessionExpired } from '@/lib/session-expiry'

import { authApi, type LoginInput, type PasswordChangeInput, type RegisterInput } from '../api'

export const SESSION_QUERY_KEY = ['session'] as const

const INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000
const ABSOLUTE_TIMEOUT_MS = 12 * 60 * 60 * 1000
const CHECK_INTERVAL_MS = 15 * 1000
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'touchstart'] as const

const SESSION_STARTED_KEY = 'delta.session.startedAt'

let sessionStartedAt: number | null = null
let lastActivityAt = Date.now()

function readSessionStartedAt(): number | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.sessionStorage.getItem(SESSION_STARTED_KEY)
    if (!raw) return null
    const value = Number(raw)
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

function writeSessionStartedAt(value: number | null): void {
  if (typeof window === 'undefined') return
  try {
    if (value === null) {
      window.sessionStorage.removeItem(SESSION_STARTED_KEY)
    } else {
      window.sessionStorage.setItem(SESSION_STARTED_KEY, String(value))
    }
  } catch {
    // sessionStorage indisponível: o limite absoluto volta a valer por carregamento.
  }
}

/** Reinicia apenas o relógio em memória entre testes. */
export function resetSessionClockForTesting(): void {
  sessionStartedAt = null
  lastActivityAt = Date.now()
  writeSessionStartedAt(null)
}

/** Marca o início de uma sessão recém-autenticada. */
function startSessionClock(): void {
  sessionStartedAt = Date.now()
  lastActivityAt = Date.now()
  writeSessionStartedAt(sessionStartedAt)
}

/** Mantém o início absoluto ao recarregar a página com sessão já existente. */
function ensureSessionClock(): void {
  if (sessionStartedAt === null) {
    sessionStartedAt = readSessionStartedAt() ?? Date.now()
  }
  writeSessionStartedAt(sessionStartedAt)
  lastActivityAt = Date.now()
}

/** Remove dados financeiros sem apagar a própria consulta de sessão. */
function clearFinancialCache(queryClient: QueryClient): void {
  queryClient.removeQueries({
    predicate: (query) => query.queryKey[0] !== SESSION_QUERY_KEY[0],
  })
}

function clearSessionState(queryClient: QueryClient): void {
  markSessionCleared()
  sessionStartedAt = null
  writeSessionStartedAt(null)
  clearFinancialCache(queryClient)
  queryClient.setQueryData(SESSION_QUERY_KEY, null)
}

/**
 * Sessão atual.
 *
 * `null` significa "não autenticado" (401 esperado), não erro. Outros erros
 * continuam sendo propagados para a tela exibir estado de falha recuperável.
 */
export function useSession() {
  const queryClient = useQueryClient()

  return useQuery<User | null>({
    queryKey: SESSION_QUERY_KEY,
    queryFn: async () => {
      try {
        const user = await authApi.me()
        markSessionEstablished()
        clearSessionExpired()
        ensureSessionClock()
        return user
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          markSessionCleared()
          markSessionExpired()
          sessionStartedAt = null
          writeSessionStartedAt(null)
          clearFinancialCache(queryClient)
          return null
        }
        throw error
      }
    },
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  })
}

/**
 * Expiração local e revalidação da sessão.
 *
 * - 30 minutos sem atividade ou 12 horas desde o login encerram a sessão no
 *   cliente e removem todo o cache financeiro;
 * - foco, `visibilitychange` e `pageshow` revalidam contra o servidor;
 * - 401, logout e troca de usuário também limpam o cache.
 */
export function useSessionExpiryListener(): void {
  const queryClient = useQueryClient()

  useEffect(() => {
    const hasSession = () => Boolean(queryClient.getQueryData(SESSION_QUERY_KEY))
    const noteActivity = () => {
      if (hasSession()) {
        lastActivityAt = Date.now()
      }
    }

    if (hasSession()) {
      ensureSessionClock()
    }

    const expireIfNeeded = () => {
      if (!hasSession()) {
        return false
      }
      const now = Date.now()
      const idle = now - lastActivityAt >= INACTIVITY_TIMEOUT_MS
      const absolute = sessionStartedAt !== null && now - sessionStartedAt >= ABSOLUTE_TIMEOUT_MS
      if (!idle && !absolute) {
        return false
      }
      markSessionExpired()
      clearSessionState(queryClient)
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT))
      return true
    }

    const revalidate = () => {
      if (expireIfNeeded() || !hasSession()) {
        return
      }
      lastActivityAt = Date.now()
      void queryClient.invalidateQueries({ queryKey: SESSION_QUERY_KEY })
    }

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        revalidate()
      }
    }

    const handleUnauthenticated = () => {
      markSessionExpired()
      clearSessionState(queryClient)
    }
    const interval = window.setInterval(expireIfNeeded, CHECK_INTERVAL_MS)

    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, noteActivity, { passive: true })
    }
    window.addEventListener('focus', revalidate)
    window.addEventListener('pageshow', revalidate)
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated)

    return () => {
      window.clearInterval(interval)
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, noteActivity)
      }
      window.removeEventListener('focus', revalidate)
      window.removeEventListener('pageshow', revalidate)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      window.removeEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated)
    }
  }, [queryClient])
}

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: LoginInput) => authApi.login(input),
    onSuccess: (user) => {
      // Troca de usuário nunca pode reaproveitar dados financeiros do anterior.
      clearSessionExpired()
      clearFinancialCache(queryClient)
      startSessionClock()
      markSessionEstablished()
      queryClient.setQueryData(SESSION_QUERY_KEY, user)
    },
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: (input: RegisterInput) => authApi.register(input),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => authApi.logout(),
    onSuccess: () => {
      clearSessionExpired()
      clearSessionState(queryClient)
      void refreshCsrfToken().catch(() => {
        // O próximo POST tenta renovar o token novamente.
      })
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (input: PasswordChangeInput) => authApi.changePassword(input),
  })
}
