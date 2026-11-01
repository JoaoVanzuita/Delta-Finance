/** Classes compartilhadas dos controles de formulário. */
export const controlClass = [
  'block w-full rounded-control border border-line-strong bg-surface px-3 py-2 text-body-sm text-ink',
  'placeholder:text-ink-muted/70 shadow-[0_1px_0_rgb(15_23_42_/_0.02)]',
  'transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]',
  'focus:border-focus focus:outline-none focus:ring-2 focus:ring-focus/30',
  'disabled:bg-disabled disabled:text-on-disabled',
  'aria-invalid:border-expense aria-invalid:ring-2 aria-invalid:ring-expense/25',
].join(' ')
