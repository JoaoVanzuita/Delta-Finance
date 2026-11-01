import { forwardRef } from 'react'
import type { SelectHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

import { controlClass } from './control-classes'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, children, ...props }, ref) {
    return (
      <select ref={ref} className={cn(controlClass, 'min-h-11 pr-8', className)} {...props}>
        {children}
      </select>
    )
  },
)
