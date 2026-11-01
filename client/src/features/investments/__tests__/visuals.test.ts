import { describe, expect, it } from 'vitest'

import { investmentVisual } from '@/features/investments/visuals'

describe('identidade visual de classes de investimento', () => {
  it('diferencia ações, fundos imobiliários e renda fixa', () => {
    const acao = investmentVisual('ACAO')
    const fii = investmentVisual('FII')
    const rendaFixa = investmentVisual('RENDA_FIXA')

    expect(acao.colorVar).not.toBe(fii.colorVar)
    expect(fii.colorVar).not.toBe(rendaFixa.colorVar)
    expect(acao.icon).not.toBe(fii.icon)
  })

  it('usa fallback neutro para classe ausente ou desconhecida', () => {
    const missing = investmentVisual(null)
    const unknown = investmentVisual('CLASSE_FUTURA')

    expect(missing.colorVar).toBe('var(--df-category-neutral)')
    expect(unknown.colorVar).toBe('var(--df-category-neutral)')
  })
})
