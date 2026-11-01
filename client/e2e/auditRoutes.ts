/**
 * Rotas cobertas pela auditoria final (axe + responsividade).
 *
 * A lista inclui telas públicas, rotas autenticadas, detalhes dinâmicos e
 * configurações. A fonte de dados é o banco fictício de `screenshots/`.
 */
export interface AuditRoute {
  readonly path: string
  readonly name: string
  /** `false` simula visitante sem sessão. */
  readonly authenticated?: boolean
}

export const AUDIT_ROUTES: readonly AuditRoute[] = [
  { path: '/', name: 'home pública', authenticated: false },
  { path: '/login', name: 'login', authenticated: false },
  { path: '/register', name: 'cadastro', authenticated: false },
  { path: '/rota-inexistente', name: '404 público', authenticated: false },
  { path: '/dashboard', name: 'dashboard' },
  { path: '/accounts', name: 'contas' },
  { path: '/accounts/acc-cash', name: 'detalhe de conta manual' },
  { path: '/accounts/acc-bank', name: 'detalhe de conta importada' },
  { path: '/accounts/new', name: 'nova conta manual' },
  { path: '/accounts/acc-cash/edit', name: 'editar conta manual' },
  { path: '/cards', name: 'cartões' },
  { path: '/cards/acc-card', name: 'detalhe do cartão' },
  { path: '/cards/acc-card/invoices', name: 'faturas do cartão' },
  { path: '/invoices/inv-open', name: 'detalhe da fatura aberta' },
  { path: '/invoices/inv-paid', name: 'detalhe da fatura paga' },
  { path: '/transactions', name: 'transações' },
  { path: '/transactions/new', name: 'novo lançamento' },
  { path: '/categories', name: 'categorias' },
  { path: '/reconciliation', name: 'reconciliação' },
  { path: '/investments', name: 'investimentos' },
  { path: '/wealth', name: 'patrimônio' },
  { path: '/goals', name: 'metas' },
  { path: '/settings/preferences', name: 'preferências' },
  { path: '/settings/integration', name: 'integração' },
  { path: '/settings/privacy', name: 'privacidade' },
  { path: '/account', name: 'conta' },
  { path: '/rota-inexistente', name: '404 autenticado' },
]
