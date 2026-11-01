/**
 * Roteiro de screenshots do Delta Finance.
 *
 * Percorre a matriz de rotas/estados definida em `captures.ts`, servindo um
 * banco de dados de teste em memória (`mockApi.ts`).
 *
 * Execução (a partir de `client/`):
 *   npm run screenshots
 *   npm run screenshots -- --grep "10-dashboard"
 *
 * As imagens são gravadas em `client/screenshots-output/`, acompanhadas de um
 * `manifest.json` e de uma galeria `index.html`.
 *
 * Manifesto:
 * - execução completa substitui o manifesto pelo conjunto gerado;
 * - execução filtrada faz merge com o manifesto anterior e registra
 *   `run.filtered=true`, sem apagar entradas de capturas não executadas;
 * - a galeria sempre reflete as entradas do manifesto atual.
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, test, type Page } from '@playwright/test'

import { CAPTURES, expandCaptures, type CaptureVariant, type ViewportId } from './captures'
import { emptyDb, seedDb } from './db'
import { installMockApi } from './mockApi'

const OUTPUT_DIR = fileURLToPath(new URL('../screenshots-output', import.meta.url))
const MANIFEST_PATH = path.join(OUTPUT_DIR, 'manifest.json')

interface ManifestEntry {
  slug: string
  name: string
  route: string
  file: string
  viewport: ViewportId
  width: number
  height: number
  theme: 'light' | 'dark'
  mode: 'route' | 'state'
}

interface Manifest {
  generatedAt: string
  dataSource: string
  run: {
    planned: number
    generated: number
    filtered: boolean
    viewports: string[]
    themes: string[]
  }
  entries: ManifestEntry[]
}

const PLANNED = expandCaptures(CAPTURES)
const captured = new Map<string, ManifestEntry>()

function readPreviousManifest(): ManifestEntry[] {
  try {
    const parsed = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8')) as ManifestEntry[] | Manifest
    if (Array.isArray(parsed)) return parsed
    if (Array.isArray(parsed.entries)) return parsed.entries
  } catch {
    // Primeira execução ou manifesto inválido: começa vazio.
  }
  return []
}

async function settle(page: Page, variant: CaptureVariant): Promise<void> {
  if (variant.settle === 'immediate') {
    await page.waitForLoadState('domcontentloaded')
    await page.getByRole('heading', { level: 1 }).first().waitFor({ timeout: 5_000 })
    await page.waitForTimeout(350)
  } else {
    await page.waitForLoadState('networkidle')
    await page.getByRole('heading', { level: 1 }).first().waitFor({ timeout: 5_000 })
  }

  await page.waitForFunction(
    (expected) => document.documentElement.getAttribute('data-theme') === expected,
    variant.theme,
    { timeout: 5_000 },
  )
}

test.describe('Screenshots do Delta Finance', () => {
  test.beforeAll(() => {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true })
  })

  for (const variant of PLANNED) {
    test(`${variant.key} — ${variant.renderName}`, async ({ page }) => {
      const db = variant.fixture === 'empty' ? emptyDb() : seedDb()
      await installMockApi(page, db, {
        delayPaths: variant.delayPaths,
        delayMs: 5_000,
        failPaths: variant.failPaths,
        authenticated: variant.authenticated,
      })

      await page.setViewportSize({ width: variant.viewport.width, height: variant.viewport.height })
      await page.emulateMedia({ colorScheme: variant.theme })
      await page.goto(variant.route)
      await settle(page, variant)

      if (variant.setup) {
        await variant.setup(page)
        if (variant.settle !== 'immediate') {
          await page.waitForLoadState('networkidle')
        }
        // Setup pode rolar até um drawer/aba; volta ao topo para evitar que
        // elementos sticky apareçam deslocados na captura full-page.
        await page.evaluate(() => window.scrollTo(0, 0))
      }

      const externalPath = path.join(OUTPUT_DIR, variant.file)
      await page.screenshot({
        path: externalPath,
        fullPage: variant.fullPage !== false,
        animations: 'disabled',
        caret: 'hide',
      })

      expect(fs.existsSync(externalPath), `captura ${variant.file} não foi gerada`).toBe(true)
      expect(fs.statSync(externalPath).size, `captura ${variant.file} está vazia`).toBeGreaterThan(
        0,
      )

      captured.set(variant.file, {
        slug: variant.slug,
        name: variant.renderName,
        route: variant.route,
        file: variant.file,
        viewport: variant.viewport.id,
        width: variant.viewport.width,
        height: variant.viewport.height,
        theme: variant.theme,
        mode: variant.variantMode ?? 'route',
      })
    })
  }

  test.afterEach(() => {
    // Manifests incrementais evitam que uma execução filtrada/parcial deixe o
    // arquivo defasado mesmo se o processo for interrompido.
    writeManifest()
    writeGallery()
  })

  test.afterAll(() => {
    writeManifest()
    writeGallery()
  })
})

function writeManifest(): void {
  const generated = [...captured.values()]
  const filtered = generated.length < PLANNED.length
  const previous = filtered ? readPreviousManifest() : []
  const merged = new Map<string, ManifestEntry>()
  for (const entry of previous) merged.set(entry.file, entry)
  for (const entry of generated) merged.set(entry.file, entry)

  const manifest: Manifest = {
    generatedAt: new Date().toISOString(),
    dataSource: 'fixtures:screenshots/db.ts',
    run: {
      planned: PLANNED.length,
      generated: generated.length,
      filtered,
      viewports: [...new Set(PLANNED.map((variant) => variant.viewport.id))],
      themes: ['light', 'dark'],
    },
    entries: [...merged.values()].sort((left, right) => left.file.localeCompare(right.file)),
  }

  fs.writeFileSync(MANIFEST_PATH, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
}

function writeGallery(): void {
  const manifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8')) as Manifest
  const items = manifest.entries
    .map(
      (entry) => `
        <figure>
          <a href="${entry.file}"><img loading="lazy" src="${entry.file}" alt="${entry.name}" /></a>
          <figcaption>
            <strong>${entry.name}</strong>
            <code>${entry.route}</code>
            <span class="meta">${entry.width}×${entry.height} · ${entry.theme} · ${entry.mode}</span>
          </figcaption>
        </figure>`,
    )
    .join('\n')

  const filteredNotice = manifest.run.filtered
    ? `<p class="notice">Execução filtrada: ${manifest.run.generated} de ${manifest.run.planned} capturas geradas. O manifesto preserva as demais entradas.</p>`
    : `<p class="lead">${manifest.entries.length} capturas. Matriz completa: desktop 1280/1440, tablet 768, mobile 360/390, temas claro e escuro.</p>`
  const fixtureNotice =
    '<p class="fixture">Dados fictícios de teste (<code>screenshots/db.ts</code>). Estas imagens não refletem o banco local nem a sessão aberta em <code>npm run dev</code>.</p>'

  const html = `<!doctype html>
<html lang="pt-BR">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Screenshots — Delta Finance</title>
    <style>
      :root { color-scheme: light dark; }
      body { font-family: system-ui, sans-serif; margin: 0; padding: 2rem; }
      h1 { font-size: 1.5rem; }
      p.lead { color: #666; }
      p.notice { color: #8a5a00; background: #fff4d6; border: 1px solid #e9c46a; padding: .75rem 1rem; border-radius: 10px; }
      p.fixture { color: #5b3b00; background: #fff8e6; border: 1px solid #f0d48a; padding: .75rem 1rem; border-radius: 10px; }
      main { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1.5rem; }
      figure { margin: 0; border: 1px solid #ddd; border-radius: 12px; overflow: hidden; }
      img { display: block; width: 100%; height: 240px; object-fit: cover; object-position: top; }
      figcaption { padding: 0.75rem; font-size: 0.85rem; }
      code { display: block; margin-top: 0.25rem; color: #888; }
      .meta { display: block; margin-top: 0.35rem; color: #666; font-size: 0.75rem; }
    </style>
  </head>
  <body>
    <h1>Delta Finance — Screenshots</h1>
    ${filteredNotice}
    ${fixtureNotice}
    <main>${items}</main>
  </body>
</html>
`
  fs.writeFileSync(path.join(OUTPUT_DIR, 'index.html'), html, 'utf8')
}
