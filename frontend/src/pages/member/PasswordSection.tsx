import { useState, type FormEvent } from 'react'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import { useChangePassword } from '../../hooks/useMember.ts'
import type { PasswordFormValues } from '../../types/auth.ts'
import { validateChangePassword, type FieldErrors } from '../../utils/validation.ts'

const EMPTY_VALUES: PasswordFormValues = { currentPassword: '', newPassword: '', newPasswordConfirm: '' }

export default function PasswordSection() {
  const change = useChangePassword()
  const [values, setValues] = useState<PasswordFormValues>(EMPTY_VALUES)
  const [errors, setErrors] = useState<FieldErrors<PasswordFormValues>>({})
  const [done, setDone] = useState(false)

  function handleChange(field: keyof PasswordFormValues, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
    setDone(false)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (change.isPending) {
      return
    }
    setDone(false)
    const fieldErrors = validateChangePassword(values)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    change.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: () => {
          setValues(EMPTY_VALUES)
          setDone(true)
        },
      },
    )
  }

  return (
    <section className="rounded border border-gray-200 bg-white p-6">
      <h2 className="text-lg font-semibold">비밀번호 변경</h2>

      <form onSubmit={handleSubmit} noValidate className="mt-4 space-y-4">
        {change.isError && <ErrorMessage message={change.error.message} />}
        {done && (
          <p role="status" className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
            비밀번호가 변경되었습니다.
          </p>
        )}

        <FormField
          id="currentPassword"
          label="현재 비밀번호"
          type="password"
          autoComplete="current-password"
          value={values.currentPassword}
          onChange={(event) => handleChange('currentPassword', event.target.value)}
          error={errors.currentPassword}
        />
        <FormField
          id="newPassword"
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          hint="8~64자"
          value={values.newPassword}
          onChange={(event) => handleChange('newPassword', event.target.value)}
          error={errors.newPassword}
        />
        <FormField
          id="newPasswordConfirm"
          label="새 비밀번호 확인"
          type="password"
          autoComplete="new-password"
          value={values.newPasswordConfirm}
          onChange={(event) => handleChange('newPasswordConfirm', event.target.value)}
          error={errors.newPasswordConfirm}
        />

        <button
          type="submit"
          disabled={change.isPending}
          className="rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {change.isPending ? '변경 중…' : '비밀번호 변경'}
        </button>
      </form>
    </section>
  )
}
