import { describe, expect, it } from 'vitest'

import { categoryVisual, otherCategoryVisual } from '@/features/categories/visuals'

describe('identidade visual de categorias', () => {
  it('reconhece categorias predefinidas pelo nome canônico', () => {
    const moradia = categoryVisual({ id: 'qualquer-id', name: 'Moradia' })
    const alimentacao = categoryVisual({ id: 'outro-id', name: 'Alimentação' })
    expect(moradia.slot).toBe(1)
    expect(alimentacao.slot).toBe(2)
    expect(moradia.dotClass).not.toBe(alimentacao.dotClass)
    expect(categoryVisual({ id: 'r', name: 'Rendimentos de investimentos' }).slot).toBe(10)
    expect(categoryVisual({ id: 'a', name: 'Aportes' }).slot).toBe(10)
    expect(categoryVisual({ id: 'g', name: 'Resgates' }).slot).toBe(10)
    expect(categoryVisual({ id: 't', name: 'Transferências externas' }).slot).toBe(11)
  })

  it('é determinística para categorias personalizadas', () => {
    const first = categoryVisual({ id: 'categoria-personalizada-1', name: 'Feira' })
    const second = categoryVisual({ id: 'categoria-personalizada-1', name: 'Feira' })
    const renamed = categoryVisual({ id: 'categoria-personalizada-1', name: 'Mercado' })
    expect(first.slot).toBe(second.slot)
    expect(first.slot).toBe(renamed.slot)
    expect(first.slot).toBeGreaterThanOrEqual(1)
    expect(first.slot).toBeLessThanOrEqual(12)
  })

  it('trata a categoria sem categoria como neutra e distinta', () => {
    const uncategorized = categoryVisual({
      id: '00000000-0000-0000-0000-000000000000',
      name: 'Sem categoria',
    })
    expect(uncategorized.uncategorized).toBe(true)
    expect(uncategorized.slot).toBe(0)
    expect(uncategorized.dotClass).toBe('bg-category-neutral')
  })

  it('usa um tom neutro para o agrupamento Outras', () => {
    const other = otherCategoryVisual()
    expect(other.slot).toBe(0)
    expect(other.dotClass).toBe('bg-category-neutral')
  })
})
