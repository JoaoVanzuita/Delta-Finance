import { describe, expect, it } from 'vitest'

import { addDecimals, subtractDecimals, toFiniteNumber } from '@/lib/format'

describe('agregação decimal de apresentação', () => {
  it('soma sem perder precisão de centavos', () => {
    expect(addDecimals(['0.10', '0.20'])).toBe('0.30')
    expect(addDecimals(['1000.00', '-610.00'])).toBe('390.00')
    expect(addDecimals(['1.005', '2.01'])).toBe('3.015')
  })

  it('preserva sinal negativo e normaliza escala', () => {
    expect(addDecimals(['-5.00', '-2.50'])).toBe('-7.50')
    expect(addDecimals(['5', '-7.5'])).toBe('-2.50')
    expect(addDecimals([])).toBe('0.00')
  })

  it('rejeita valores não decimais na agregação visual', () => {
    expect(() => addDecimals(['não informado'])).toThrow(/decimal inválido/i)
  })

  it('converte para número apenas quando seguro para geometria', () => {
    expect(toFiniteNumber('12.5')).toBe(12.5)
    expect(toFiniteNumber('não informado')).toBe(0)
  })

  it('calcula diferença entre pernas sem float', () => {
    expect(subtractDecimals('100.00', '100.00')).toBe('0.00')
    expect(subtractDecimals('100.00', '95.50')).toBe('4.50')
    expect(subtractDecimals('100.00', '-20.00')).toBe('120.00')
  })
})
