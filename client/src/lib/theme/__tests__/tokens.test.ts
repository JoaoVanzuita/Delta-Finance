import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

const root = process.cwd()
const tokens = readFileSync(join(root, 'src/styles/tokens.css'), 'utf8')
const indexHtml = readFileSync(join(root, 'index.html'), 'utf8')
const themeBootstrap = readFileSync(join(root, 'public/theme-bootstrap.js'), 'utf8')

describe('design tokens', () => {
  it('publica a escala semântica para o Tailwind 4', () => {
    for (const token of [
      '--color-canvas',
      '--color-surface',
      '--color-surface-muted',
      '--color-surface-raised',
      '--color-ink',
      '--color-ink-muted',
      '--color-line',
      '--color-line-strong',
      '--color-primary',
      '--color-primary-hover',
      '--color-on-primary',
      '--color-primary-soft',
      '--color-action',
      '--color-income',
      '--color-expense',
      '--color-warning',
      '--color-info',
      '--color-focus',
      '--color-scrim',
      '--color-on-income',
      '--color-on-expense',
      '--color-on-warning',
      '--color-on-info',
    ]) {
      expect(tokens).toContain(token)
    }
  })

  it('define pares claro/escuro e as quatro paletas curadas', () => {
    expect(tokens).toContain(":root,\n[data-theme='light']")
    expect(tokens).toContain("[data-theme='dark']")
    for (const accent of ['blue', 'green', 'purple', 'amber']) {
      expect(tokens).toContain(`[data-accent='${accent}']`)
    }
    expect(tokens).toContain("[data-theme='light'][data-accent='amber']")
    expect(tokens).toContain("[data-theme='dark'][data-accent='amber']")
  })

  it('define tipografia, espaçamento, raios, elevação, foco, motion e densidade', () => {
    for (const token of [
      '--text-display',
      '--text-page-title',
      '--text-section-title',
      '--text-body',
      '--text-body-sm',
      '--text-caption',
      '--text-overline',
      '--text-metric-xl',
      '--space-1',
      '--space-9',
      '--radius-control',
      '--radius-card',
      '--radius-panel',
      '--radius-pill',
      '--shadow-card',
      '--shadow-overlay',
      '--focus-ring-width',
      '--motion-fast',
      '--motion-base',
      '--ease-standard',
      '--density-pad-comfortable',
      '--density-pad-compact',
      '--density-row-comfortable',
      '--density-row-compact',
    ]) {
      expect(tokens).toContain(token)
    }
  })

  it('atualiza color-scheme e evita flash de tema na primeira pintura', () => {
    expect(tokens).toContain('color-scheme: light')
    expect(tokens).toContain('color-scheme: dark')
    expect(indexHtml).toContain('name="color-scheme" content="light dark"')
    expect(indexHtml).toContain('src="/theme-bootstrap.js"')
    expect(indexHtml).toContain('data-theme')
    expect(indexHtml).toContain('data-accent')
    expect(themeBootstrap).toContain('localStorage')
    expect(themeBootstrap).toContain("root.setAttribute('data-theme'")
  })

  it('não usa hexadecimais soltos em componentes compartilhados', () => {
    const files = ['button-variants.ts', 'alert.tsx', 'badge.tsx', 'card.tsx', 'field.tsx']
    for (const file of files) {
      const source = readFileSync(join(root, 'src/components/ui', file), 'utf8')
      expect(source).not.toMatch(/#[0-9a-fA-F]{3,8}/)
    }
  })
})
