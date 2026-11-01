import { useState } from 'react'

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/ui/empty-state'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { Tab, TabList, TabPanel, Tabs } from '@/components/ui/tabs'

function TabsHarness() {
  const [value, setValue] = useState('resumo')
  return (
    <Tabs value={value} onValueChange={setValue}>
      <TabList label="Seções do cartão">
        <Tab value="resumo">Resumo</Tab>
        <Tab value="faturas">Faturas</Tab>
      </TabList>
      <TabPanel value="resumo">Conteúdo do resumo</TabPanel>
      <TabPanel value="faturas">Conteúdo das faturas</TabPanel>
    </Tabs>
  )
}

describe('estados compartilhados', () => {
  it('alerta usa role adequado e mantém título e conteúdo', () => {
    render(
      <Alert variant="danger" title="Falha ao salvar">
        Tente novamente em instantes.
      </Alert>,
    )
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Falha ao salvar')
    expect(alert).toHaveTextContent('Tente novamente em instantes.')
  })

  it('badge, skeleton, empty state e progresso expõem estado semântico', () => {
    render(
      <div>
        <Badge variant="expense">Vencida</Badge>
        <Skeleton lines={2} data-testid="skeleton" />
        <EmptyState
          title="Nenhuma conta"
          description="Crie uma conta manual para começar."
          action={<button type="button">Criar conta</button>}
        />
        <Progress value={45} label="Limite usado" />
      </div>,
    )

    expect(screen.getByText('Vencida')).toBeInTheDocument()
    expect(screen.getByTestId('skeleton')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByRole('heading', { name: 'Nenhuma conta' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Criar conta' })).toBeInTheDocument()
    const progress = screen.getByRole('progressbar', { name: 'Limite usado' })
    expect(progress).toHaveAttribute('aria-valuenow', '45')
    expect(progress).toHaveAttribute('aria-valuetext', '45%')
  })

  it('tabs seguem o padrão ARIA e respondem ao teclado', async () => {
    const user = userEvent.setup()
    render(<TabsHarness />)

    const resumo = screen.getByRole('tab', { name: 'Resumo' })
    expect(resumo).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Conteúdo do resumo')

    resumo.focus()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Faturas' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Conteúdo das faturas')
  })
})
