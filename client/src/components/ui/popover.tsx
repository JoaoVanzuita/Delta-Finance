import { useEffect, useId, useRef, useState } from 'react'
import type { ReactNode, RefObject } from 'react'

import { cn } from '@/lib/cn'

export interface PopoverTriggerState {
  readonly open: boolean
  readonly id: string
  readonly toggle: () => void
  readonly triggerRef: RefObject<HTMLButtonElement | null>
}

export interface PopoverCloseState {
  readonly close: () => void
}

export interface PopoverProps {
  readonly label: string
  readonly trigger: (state: PopoverTriggerState) => ReactNode
  readonly children: ReactNode | ((state: PopoverCloseState) => ReactNode)
  readonly align?: 'start' | 'end'
  readonly placement?: 'top' | 'bottom'
  readonly className?: string
}

/**
 * Popover para filtros/menus compactos. Fecha ao clicar fora e com Escape,
 * devolvendo o foco ao gatilho. Não é um dialog modal.
 */
export function Popover({
  label,
  trigger,
  children,
  align = 'end',
  placement = 'bottom',
  className,
}: PopoverProps) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) {
        setOpen(false)
      }
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    const first = panelRef.current?.querySelector<HTMLElement>(
      'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    )
    first?.focus()
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      {trigger({
        open,
        id,
        toggle: () => setOpen((current) => !current),
        triggerRef,
      })}
      {open ? (
        <div
          ref={panelRef}
          id={id}
          role="dialog"
          aria-label={label}
          className={cn(
            'absolute z-40 min-w-56 rounded-card border border-line',
            'bg-surface-raised p-3 shadow-[var(--shadow-overlay)]',
            align === 'end' ? 'right-0' : 'left-0',
            placement === 'top' ? 'bottom-full mb-2' : 'top-full mt-2',
          )}
        >
          {typeof children === 'function' ? children({ close: () => setOpen(false) }) : children}
        </div>
      ) : null}
    </div>
  )
}
