import { useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import { useLogin } from '../../hooks/useAuth.ts'
import type { LoginRequest } from '../../types/auth.ts'
import { pathWithRedirect, readNotice } from '../../utils/navigation.ts'
import { validateLogin, type FieldErrors } from '../../utils/validation.ts'

export default function LoginPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const login = useLogin()
  const [values, setValues] = useState<LoginRequest>({ email: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors<LoginRequest>>({})

  const notice = readNotice(location.state)

  function handleChange(field: keyof LoginRequest, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  // 성공 후 이동은 GuestRoute 가 처리한다 (useLogin 이 me 캐시를 채우면 redirect 대상으로 이동)
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (login.isPending) {
      return
    }
    const request: LoginRequest = { email: values.email.trim(), password: values.password }
    const fieldErrors = validateLogin(request)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    login.mutate(request)
  }

  return (
    <section className="mx-auto max-w-sm rounded border border-gray-200 bg-white p-6">
      <h1 className="text-2xl font-bold">로그인</h1>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {notice && (
          <p role="status" className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
            {notice}
          </p>
        )}
        {login.isError && <ErrorMessage message={login.error.message} />}

        <FormField
          id="email"
          label="이메일"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={(event) => handleChange('email', event.target.value)}
          error={errors.email}
        />
        <FormField
          id="password"
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={values.password}
          onChange={(event) => handleChange('password', event.target.value)}
          error={errors.password}
        />

        <button
          type="submit"
          disabled={login.isPending}
          className="w-full rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {login.isPending ? '로그인 중…' : '로그인'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-gray-600">
        계정이 없으신가요?{' '}
        <Link
          to={pathWithRedirect('/signup', searchParams.get('redirect'))}
          className="font-medium text-indigo-600 hover:underline"
        >
          회원가입
        </Link>
      </p>
    </section>
  )
}
