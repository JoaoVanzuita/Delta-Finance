import { forwardRef } from 'react'
import type { InputHTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

import { controlClass } from './control-classes'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, type = 'text', ...props }, ref) {
    return (
      <input ref={ref} type={type} className={cn(controlClass, 'min-h-11', className)} {...props} />
    )
  },
)
