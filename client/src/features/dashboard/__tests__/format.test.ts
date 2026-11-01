import { describe, expect, it } from 'vitest'

import { categoryDisplayName } from '@/features/dashboard/format'

describe('nome de categoria no dashboard', () => {
  it('diferencia categorias bidirecionais pelo sentido do gráfico', () => {
    expect(categoryDisplayName('Outros', 'EXPENSE')).toBe('Outros gastos')
    expect(categoryDisplayName('Outros', 'INCOME')).toBe('Outras receitas')
    expect(categoryDisplayName('Transferências externas', 'EXPENSE')).toBe(
      'Transferências externas (saídas)',
    )
    expect(categoryDisplayName('Transferências externas', 'INCOME')).toBe(
      'Transferências externas (entradas)',
    )
    expect(categoryDisplayName('Sem categoria', 'EXPENSE')).toBe('Sem categoria (despesas)')
    expect(categoryDisplayName('Sem categoria', 'INCOME')).toBe('Sem categoria (receitas)')
  })

  it('mantém categorias exclusivas sem alteração', () => {
    expect(categoryDisplayName('Alimentação', 'EXPENSE')).toBe('Alimentação')
    expect(categoryDisplayName('Salário', 'INCOME')).toBe('Salário')
    expect(categoryDisplayName('Rendimentos de investimentos', 'INCOME')).toBe(
      'Rendimentos de investimentos',
    )
  })
})
