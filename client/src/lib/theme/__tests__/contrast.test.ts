import { describe, expect, it } from 'vitest'

/**
 * Pares de contraste críticos do sistema.
 *
 * Os valores espelham `tokens.css`; axe cobre a UI renderizada e este teste
 * impede regressão silenciosa ao ajustar uma paleta.
 */
const PAIRS: ReadonlyArray<{ name: string; foreground: string; background: string; min: number }> =
  [
    { name: 'claro/azul primário', foreground: '#ffffff', background: '#1d4ed8', min: 4.5 },
    { name: 'claro/verde primário', foreground: '#ffffff', background: '#047857', min: 4.5 },
    { name: 'claro/roxo primário', foreground: '#ffffff', background: '#6d28d9', min: 4.5 },
    { name: 'claro/âmbar primário', foreground: '#ffffff', background: '#b45309', min: 4.5 },
    { name: 'escuro/azul primário', foreground: '#071426', background: '#60a5fa', min: 4.5 },
    { name: 'escuro/verde primário', foreground: '#04211a', background: '#34d399', min: 4.5 },
    { name: 'escuro/roxo primário', foreground: '#140a2e', background: '#a78bfa', min: 4.5 },
    { name: 'escuro/âmbar primário', foreground: '#1c1204', background: '#fbbf24', min: 4.5 },
    {
      name: 'claro texto sobre superfície',
      foreground: '#0f172a',
      background: '#ffffff',
      min: 4.5,
    },
    {
      name: 'claro secundário sobre superfície',
      foreground: '#475569',
      background: '#ffffff',
      min: 4.5,
    },
    {
      name: 'escuro texto sobre superfície',
      foreground: '#f1f5f9',
      background: '#111a2a',
      min: 4.5,
    },
    {
      name: 'escuro secundário sobre superfície',
      foreground: '#a7b4c6',
      background: '#111a2a',
      min: 4.5,
    },
    { name: 'claro foco sobre superfície', foreground: '#0284c7', background: '#ffffff', min: 3 },
    { name: 'escuro foco sobre superfície', foreground: '#7dd3fc', background: '#111a2a', min: 3 },
  ]

function channel(value: number): number {
  const normalized = value / 255
  return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
}

function luminance(hex: string): number {
  const clean = hex.replace('#', '')
  const red = Number.parseInt(clean.slice(0, 2), 16)
  const green = Number.parseInt(clean.slice(2, 4), 16)
  const blue = Number.parseInt(clean.slice(4, 6), 16)
  return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

function contrastRatio(foreground: string, background: string): number {
  const first = luminance(foreground)
  const second = luminance(background)
  const lighter = Math.max(first, second)
  const darker = Math.min(first, second)
  return (lighter + 0.05) / (darker + 0.05)
}

describe('contraste das paletas', () => {
  for (const pair of PAIRS) {
    it(`${pair.name} ≥ ${pair.min}:1`, () => {
      expect(contrastRatio(pair.foreground, pair.background)).toBeGreaterThanOrEqual(pair.min)
    })
  }
})
