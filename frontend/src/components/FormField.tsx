import type { InputHTMLAttributes } from 'react'

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> {
  id: string
  label: string
  error?: string
  hint?: string
}

export default function FormField({ id, label, error, hint, ...inputProps }: FormFieldProps) {
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ')

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-small font-semibold text-ink">
        {label}
      </label>
      <input
        {...inputProps}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`h-11 w-full rounded-md border bg-surface px-3 text-body text-ink placeholder:text-ink-muted focus:border-felt focus:outline-2 focus:outline-offset-1 focus:outline-felt disabled:bg-sunken disabled:text-ink-muted ${
          error ? 'border-danger' : 'border-line-strong'
        }`}
      />
      {hint && (
        <p id={hintId} className="mt-1.5 text-small text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1.5 text-small font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
