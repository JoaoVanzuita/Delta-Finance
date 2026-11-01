export interface DonutSegment {
  readonly value: number
  readonly color: string
}

/**
 * Monta o `conic-gradient` de um donut a partir de valores numéricos.
 *
 * O total é explícito para que a geometria continue alinhada ao total
 * autoritativo do backend mesmo quando a soma dos segmentos tem arredondamento.
 * Valores ausentes ou negativos são tratados como zero.
 */
export function buildDonutGradient(segments: readonly DonutSegment[], total: number): string {
  if (!Number.isFinite(total) || total <= 0) {
    return 'var(--df-surface-muted)'
  }

  let cursor = 0
  const parts: string[] = []
  for (const segment of segments) {
    const value = Number.isFinite(segment.value) ? Math.max(0, segment.value) : 0
    const percentage = (value / total) * 100
    const start = cursor
    const end = Math.min(100, cursor + percentage)
    if (end > start) {
      parts.push(`${segment.color} ${start.toFixed(3)}% ${end.toFixed(3)}%`)
    }
    cursor = end
  }
  if (cursor < 99.999) {
    parts.push(`var(--df-surface-muted) ${cursor.toFixed(3)}% 100%`)
  }
  return `conic-gradient(${parts.join(', ')})`
}
