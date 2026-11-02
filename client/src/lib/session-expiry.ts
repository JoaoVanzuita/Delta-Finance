/**
 * Aviso de sessão expirada de uso único.
 *
 * O evento `delta:unauthenticated` pode disparar antes de a tela de login montar. O flag em
 * `sessionStorage` faz o aviso sobreviver ao redirect; ele é limpo ao ser dispensado, no logout ou
 * em um novo login.
 */
const KEY = 'delta.session.expired'

export function markSessionExpired(): void {
  try {
    window.sessionStorage.setItem(KEY, '1')
  } catch {
    // sessionStorage indisponível: o aviso fica restrito à tela atual.
  }
}

export function hasSessionExpired(): boolean {
  try {
    return window.sessionStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function clearSessionExpired(): void {
  try {
    window.sessionStorage.removeItem(KEY)
  } catch {
    // idem
  }
}
