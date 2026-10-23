import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Configuração compartilhada dos testes de componente.
// Helpers de RTL/axe adicionais ficam em src/test/ (T036).
afterEach(() => {
  cleanup()
})
