import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import AvatarField from '../../components/AvatarField.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { useSignup } from '../../hooks/useAuth.ts'
import type { SignupFormValues, SignupRequest } from '../../types/auth.ts'
import { INITIAL_AVATAR_STATE, toAvatarRequest, type AvatarFormState } from '../../utils/avatar.ts'
import { blankToNull } from '../../utils/format.ts'
import { pathWithRedirect } from '../../utils/navigation.ts'
import { birthDateMax, validateAvatar, validateSignup, type FieldErrors } from '../../utils/validation.ts'

const SIGNUP_DONE_NOTICE = '회원가입이 완료되었습니다. 로그인해 주세요.'
const ADMIN_REQUEST_NOTICE = '관리자 신청이 접수되었습니다. 최고 관리자 승인 후 관리자 권한이 부여됩니다.'

const INITIAL_VALUES: SignupFormValues = {
  email: '',
  password: '',
  nickname: '',
  name: '',
  birthDate: '',
  affiliation: '',
  job: '',
  bio: '',
  requestAdmin: false,
}

type TextField = Exclude<keyof SignupFormValues, 'requestAdmin'>

export default function SignupPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const signup = useSignup()
  const [values, setValues] = useState<SignupFormValues>(INITIAL_VALUES)
  const [avatar, setAvatar] = useState<AvatarFormState>(INITIAL_AVATAR_STATE)
  const [errors, setErrors] = useState<FieldErrors<SignupFormValues>>({})
  const [avatarError, setAvatarError] = useState<string | undefined>()

  const rawRedirect = searchParams.get('redirect')

  function handleChange(field: TextField, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  function handleAvatarChange(next: AvatarFormState) {
    setAvatar(next)
    setAvatarError(undefined)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (signup.isPending) {
      return
    }
    // 서버가 텍스트 값을 trim 하고 비면 null 로 저장하므로 같은 값으로 검증·전송한다. 비밀번호는 그대로.
    const trimmed: SignupFormValues = {
      email: values.email.trim(),
      password: values.password,
      nickname: values.nickname.trim(),
      name: values.name.trim(),
      birthDate: values.birthDate,
      affiliation: values.affiliation.trim(),
      job: values.job.trim(),
      bio: values.bio.trim(),
      requestAdmin: values.requestAdmin,
    }
    const fieldErrors = validateSignup(trimmed)
    const avatarMessage = validateAvatar(avatar, false)
    setErrors(fieldErrors)
    setAvatarError(avatarMessage)
    if (Object.keys(fieldErrors).length > 0 || avatarMessage !== undefined) {
      return
    }

    const { avatarEmoji, image } = toAvatarRequest(avatar)
    const data: SignupRequest = {
      email: trimmed.email,
      password: trimmed.password,
      nickname: trimmed.nickname,
      name: trimmed.name,
      birthDate: blankToNull(trimmed.birthDate),
      affiliation: blankToNull(trimmed.affiliation),
      job: blankToNull(trimmed.job),
      bio: blankToNull(trimmed.bio),
      avatarEmoji,
      requestAdmin: trimmed.requestAdmin,
    }
    signup.mutate(
      { data, image },
      {
        onSuccess: () => {
          const notice = trimmed.requestAdmin ? `${SIGNUP_DONE_NOTICE} ${ADMIN_REQUEST_NOTICE}` : SIGNUP_DONE_NOTICE
          navigate(pathWithRedirect('/login', rawRedirect), { replace: true, state: { notice } })
        },
      },
    )
  }

  return (
    <section className="mx-auto max-w-md rounded border border-gray-200 bg-white p-6">
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
        <FormField
          id="name"
          label="이름"
          type="text"
          autoComplete="name"
          hint="2~20자"
          value={values.name}
          onChange={(event) => handleChange('name', event.target.value)}
          error={errors.name}
        />
        <FormField
          id="birthDate"
          label="생년월일 (선택)"
          type="date"
          autoComplete="bday"
          max={birthDateMax()}
          value={values.birthDate}
          onChange={(event) => handleChange('birthDate', event.target.value)}
          error={errors.birthDate}
        />
        <FormField
          id="affiliation"
          label="소속 (선택)"
          type="text"
          autoComplete="organization"
          hint="50자 이하"
          value={values.affiliation}
          onChange={(event) => handleChange('affiliation', event.target.value)}
          error={errors.affiliation}
        />
        <FormField
          id="job"
          label="직업 (선택)"
          type="text"
          autoComplete="organization-title"
          hint="50자 이하"
          value={values.job}
          onChange={(event) => handleChange('job', event.target.value)}
          error={errors.job}
        />
        <TextAreaField
          id="bio"
          label="한줄소개 (선택)"
          rows={2}
          hint="100자 이하"
          value={values.bio}
          onChange={(event) => handleChange('bio', event.target.value)}
          error={errors.bio}
        />

        <AvatarField id="avatar" value={avatar} onChange={handleAvatarChange} error={avatarError} />

        <div>
          <label className="flex items-center gap-2 text-sm font-medium text-gray-700">
            <input
              type="checkbox"
              checked={values.requestAdmin}
              onChange={(event) => setValues((prev) => ({ ...prev, requestAdmin: event.target.checked }))}
            />
            관리자로 가입 신청
          </label>
          <p className="mt-1 text-xs text-gray-500">최고 관리자 승인 후 관리자 권한이 부여됩니다</p>
        </div>

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
