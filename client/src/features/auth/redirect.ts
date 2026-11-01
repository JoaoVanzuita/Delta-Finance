/**
 * Destino pós-login.
 *
 * Preserva deep links (`state.from`) apenas quando são caminhos internos
 * absolutos, evitando redirecionamento aberto para outro domínio.
 */
export function safeRedirectDestination(from: unknown): string {
  if (typeof from !== 'string' || !from) {
    return '/dashboard'
  }
  if (!from.startsWith('/') || from.startsWith('//')) {
    return '/dashboard'
  }
  return from
}
