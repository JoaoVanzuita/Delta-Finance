import { useEffect, useState } from 'react'

import { UNAUTHENTICATED_EVENT } from '@/lib/api/client'

/**
 * Aviso de sessão expirada.
 *
 * Só aparece quando uma sessão existente recebe 401; a sondagem inicial de um
 * visitante anônimo não dispara o evento.
 */
export function SessionExpiredBanner() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const handleExpired = () => setVisible(true)
    window.addEventListener(UNAUTHENTICATED_EVENT, handleExpired)
    return () => window.removeEventListener(UNAUTHENTICATED_EVENT, handleExpired)
  }, [])

  if (!visible) {
    return null
  }

  return (
    <div
      role="status"
      className="mb-6 flex flex-col gap-3 rounded-card border border-warning/30 bg-warning/5 p-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <p className="text-sm text-ink">
        Sua sessão expirou. Entre novamente para continuar de onde parou.
      </p>
      <button
        type="button"
        onClick={() => setVisible(false)}
        className="self-start rounded-card border border-line bg-surface px-3 py-1.5 text-sm font-medium text-ink transition-colors hover:bg-surface-muted sm:self-auto"
      >
        Fechar aviso
      </button>
    </div>
  )
}
