import {
  ArrowLeftRight,
  CreditCard,
  GitCompareArrows,
  Landmark,
  LayoutDashboard,
  Palette,
  Plug,
  ShieldCheck,
  Tags,
  Target,
  TrendingUp,
  UserRound,
  Wallet,
  type LucideIcon,
} from 'lucide-react'

export interface NavigationItem {
  readonly to: string
  readonly label: string
  readonly icon: LucideIcon
  readonly end?: boolean
}

export interface NavigationGroup {
  readonly label: string
  readonly items: readonly NavigationItem[]
}

/** Arquitetura da informação aprovada na Fase 0: cinco grupos, doze destinos. */
export const NAVIGATION_GROUPS: readonly NavigationGroup[] = [
  {
    label: 'Visão geral',
    items: [{ to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, end: true }],
  },
  {
    label: 'Meu dinheiro',
    items: [
      { to: '/accounts', label: 'Contas', icon: Wallet },
      { to: '/cards', label: 'Cartões', icon: CreditCard },
      { to: '/transactions', label: 'Transações', icon: ArrowLeftRight },
    ],
  },
  {
    label: 'Planejar',
    items: [
      { to: '/investments', label: 'Investimentos', icon: TrendingUp },
      { to: '/wealth', label: 'Patrimônio', icon: Landmark },
      { to: '/goals', label: 'Metas', icon: Target },
    ],
  },
  {
    label: 'Organizar',
    items: [
      { to: '/categories', label: 'Categorias', icon: Tags },
      { to: '/reconciliation', label: 'Reconciliação', icon: GitCompareArrows },
    ],
  },
  {
    label: 'Configurações',
    items: [
      { to: '/settings/preferences', label: 'Preferências', icon: Palette },
      { to: '/settings/integration', label: 'Integração', icon: Plug },
      { to: '/settings/privacy', label: 'Privacidade', icon: ShieldCheck },
      { to: '/account', label: 'Conta', icon: UserRound },
    ],
  },
]
