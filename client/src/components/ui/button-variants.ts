import { cva } from 'class-variance-authority'

export const buttonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2 rounded-control font-semibold',
    'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas',
    'disabled:pointer-events-none disabled:bg-disabled disabled:text-on-disabled disabled:shadow-none',
  ],
  {
    variants: {
      variant: {
        primary: 'bg-primary text-on-primary hover:bg-primary-hover',
        secondary:
          'border border-line-strong bg-surface text-ink hover:bg-surface-muted active:bg-surface-muted',
        ghost: 'text-ink hover:bg-surface-muted',
        danger: 'bg-expense text-on-expense hover:opacity-90',
        link: 'text-action underline underline-offset-4 hover:text-action-hover',
      },
      size: {
        sm: 'min-h-11 px-3 text-body-sm sm:min-h-9',
        md: 'min-h-11 px-4 text-body-sm',
        lg: 'min-h-12 px-5 text-body',
      },
      fullWidth: {
        true: 'w-full',
        false: '',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
      fullWidth: false,
    },
  },
)
