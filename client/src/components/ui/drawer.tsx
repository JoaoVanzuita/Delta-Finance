import type { ReactNode, RefObject } from 'react'

import { OverlayPanel } from './overlay-panel'

export interface DrawerProps {
  readonly open: boolean
  readonly onClose: () => void
  readonly title: string
  readonly description?: ReactNode
  readonly children: ReactNode
  readonly footer?: ReactNode
  readonly side?: 'left' | 'right'
  readonly width?: 'sm' | 'md' | 'lg'
  readonly dismissible?: boolean
  readonly busy?: boolean
  readonly initialFocusRef?: RefObject<HTMLElement | null>
  readonly className?: string
  readonly bodyClassName?: string
}

const widths = {
  sm: 'sm:max-w-sm',
  md: 'sm:max-w-md',
  lg: 'sm:max-w-lg',
} as const

/**
 * Drawer contextual com a mesma base acessível do Dialog.
 * Ocupa a largura disponível no mobile e mantém header/footer visíveis.
 */
export function Drawer({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  side = 'right',
  width = 'md',
  dismissible = true,
  busy = false,
  initialFocusRef,
  className,
  bodyClassName,
}: DrawerProps) {
  return (
    <OverlayPanel
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      dismissible={dismissible}
      closeOnEscape
      busy={busy}
      initialFocusRef={initialFocusRef}
      portalClassName={side === 'right' ? 'flex justify-end' : 'flex justify-start'}
      panelClassName={[
        'h-full w-full border-line',
        side === 'right' ? 'border-l' : 'border-r',
        widths[width],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      bodyClassName={bodyClassName}
      footerClassName="border-t border-line bg-surface px-5 py-4"
      footer={footer}
    >
      {children}
    </OverlayPanel>
  )
}
