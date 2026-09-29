import type { SelectHTMLAttributes } from 'react'

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'className'> {
  id: string
  label: string
  error?: string
}

export default function SelectField({ id, label, error, children, ...selectProps }: SelectFieldProps) {
  const errorId = `${id}-error`

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-small font-semibold text-ink">
        {label}
      </label>
      <select
        {...selectProps}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`h-11 w-full rounded-md border bg-surface px-3 text-body text-ink placeholder:text-ink-muted focus:border-felt focus:outline-2 focus:outline-offset-1 focus:outline-felt disabled:bg-sunken disabled:text-ink-muted ${
          error ? 'border-danger' : 'border-line-strong'
        }`}
      >
        {children}
      </select>
      {error && (
        <p id={errorId} className="mt-1.5 text-small font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
