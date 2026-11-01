import type { HTMLAttributes } from 'react'

import { cn } from '@/lib/cn'

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  readonly lines?: number
}

/** Placeholder que preserva o layout; nunca substitui feedback textual de erro. */
export function Skeleton({ className, lines = 1, ...props }: SkeletonProps) {
  if (lines <= 1) {
    return (
      <div
        aria-hidden="true"
        className={cn('animate-pulse rounded-control bg-surface-muted', 'h-4 w-full', className)}
        {...props}
      />
    )
  }

  return (
    <div aria-hidden="true" className={cn('space-y-2', className)} {...props}>
      {Array.from({ length: lines }, (_, index) => (
        <div
          key={index}
          className={cn(
            'h-4 animate-pulse rounded-control bg-surface-muted',
            index === lines - 1 ? 'w-2/3' : 'w-full',
          )}
        />
      ))}
    </div>
  )
}
