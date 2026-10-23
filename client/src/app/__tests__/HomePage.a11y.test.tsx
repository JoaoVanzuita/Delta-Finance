import { describe, it } from 'vitest'

import { HomePage } from '@/app/HomePage'
import { expectNoA11yViolations } from '@/test/axe'
import { renderWithProviders } from '@/test/render'

describe('HomePage', () => {
  it('não tem violações WCAG 2.1 A/AA', async () => {
    renderWithProviders(<HomePage />)

    await expectNoA11yViolations()
  })
})
