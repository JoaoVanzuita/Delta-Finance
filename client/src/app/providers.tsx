import { useState } from 'react'
import type { ReactNode } from 'react'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * Cria o cache de estado de servidor.
 *
 * Regras: nenhum segredo ou dado financeiro persistido no cliente; retry
 * conservador para não amplificar falhas do backend; janela curta de
 * "frescor" porque totais financeiros são sempre recalculados no servidor.
 */
function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        gcTime: 5 * 60_000,
      },
      mutations: {
        retry: 0,
      },
    },
  })
}

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient)

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
