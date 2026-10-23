import { useEffect } from 'react'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  markSessionCleared,
  markSessionEstablished,
  refreshCsrfToken,
  UNAUTHENTICATED_EVENT,
  type User,
} from '@/lib/api/client'
import { ApiError } from '@/lib/api/problem'

import { authApi, type LoginInput, type PasswordChangeInput, type RegisterInput } from '../api'

export const SESSION_QUERY_KEY = ['session'] as const

/**
 * Sessão atual.
 *
 * `null` significa "não autenticado" (401 esperado), não erro. Outros erros
 * continuam sendo propagados para a tela exibir estado de falha recuperável.
 */
export function useSession() {
  return useQuery<User | null>({
    queryKey: SESSION_QUERY_KEY,
    queryFn: async () => {
      try {
        const user = await authApi.me()
        markSessionEstablished()
        return user
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          markSessionCleared()
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

/** Limpa o cache quando qualquer requisição recebe 401 com sessão conhecida. */
export function useSessionExpiryListener(): void {
  const queryClient = useQueryClient()

  useEffect(() => {
    const handleUnauthenticated = () => {
      markSessionCleared()
      queryClient.setQueryData(SESSION_QUERY_KEY, null)
    }
    window.addEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated)
    return () => window.removeEventListener(UNAUTHENTICATED_EVENT, handleUnauthenticated)
  }, [queryClient])
}

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: LoginInput) => authApi.login(input),
    onSuccess: (user) => {
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
      markSessionCleared()
      // Nenhum dado financeiro deve permanecer em cache após sair.
      queryClient.clear()
      queryClient.setQueryData(SESSION_QUERY_KEY, null)
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
