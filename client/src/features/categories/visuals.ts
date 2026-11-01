import {
  ArrowLeftRight,
  BriefcaseBusiness,
  Car,
  Gamepad2,
  GraduationCap,
  HeartPulse,
  Home,
  PiggyBank,
  ShoppingBag,
  Tag,
  TrendingUp,
  Utensils,
  Wrench,
  type LucideIcon,
} from 'lucide-react'

/**
 * Identidade visual determinística de categorias.
 *
 * A API atual não expõe `predefinedKey`; portanto, categorias predefinidas são
 * reconhecidas pelo nome canônico e as demais usam um hash estável do `id`.
 * A cor é apoio de leitura — nome, ícone e sinal continuam obrigatórios.
 */

export const CATEGORY_SLOTS = 12

interface SlotDefinition {
  readonly name: string
  readonly icon: LucideIcon
  readonly dotClass: string
  readonly textClass: string
  readonly borderClass: string
  readonly colorVar: string
}

const SLOTS: readonly SlotDefinition[] = [
  {
    name: 'Moradia',
    icon: Home,
    dotClass: 'bg-category-1',
    textClass: 'text-category-1',
    borderClass: 'border-category-1',
    colorVar: 'var(--df-category-1)',
  },
  {
    name: 'Alimentação',
    icon: Utensils,
    dotClass: 'bg-category-2',
    textClass: 'text-category-2',
    borderClass: 'border-category-2',
    colorVar: 'var(--df-category-2)',
  },
  {
    name: 'Transporte',
    icon: Car,
    dotClass: 'bg-category-3',
    textClass: 'text-category-3',
    borderClass: 'border-category-3',
    colorVar: 'var(--df-category-3)',
  },
  {
    name: 'Saúde',
    icon: HeartPulse,
    dotClass: 'bg-category-4',
    textClass: 'text-category-4',
    borderClass: 'border-category-4',
    colorVar: 'var(--df-category-4)',
  },
  {
    name: 'Educação',
    icon: GraduationCap,
    dotClass: 'bg-category-5',
    textClass: 'text-category-5',
    borderClass: 'border-category-5',
    colorVar: 'var(--df-category-5)',
  },
  {
    name: 'Lazer',
    icon: Gamepad2,
    dotClass: 'bg-category-6',
    textClass: 'text-category-6',
    borderClass: 'border-category-6',
    colorVar: 'var(--df-category-6)',
  },
  {
    name: 'Compras',
    icon: ShoppingBag,
    dotClass: 'bg-category-7',
    textClass: 'text-category-7',
    borderClass: 'border-category-7',
    colorVar: 'var(--df-category-7)',
  },
  {
    name: 'Serviços',
    icon: Wrench,
    dotClass: 'bg-category-8',
    textClass: 'text-category-8',
    borderClass: 'border-category-8',
    colorVar: 'var(--df-category-8)',
  },
  {
    name: 'Salário',
    icon: BriefcaseBusiness,
    dotClass: 'bg-category-9',
    textClass: 'text-category-9',
    borderClass: 'border-category-9',
    colorVar: 'var(--df-category-9)',
  },
  {
    name: 'Investimentos',
    icon: TrendingUp,
    dotClass: 'bg-category-10',
    textClass: 'text-category-10',
    borderClass: 'border-category-10',
    colorVar: 'var(--df-category-10)',
  },
  {
    name: 'Transferências',
    icon: ArrowLeftRight,
    dotClass: 'bg-category-11',
    textClass: 'text-category-11',
    borderClass: 'border-category-11',
    colorVar: 'var(--df-category-11)',
  },
  {
    name: 'Outros',
    icon: PiggyBank,
    dotClass: 'bg-category-12',
    textClass: 'text-category-12',
    borderClass: 'border-category-12',
    colorVar: 'var(--df-category-12)',
  },
]

const FALLBACK: SlotDefinition = {
  name: 'Outros',
  icon: Tag,
  dotClass: 'bg-category-neutral',
  textClass: 'text-category-neutral',
  borderClass: 'border-category-neutral',
  colorVar: 'var(--df-category-neutral)',
}

export interface CategoryVisual {
  readonly icon: LucideIcon
  readonly dotClass: string
  readonly textClass: string
  readonly borderClass: string
  readonly colorVar: string
  readonly slot: number
  /** Verdadeiro para a categoria sintética “Sem categoria”. */
  readonly uncategorized: boolean
}

const UNCATEGORIZED_ID = '00000000-0000-0000-0000-000000000000'

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase()
}

const KNOWN_NAMES = new Map<string, number>([
  ...SLOTS.map((slot, index): [string, number] => [normalize(slot.name), index + 1]),
  // Rendimentos compartilha a identidade visual de Investimentos, mas é
  // uma categoria de receita distinta.
  ['rendimentos de investimentos', 10],
  ['rendimentos', 10],
  ['aportes', 10],
  ['resgates', 10],
  // Transferências externas compartilham o ícone/cor de transferências, mas
  // aparecem em receitas/despesas conforme a direção.
  ['transferencias externas', 11],
])

/** FNV-1a de 32 bits: estável entre sessões e navegadores. */
function hash(value: string): number {
  let result = 0x811c9dc5
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index)
    result = Math.imul(result, 0x01000193)
  }
  return result >>> 0
}

export function categoryVisual(input: {
  readonly id: string
  readonly name: string
}): CategoryVisual {
  if (input.id === UNCATEGORIZED_ID || normalize(input.name) === 'sem categoria') {
    return {
      icon: Tag,
      dotClass: FALLBACK.dotClass,
      textClass: FALLBACK.textClass,
      borderClass: FALLBACK.borderClass,
      colorVar: FALLBACK.colorVar,
      slot: 0,
      uncategorized: true,
    }
  }

  const known = KNOWN_NAMES.get(normalize(input.name))
  const slot = known ?? (hash(input.id) % CATEGORY_SLOTS) + 1
  const definition = SLOTS[slot - 1] ?? FALLBACK
  return {
    // Categorias personalizadas usam cor determinística, mas ícone neutro.
    icon: known ? definition.icon : Tag,
    dotClass: definition.dotClass,
    textClass: definition.textClass,
    borderClass: definition.borderClass,
    colorVar: definition.colorVar,
    slot,
    uncategorized: false,
  }
}

export function otherCategoryVisual(): CategoryVisual {
  return {
    icon: Tag,
    dotClass: FALLBACK.dotClass,
    textClass: FALLBACK.textClass,
    borderClass: FALLBACK.borderClass,
    colorVar: FALLBACK.colorVar,
    slot: 0,
    uncategorized: false,
  }
}
