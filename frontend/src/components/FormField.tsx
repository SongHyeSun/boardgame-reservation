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
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-gray-700">
        {label}
      </label>
      <input
        {...inputProps}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`w-full rounded border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
          error ? 'border-red-400' : 'border-gray-300'
        }`}
      />
      {hint && (
        <p id={hintId} className="mt-1 text-xs text-gray-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
