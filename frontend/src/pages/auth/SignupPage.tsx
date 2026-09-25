import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import { useSignup } from '../../hooks/useAuth.ts'
import type { SignupRequest } from '../../types/auth.ts'
import { pathWithRedirect } from '../../utils/navigation.ts'
import { validateSignup, type FieldErrors } from '../../utils/validation.ts'

const SIGNUP_DONE_NOTICE = '회원가입이 완료되었습니다. 로그인해 주세요.'

export default function SignupPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const signup = useSignup()
  const [values, setValues] = useState<SignupRequest>({ email: '', password: '', nickname: '' })
  const [errors, setErrors] = useState<FieldErrors<SignupRequest>>({})

  const rawRedirect = searchParams.get('redirect')

  function handleChange(field: keyof SignupRequest, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (signup.isPending) {
      return
    }
    // 서버가 이메일·닉네임을 trim 하므로 같은 값으로 검증·전송한다. 비밀번호는 그대로.
    const request: SignupRequest = {
      email: values.email.trim(),
      password: values.password,
      nickname: values.nickname.trim(),
    }
    const fieldErrors = validateSignup(request)
    setErrors(fieldErrors)
    if (Object.keys(fieldErrors).length > 0) {
      return
    }
    signup.mutate(request, {
      onSuccess: () => {
        navigate(pathWithRedirect('/login', rawRedirect), { replace: true, state: { notice: SIGNUP_DONE_NOTICE } })
      },
    })
  }

  return (
    <section className="mx-auto max-w-sm rounded border border-gray-200 bg-white p-6">
      <h1 className="text-2xl font-bold">회원가입</h1>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-4">
        {signup.isError && <ErrorMessage message={signup.error.message} />}

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
          autoComplete="new-password"
          hint="8~64자"
          value={values.password}
          onChange={(event) => handleChange('password', event.target.value)}
          error={errors.password}
        />
        <FormField
          id="nickname"
          label="닉네임"
          type="text"
          autoComplete="nickname"
          hint="2~20자"
          value={values.nickname}
          onChange={(event) => handleChange('nickname', event.target.value)}
          error={errors.nickname}
        />

        <button
          type="submit"
          disabled={signup.isPending}
          className="w-full rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {signup.isPending ? '가입 중…' : '회원가입'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-gray-600">
        이미 계정이 있으신가요?{' '}
        <Link to={pathWithRedirect('/login', rawRedirect)} className="font-medium text-indigo-600 hover:underline">
          로그인
        </Link>
      </p>
    </section>
  )
}
