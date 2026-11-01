import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatNumber,
  formatPercent,
  formatRatioAsPercent,
  formatRelativeDate,
  formatSignedMoney,
  isDecimalString,
} from '@/lib/format'

describe('formatação compartilhada pt-BR', () => {
  it('formata BRL sem converter o valor inteiro para float', () => {
    expect(formatMoney('1234.56', 'BRL')).toBe('R$\u00a01.234,56')
    expect(formatMoney('0.10', 'BRL')).toBe('R$\u00a00,10')
    expect(formatMoney('12345678901234567890.99', 'BRL')).toBe(
      'R$\u00a012.345.678.901.234.567.890,99',
    )
  })

  it('formata negativos com sinal de menos tipográfico', () => {
    expect(formatMoney('-1234.56', 'BRL')).toBe('−R$\u00a01.234,56')
    expect(formatSignedMoney('10.00', 'BRL')).toBe('+R$\u00a010,00')
  })

  it('exibe moeda estrangeira com o símbolo correto, nunca R$', () => {
    const usd = formatMoney('1234.56', 'USD')
    expect(usd).toContain('US$')
    expect(usd).not.toContain('R$')
    expect(formatMoney('10.00', 'EUR')).not.toContain('R$')
  })

  it('não inventa valor para entrada inválida', () => {
    expect(formatMoney(null)).toBe('Não informado')
    expect(formatMoney('abc', 'USD')).toBe('abc USD')
    expect(isDecimalString('10.50')).toBe(true)
    expect(isDecimalString('10,50')).toBe(false)
  })

  it('formata percentuais com uma casa e sem zeros desnecessários', () => {
    expect(formatPercent('12.3')).toBe('12,3%')
    expect(formatPercent('50.00')).toBe('50%')
    expect(formatPercent('3.25')).toBe('3,3%')
    expect(formatRatioAsPercent('0.5')).toBe('50%')
    expect(formatRatioAsPercent('0.123')).toBe('12,3%')
  })

  it('formata números com agrupamento e casas controladas', () => {
    expect(formatNumber('1234.5', { maximumFractionDigits: 2 })).toBe('1.234,5')
    expect(formatNumber('1234', { useGrouping: false })).toBe('1234')
    expect(formatNumber('12.34567', { minimumFractionDigits: 2, maximumFractionDigits: 4 })).toBe(
      '12,3457',
    )
  })

  it('formata datas civis e timestamps em America/Sao_Paulo', () => {
    expect(formatDate('2026-10-30')).toBe('30/10/2026')
    expect(formatDateTime('2026-10-30T15:30:00Z')).toBe('30/10/2026, 12:30')
    expect(formatDate(null)).toBe('Data não informada')
    expect(formatDate('0000-00-00')).toBe('0000-00-00')
  })

  it('usa datas relativas apenas quando não há ambiguidade', () => {
    const now = new Date('2026-10-30T12:00:00Z')
    expect(formatRelativeDate('2026-10-30', now)).toBe('hoje')
    expect(formatRelativeDate('2026-10-29', now)).toBe('ontem')
    expect(formatRelativeDate('2026-10-27', now)).toBe('há 3 dias')
    expect(formatRelativeDate('2026-11-02', now)).toBe('em 3 dias')
    expect(formatRelativeDate('2026-08-01', now)).toBe('01/08/2026')
  })
})
