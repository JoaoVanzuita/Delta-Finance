import axe from 'axe-core'
import { expect } from 'vitest'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

/**
 * Executa axe-core no contêiner e falha se houver violações WCAG 2.1 A/AA.
 *
 * A mensagem de falha lista `id`, impacto e quantidade de nós para facilitar a
 * correção; o teste nunca ignora violações críticas ou sérias.
 */
export async function expectNoA11yViolations(container: Element = document.body): Promise<void> {
  const results = await axe.run(container, {
    runOnly: { type: 'tag', values: WCAG_TAGS },
  })

  const summary = results.violations
    .map(
      (violation) =>
        `${violation.id} (${violation.impact ?? 'unknown'}): ${violation.help} — ${violation.nodes.length} nó(s)`,
    )
    .join('\n')

  expect(results.violations, summary).toEqual([])
}
