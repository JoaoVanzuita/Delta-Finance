import { createContext, useContext, useId, useRef } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'

import { cn } from '@/lib/cn'

interface TabsContextValue {
  readonly value: string
  readonly baseId: string
  readonly onChange: (value: string) => void
  readonly registerTab: (value: string, element: HTMLButtonElement | null) => void
}

const TabsContext = createContext<TabsContextValue | null>(null)

function useTabsContext(): TabsContextValue {
  const context = useContext(TabsContext)
  if (!context) {
    throw new Error('Componentes de Tabs devem estar dentro de <Tabs>')
  }
  return context
}

export interface TabsProps {
  readonly value: string
  readonly onValueChange: (value: string) => void
  readonly children: ReactNode
  readonly className?: string
}

export function Tabs({ value, onValueChange, children, className }: TabsProps) {
  const baseId = useId()
  const elements = useRef(new Map<string, HTMLButtonElement>())
  return (
    <TabsContext.Provider
      value={{
        value,
        baseId,
        onChange: onValueChange,
        registerTab: (tabValue, element) => {
          if (element) {
            elements.current.set(tabValue, element)
          } else {
            elements.current.delete(tabValue)
          }
        },
      }}
    >
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

export interface TabListProps {
  readonly label: string
  readonly children: ReactNode
  readonly className?: string
}

export function TabList({ label, children, className }: TabListProps) {
  return (
    <div
      role="tablist"
      aria-label={label}
      className={cn('flex gap-1 overflow-x-auto border-b border-line', className)}
    >
      {children}
    </div>
  )
}

export interface TabProps {
  readonly value: string
  readonly children: ReactNode
  readonly disabled?: boolean
}

export function Tab({ value, children, disabled = false }: TabProps) {
  const { value: active, baseId, onChange, registerTab } = useTabsContext()
  const selected = active === value

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) {
      return
    }
    const buttons = Array.from(
      event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>(
        '[role="tab"]:not([disabled])',
      ) ?? [],
    )
    if (buttons.length === 0) {
      return
    }
    event.preventDefault()
    const currentIndex = buttons.indexOf(event.currentTarget)
    let nextIndex = currentIndex
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % buttons.length
    if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + buttons.length) % buttons.length
    if (event.key === 'Home') nextIndex = 0
    if (event.key === 'End') nextIndex = buttons.length - 1
    const next = buttons[nextIndex]
    next?.focus()
    const nextValue = next?.dataset.tabValue
    if (nextValue) onChange(nextValue)
  }

  return (
    <button
      ref={(element) => registerTab(value, element)}
      type="button"
      role="tab"
      id={`${baseId}-tab-${value}`}
      aria-selected={selected}
      aria-controls={`${baseId}-panel-${value}`}
      tabIndex={selected ? 0 : -1}
      disabled={disabled}
      data-tab-value={value}
      onClick={() => onChange(value)}
      onKeyDown={onKeyDown}
      className={cn(
        'inline-flex min-h-11 items-center border-b-2 px-3 text-body-sm font-semibold transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas',
        selected
          ? 'border-primary text-ink'
          : 'border-transparent text-ink-muted hover:border-line-strong hover:text-ink',
        disabled && 'cursor-not-allowed opacity-60',
      )}
    >
      {children}
    </button>
  )
}

export interface TabPanelProps {
  readonly value: string
  readonly children: ReactNode
  readonly className?: string
}

export function TabPanel({ value, children, className }: TabPanelProps) {
  const { value: active, baseId } = useTabsContext()
  const selected = active === value
  return (
    <div
      role="tabpanel"
      id={`${baseId}-panel-${value}`}
      aria-labelledby={`${baseId}-tab-${value}`}
      tabIndex={selected ? 0 : -1}
      hidden={!selected}
      className={cn('pt-4 focus-visible:outline-none', className)}
    >
      {children}
    </div>
  )
}
