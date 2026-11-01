/**
 * Registro e expansão das capturas do roteiro de screenshots.
 *
 * Cada entrada corresponde a uma tela (rota) ou a um estado de componente
 * (diálogo, drawer, dropdown, formulário inválido, carregamento etc.). A matriz
 * combina viewports e temas exigidos pela Fase 6 sem multiplicar estados
 * interativos por todas as combinações.
 */
import type { Page } from '@playwright/test'

export type ViewportId = 'desktop' | 'desktop-wide' | 'tablet' | 'mobile-sm' | 'mobile'
export type ThemeMode = 'light' | 'dark'
export type FixtureMode = 'default' | 'empty' | 'error'

export interface ViewportPreset {
  readonly id: ViewportId
  readonly label: string
  readonly width: number
  readonly height: number
}

export const VIEWPORTS: Record<ViewportId, ViewportPreset> = {
  desktop: { id: 'desktop', label: '1280', width: 1280, height: 900 },
  'desktop-wide': { id: 'desktop-wide', label: '1440', width: 1440, height: 900 },
  tablet: { id: 'tablet', label: '768', width: 768, height: 1024 },
  'mobile-sm': { id: 'mobile-sm', label: '360', width: 360, height: 800 },
  mobile: { id: 'mobile', label: '390', width: 390, height: 844 },
}

export interface Capture {
  /** Nome de arquivo base (sem extensão), prefixado para ordenar a galeria. */
  readonly slug: string
  /** Título legível exibido na galeria. */
  readonly name: string
  /** Rota a visitar. */
  readonly route: string
  /** Abre um estado de componente antes do screenshot (opcional). */
  readonly setup?: (page: Page) => Promise<void>
  /** `false` para capturar apenas a viewport (diálogos/overlays). */
  readonly fullPage?: boolean
  /** Rota normal ou estado de componente. */
  readonly variantMode?: 'route' | 'state'
  /** Adiciona a variante 1280×900 no tema escuro. */
  readonly dark?: boolean
  /** Banco de dados alternativo para estados vazios/erro. */
  readonly fixture?: FixtureMode
  /** Rotas com resposta atrasada para capturar carregamento. */
  readonly delayPaths?: readonly string[]
  /** Rotas com erro controlado. */
  readonly failPaths?: readonly string[]
  /** `immediate` não espera a rede ociosa (loading/submitting). */
  readonly settle?: 'network' | 'immediate'
  /** `false` simula visitante sem sessão (Home e 404 públicos). */
  readonly authenticated?: boolean
}

export interface CaptureVariant extends Capture {
  readonly key: string
  readonly file: string
  readonly renderName: string
  readonly viewport: ViewportPreset
  readonly theme: ThemeMode
}

const ROUTE_POINTS: ReadonlyArray<{ viewport: ViewportId; theme: ThemeMode }> = [
  { viewport: 'desktop', theme: 'light' },
  { viewport: 'desktop-wide', theme: 'light' },
  { viewport: 'tablet', theme: 'light' },
  { viewport: 'mobile-sm', theme: 'light' },
  { viewport: 'mobile', theme: 'light' },
]

const STATE_POINTS: ReadonlyArray<{ viewport: ViewportId; theme: ThemeMode }> = [
  { viewport: 'desktop', theme: 'light' },
  { viewport: 'mobile', theme: 'light' },
]

const THEME_LABELS: Record<ThemeMode, string> = { light: 'claro', dark: 'escuro' }

function variantSuffix(viewport: ViewportPreset, theme: ThemeMode): string {
  if (viewport.id === 'desktop' && theme === 'light') return ''
  return `--${viewport.label}-${theme}`
}

function renderName(name: string, viewport: ViewportPreset, theme: ThemeMode): string {
  if (viewport.id === 'desktop' && theme === 'light') return name
  return `${name} (${viewport.label} · ${THEME_LABELS[theme]})`
}

/** Expande a matriz de rotas/estados em variantes determinísticas. */
export function expandCaptures(captures: readonly Capture[] = CAPTURES): CaptureVariant[] {
  const variants: CaptureVariant[] = []
  for (const capture of captures) {
    const mode = capture.variantMode ?? 'route'
    const points = [...(mode === 'route' ? ROUTE_POINTS : STATE_POINTS)]
    if (capture.dark) {
      points.push({ viewport: 'desktop', theme: 'dark' })
    }
    for (const point of points) {
      const viewport = VIEWPORTS[point.viewport]
      const suffix = variantSuffix(viewport, point.theme)
      variants.push({
        ...capture,
        key: `${capture.slug}${suffix}`,
        file: `${capture.slug}${suffix}.png`,
        renderName: renderName(capture.name, viewport, point.theme),
        viewport,
        theme: point.theme,
      })
    }
  }
  return variants
}

export const CAPTURES: readonly Capture[] = [
  // ── Telas públicas ────────────────────────────────────────────────
  { slug: '01-home', name: 'Home pública', route: '/', dark: true, authenticated: false },
  { slug: '02-login', name: 'Login', route: '/login', dark: true, authenticated: false },
  { slug: '03-register', name: 'Cadastro', route: '/register', authenticated: false },
  { slug: '04-not-found', name: 'Página não encontrada (autenticada)', route: '/rota-inexistente' },
  {
    slug: '04-not-found-public',
    name: 'Página não encontrada (visitante)',
    route: '/rota-inexistente',
    variantMode: 'state',
    authenticated: false,
  },
  {
    slug: '02-login-validation',
    name: 'Login — validação',
    route: '/login',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Entrar' }).click()
      await page.locator('#login-username-error').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '02-login-submitting',
    name: 'Login — ação em andamento',
    route: '/login',
    variantMode: 'state',
    fullPage: false,
    delayPaths: ['/auth/login'],
    settle: 'immediate',
    setup: async (page) => {
      await page.getByLabel('Nome de usuário').fill('alice.familia')
      await page.getByLabel('Senha').fill('senha-de-teste-123')
      await page.getByRole('button', { name: 'Entrar' }).click()
      await page.getByRole('button', { name: 'Entrando…' }).waitFor({ timeout: 5000 })
    },
  },

  // ── Dashboard ─────────────────────────────────────────────────────
  { slug: '10-dashboard', name: 'Dashboard', route: '/dashboard', dark: true },
  {
    slug: '10-dashboard-income-categories',
    name: 'Dashboard — receitas por categoria',
    route: '/dashboard',
    variantMode: 'state',
    setup: async (page) => {
      await page.getByRole('tab', { name: 'Receitas' }).click()
      await page.getByText('Total de receitas no período').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '10-dashboard-drilldown',
    name: 'Dashboard — drill-down por categoria',
    route: '/dashboard',
    variantMode: 'state',
    fullPage: false,
    dark: true,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Feira' }).first().click()
      await page.getByText('Categoria: Feira').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '10-dashboard-loading',
    name: 'Dashboard — carregando',
    route: '/dashboard',
    variantMode: 'state',
    fullPage: false,
    settle: 'immediate',
    delayPaths: [
      '/dashboard/summary',
      '/dashboard/categories',
      '/dashboard/timeseries',
      '/dashboard/entries',
    ],
  },
  {
    slug: '10-dashboard-empty',
    name: 'Dashboard — período sem dados',
    route: '/dashboard',
    variantMode: 'state',
    fixture: 'empty',
  },
  {
    slug: '10-dashboard-error',
    name: 'Dashboard — erro de carregamento',
    route: '/dashboard',
    variantMode: 'state',
    failPaths: ['/dashboard/summary'],
    setup: async (page) => {
      await page.getByText('Não foi possível carregar o dashboard').waitFor({ timeout: 10_000 })
    },
  },

  // ── Contas ────────────────────────────────────────────────────────
  { slug: '11-accounts', name: 'Contas', route: '/accounts', dark: true },
  { slug: '12-account-manual', name: 'Detalhe da conta manual', route: '/accounts/acc-cash' },
  { slug: '13-account-imported', name: 'Detalhe da conta importada', route: '/accounts/acc-bank' },
  {
    slug: '13-account-imported-rename',
    name: 'Detalhe da conta importada — renomear',
    route: '/accounts/acc-bank',
    variantMode: 'state',
    setup: async (page) => {
      await page.getByRole('tab', { name: 'Configurações' }).click()
      await page.getByRole('button', { name: 'Renomear' }).first().click()
      await page.getByLabel('Nome de exibição').waitFor({ timeout: 5000 })
    },
  },
  { slug: '14-account-card', name: 'Detalhe do cartão (conta)', route: '/accounts/acc-card' },
  { slug: '15-account-new', name: 'Nova conta manual', route: '/accounts/new' },
  { slug: '16-account-edit', name: 'Editar conta manual', route: '/accounts/acc-cash/edit' },

  // ── Cartões e faturas ─────────────────────────────────────────────
  { slug: '17-cards', name: 'Cartões de crédito', route: '/cards', dark: true },
  { slug: '18-card-detail', name: 'Detalhe do cartão', route: '/cards/acc-card' },
  { slug: '19-invoices', name: 'Faturas do cartão', route: '/cards/acc-card/invoices' },
  {
    slug: '20-invoice-open',
    name: 'Detalhe da fatura aberta',
    route: '/invoices/inv-open',
    dark: true,
  },
  {
    slug: '20-invoice-open-item-dialog',
    name: 'Detalhe da fatura — adicionar item',
    route: '/invoices/inv-open',
    variantMode: 'state',
    fullPage: false,
    dark: true,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Adicionar item' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '20-invoice-open-payment-dialog',
    name: 'Detalhe da fatura — registrar pagamento',
    route: '/invoices/inv-open',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Registrar pagamento' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '20-invoice-open-reconcile-dialog',
    name: 'Detalhe da fatura — conciliar com a Bill',
    route: '/invoices/inv-open',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Conciliar fatura' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  { slug: '21-invoice-paid', name: 'Detalhe da fatura paga', route: '/invoices/inv-paid' },

  // ── Transações ────────────────────────────────────────────────────
  { slug: '22-transactions', name: 'Transações', route: '/transactions', dark: true },
  {
    slug: '22-transactions-actions',
    name: 'Transações — menu de ações',
    route: '/transactions',
    variantMode: 'state',
    fullPage: false,
    dark: true,
    setup: async (page) => {
      await page
        .getByRole('button', { name: /Ações de/ })
        .first()
        .click()
      await page.getByRole('dialog', { name: /Ações de/ }).waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '22-transactions-detail',
    name: 'Transações — detalhes no drawer',
    route: '/transactions',
    variantMode: 'state',
    fullPage: false,
    dark: true,
    setup: async (page) => {
      await page
        .getByRole('button', { name: /Ações de/ })
        .first()
        .click()
      await page.getByRole('button', { name: 'Ver detalhes' }).click()
      await page.getByRole('dialog', { name: 'Detalhes da transação' }).waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '22-transactions-correction',
    name: 'Transações — corrigir lançamento',
    route: '/transactions',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page
        .getByRole('button', { name: /Ações de/ })
        .first()
        .click()
      await page.getByRole('button', { name: 'Corrigir transação' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  { slug: '23-transaction-new', name: 'Novo lançamento manual', route: '/transactions/new' },
  {
    slug: '23-transaction-new-imported-account',
    name: 'Novo lançamento — conta importada',
    route: '/transactions/new',
    variantMode: 'state',
    setup: async (page) => {
      await page.getByLabel('Conta').selectOption({ label: 'Conta Corrente' })
      await page.getByText(/o saldo do provedor não é ajustado/i).waitFor({ timeout: 5000 })
    },
  },

  // ── Categorias e reconciliação ────────────────────────────────────
  { slug: '24-categories', name: 'Categorias', route: '/categories' },
  {
    slug: '24-categories-edit',
    name: 'Categorias — editar linha',
    route: '/categories',
    variantMode: 'state',
    setup: async (page) => {
      await page.getByRole('button', { name: 'Editar' }).first().click()
      await page.getByRole('button', { name: 'Salvar categoria' }).waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '24-categories-deactivate',
    name: 'Categorias — desativar',
    route: '/categories',
    variantMode: 'state',
    setup: async (page) => {
      await page.getByRole('button', { name: 'Desativar' }).first().click()
      await page.getByText('Confirmar desativação').waitFor({ timeout: 5000 })
    },
  },
  { slug: '25-reconciliation', name: 'Reconciliação', route: '/reconciliation' },

  // ── Investimentos, patrimônio e metas ─────────────────────────────
  { slug: '26-investments', name: 'Investimentos', route: '/investments', dark: true },
  {
    slug: '26-investments-allocation-class',
    name: 'Investimentos — posições da classe',
    route: '/investments',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: /Fundos imobiliários:/ }).click()
      await page.getByRole('dialog', { name: 'Fundos imobiliários' }).waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '26-investments-positions',
    name: 'Investimentos — posições',
    route: '/investments?tab=positions',
  },
  {
    slug: '26-investments-income',
    name: 'Investimentos — proventos',
    route: '/investments?tab=income',
  },
  {
    slug: '26-investments-holding-details',
    name: 'Investimentos — detalhes da posição',
    route: '/investments',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('tab', { name: 'Posições' }).click()
      await page.getByRole('button', { name: 'Ver detalhes' }).first().click()
      await page.getByLabel('Custo informado (BRL)').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '26-investments-income-form',
    name: 'Investimentos — registrar provento manual',
    route: '/investments',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('tab', { name: 'Proventos' }).click()
      await page.getByRole('button', { name: 'Registrar provento' }).click()
      await page.getByLabel('Posição').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '26-investments-empty',
    name: 'Investimentos — sem posições',
    route: '/investments',
    variantMode: 'state',
    fixture: 'empty',
  },
  { slug: '27-wealth', name: 'Patrimônio', route: '/wealth', dark: true },
  {
    slug: '27-wealth-empty',
    name: 'Patrimônio — sem dados',
    route: '/wealth',
    variantMode: 'state',
    fixture: 'empty',
  },
  { slug: '28-goals', name: 'Metas', route: '/goals', dark: true },
  {
    slug: '28-goals-history',
    name: 'Metas — histórico de progresso',
    route: '/goals',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Ver detalhes' }).first().click()
      await page.getByText('Histórico de progresso').click()
      await page
        .getByText(/Registro sob demanda/)
        .first()
        .waitFor({ timeout: 5000 })
    },
  },

  // ── Configurações e privacidade ───────────────────────────────────
  { slug: '29-preferences', name: 'Preferências', route: '/settings/preferences', dark: true },
  {
    slug: '29-integration',
    name: 'Integração (Meu Pluggy)',
    route: '/settings/integration',
    dark: true,
  },
  { slug: '30-privacy', name: 'Privacidade e dados', route: '/settings/privacy', dark: true },
  {
    slug: '30-privacy-disconnect-dialog',
    name: 'Privacidade — desconectar integração',
    route: '/settings/privacy',
    variantMode: 'state',
    fullPage: false,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Desconectar integração' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  {
    slug: '30-privacy-delete-dialog',
    name: 'Privacidade — excluir conta',
    route: '/settings/privacy',
    variantMode: 'state',
    fullPage: false,
    dark: true,
    setup: async (page) => {
      await page.getByRole('button', { name: 'Excluir minha conta' }).click()
      await page.getByRole('dialog').waitFor({ timeout: 5000 })
    },
  },
  { slug: '31-account-settings', name: 'Configurações da conta', route: '/account', dark: true },
]
