import {
  Activity,
  Building2,
  FileText,
  Globe2,
  Landmark,
  Layers,
  PieChart,
  Shield,
  Tag,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react'

/**
 * Identidade visual determinística das classes de investimento.
 *
 * A cor é apoio de leitura; o nome da classe continua sendo o identificador
 * principal. Tokens de categoria são reutilizados para manter a mesma paleta do
 * Dashboard sem criar uma segunda escala cromática.
 */
interface InvestmentVisual {
  readonly icon: LucideIcon
  readonly dotClass: string
  readonly textClass: string
  readonly colorVar: string
}

const VISUALS: Record<string, InvestmentVisual> = {
  ACAO: {
    icon: TrendingUp,
    dotClass: 'bg-category-3',
    textClass: 'text-category-3',
    colorVar: 'var(--df-category-3)',
  },
  FII: {
    icon: Building2,
    dotClass: 'bg-category-1',
    textClass: 'text-category-1',
    colorVar: 'var(--df-category-1)',
  },
  FUNDO: {
    icon: PieChart,
    dotClass: 'bg-category-5',
    textClass: 'text-category-5',
    colorVar: 'var(--df-category-5)',
  },
  ETF: {
    icon: Layers,
    dotClass: 'bg-category-7',
    textClass: 'text-category-7',
    colorVar: 'var(--df-category-7)',
  },
  RENDA_FIXA: {
    icon: Landmark,
    dotClass: 'bg-category-9',
    textClass: 'text-category-9',
    colorVar: 'var(--df-category-9)',
  },
  BDR: {
    icon: Globe2,
    dotClass: 'bg-category-8',
    textClass: 'text-category-8',
    colorVar: 'var(--df-category-8)',
  },
  DERIVATIVO: {
    icon: Activity,
    dotClass: 'bg-category-4',
    textClass: 'text-category-4',
    colorVar: 'var(--df-category-4)',
  },
  TITULO: {
    icon: FileText,
    dotClass: 'bg-category-11',
    textClass: 'text-category-11',
    colorVar: 'var(--df-category-11)',
  },
  COE: {
    icon: Shield,
    dotClass: 'bg-category-10',
    textClass: 'text-category-10',
    colorVar: 'var(--df-category-10)',
  },
  OUTRO: {
    icon: Tag,
    dotClass: 'bg-category-12',
    textClass: 'text-category-12',
    colorVar: 'var(--df-category-12)',
  },
}

const FALLBACK: InvestmentVisual = {
  icon: Tag,
  dotClass: 'bg-category-neutral',
  textClass: 'text-category-neutral',
  colorVar: 'var(--df-category-neutral)',
}

export function investmentVisual(assetClass: string | null | undefined): InvestmentVisual {
  return (assetClass && VISUALS[assetClass]) || FALLBACK
}
