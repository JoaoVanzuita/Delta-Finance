import { describe, expect, it } from 'vitest'

import { buildDonutGradient } from '@/lib/donut'

describe('buildDonutGradient', () => {
  it('distribui os segmentos proporcionalmente ao total', () => {
    expect(
      buildDonutGradient(
        [
          { value: 75, color: 'var(--a)' },
          { value: 25, color: 'var(--b)' },
        ],
        100,
      ),
    ).toBe('conic-gradient(var(--a) 0.000% 75.000%, var(--b) 75.000% 100.000%)')
  })

  it('preenche o restante com a superfície quando a soma não fecha 100%', () => {
    expect(buildDonutGradient([{ value: 50, color: 'var(--a)' }], 100)).toBe(
      'conic-gradient(var(--a) 0.000% 50.000%, var(--df-surface-muted) 50.000% 100%)',
    )
  })

  it('trata total zero, valores inválidos e negativos', () => {
    expect(buildDonutGradient([{ value: 10, color: 'var(--a)' }], 0)).toBe(
      'var(--df-surface-muted)',
    )
    expect(
      buildDonutGradient(
        [
          { value: -10, color: 'var(--a)' },
          { value: Number.NaN, color: 'var(--b)' },
        ],
        100,
      ),
    ).toBe('conic-gradient(var(--df-surface-muted) 0.000% 100%)')
  })

  it('não ultrapassa 100% quando a soma dos segmentos é maior que o total', () => {
    const gradient = buildDonutGradient(
      [
        { value: 80, color: 'var(--a)' },
        { value: 80, color: 'var(--b)' },
      ],
      100,
    )
    expect(gradient).toContain('var(--a) 0.000% 80.000%')
    expect(gradient).toContain('var(--b) 80.000% 100.000%')
    expect(gradient).not.toContain('100.000% 160.000%')
  })
})
