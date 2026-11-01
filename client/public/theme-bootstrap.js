/*
 * Resolve tema e paleta antes da primeira pintura.
 *
 * Arquivo externo (não inline) para permanecer compatível com a CSP
 * `script-src 'self'` da instalação. Lê somente preferências visuais; nenhum
 * dado financeiro é armazenado.
 */
;(function () {
  try {
    var PREFIX = 'delta.prefs'
    var active = localStorage.getItem(PREFIX + '.active') || 'guest'
    var raw = localStorage.getItem(PREFIX + '.' + active)
    var prefs = raw ? JSON.parse(raw) : {}
    var themes = ['light', 'dark', 'system']
    var accents = ['blue', 'green', 'purple', 'amber']
    var preference = themes.indexOf(prefs.theme) >= 0 ? prefs.theme : 'system'
    var accent = accents.indexOf(prefs.accent) >= 0 ? prefs.accent : 'blue'
    var resolved =
      preference === 'system'
        ? window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'dark'
          : 'light'
        : preference
    var root = document.documentElement
    root.setAttribute('data-theme', resolved)
    root.setAttribute('data-theme-preference', preference)
    root.setAttribute('data-accent', accent)
    root.style.colorScheme = resolved
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', resolved === 'dark' ? '#0a1120' : '#f5f7fb')
  } catch (error) {
    /* localStorage indisponível: mantém o padrão claro/azul do HTML. */
  }
})()
