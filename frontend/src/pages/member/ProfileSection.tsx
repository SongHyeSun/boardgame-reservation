import { useState, type FormEvent } from 'react'
import Avatar from '../../components/Avatar.tsx'
import AvatarField from '../../components/AvatarField.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import FormField from '../../components/FormField.tsx'
import TextAreaField from '../../components/TextAreaField.tsx'
import { useUpdateProfile } from '../../hooks/useMember.ts'
import type { MemberResponse, ProfileFormValues, UpdateProfileRequest } from '../../types/auth.ts'
import { avatarStateFrom, toAvatarRequest, type AvatarFormState } from '../../utils/avatar.ts'
import { blankToNull, formatDateTime } from '../../utils/format.ts'
import { birthDateMax, validateAvatar, validateProfile, type FieldErrors } from '../../utils/validation.ts'

const CARD = 'rounded border border-gray-200 bg-white p-6'
const PRIMARY_BUTTON = 'rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50'
const SECONDARY_BUTTON =
  'rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50 disabled:opacity-50'

interface MeProps {
  me: MemberResponse
}

function InfoRow({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="grid grid-cols-3 gap-2 text-sm">
      <dt className="text-gray-500">{label}</dt>
      <dd className="col-span-2 whitespace-pre-wrap break-words font-medium">{value ?? '-'}</dd>
    </div>
  )
}

function ProfileView({ me, onEdit }: MeProps & { onEdit: () => void }) {
  return (
    <div className={CARD}>
      <div className="flex items-center gap-4">
        <Avatar avatar={me.avatar} size="lg" nickname={me.nickname} />
        <div className="min-w-0">
          <p className="truncate text-xl font-bold">{me.nickname}</p>
          <p className="truncate text-sm text-gray-500">{me.email}</p>
        </div>
      </div>

      <dl className="mt-6 space-y-3">
        <InfoRow label="이름" value={me.name ?? '미등록 (수정에서 입력해 주세요)'} />
        <InfoRow label="생년월일" value={me.birthDate} />
        <InfoRow label="소속" value={me.affiliation} />
        <InfoRow label="직업" value={me.job} />
        <InfoRow label="한줄소개" value={me.bio} />
        <InfoRow label="가입일" value={formatDateTime(me.createdAt)} />
      </dl>

      <button type="button" onClick={onEdit} className={`mt-6 ${SECONDARY_BUTTON}`}>
        정보 수정
      </button>
    </div>
  )
}

function toFormValues(me: MemberResponse): ProfileFormValues {
  return {
    nickname: me.nickname,
    name: me.name ?? '',
    birthDate: me.birthDate ?? '',
    affiliation: me.affiliation ?? '',
    job: me.job ?? '',
    bio: me.bio ?? '',
  }
}

/** 수정 모드에 들어올 때마다 새로 마운트되므로 초기 상태는 현재 me 그대로다. */
function ProfileForm({ me, onDone }: MeProps & { onDone: () => void }) {
  const update = useUpdateProfile()
  const [values, setValues] = useState<ProfileFormValues>(() => toFormValues(me))
  const [avatar, setAvatar] = useState<AvatarFormState>(() => avatarStateFrom(me.avatar))
  const [errors, setErrors] = useState<FieldErrors<ProfileFormValues>>({})
  const [avatarError, setAvatarError] = useState<string | undefined>()

  const storedImageUrl = me.avatar.imageUrl

  function handleChange(field: keyof ProfileFormValues, value: string) {
    setValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  function handleAvatarChange(next: AvatarFormState) {
    setAvatar(next)
    setAvatarError(undefined)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (update.isPending) {
      return
    }
    const trimmed: ProfileFormValues = {
      nickname: values.nickname.trim(),
      name: values.name.trim(),
      birthDate: values.birthDate,
      affiliation: values.affiliation.trim(),
      job: values.job.trim(),
      bio: values.bio.trim(),
    }
    const fieldErrors = validateProfile(trimmed)
    const avatarMessage = validateAvatar(avatar, storedImageUrl !== null)
    setErrors(fieldErrors)
    setAvatarError(avatarMessage)
    if (Object.keys(fieldErrors).length > 0 || avatarMessage !== undefined) {
      return
    }

    // 선택 필드는 보내지 않으면 서버에서 지워진다(전체 교체). 그래서 항상 전체 값을 보낸다.
    const { avatarType, avatarEmoji, removeImage, image } = toAvatarRequest(avatar)
    const data: UpdateProfileRequest = {
      nickname: trimmed.nickname,
      name: trimmed.name,
      birthDate: blankToNull(trimmed.birthDate),
      affiliation: blankToNull(trimmed.affiliation),
      job: blankToNull(trimmed.job),
      bio: blankToNull(trimmed.bio),
      avatarType,
      avatarEmoji,
      removeImage,
    }
    update.mutate({ data, image }, { onSuccess: onDone })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className={`${CARD} space-y-4`}>
      {update.isError && <ErrorMessage message={update.error.message} />}

      <FormField id="profile-email" label="이메일" type="email" value={me.email} readOnly disabled hint="이메일은 변경할 수 없습니다." />
      <FormField
        id="profile-nickname"
        label="닉네임"
        type="text"
        autoComplete="nickname"
        hint="2~20자"
        value={values.nickname}
        onChange={(event) => handleChange('nickname', event.target.value)}
        error={errors.nickname}
      />
      <FormField
        id="profile-name"
        label="이름"
        type="text"
        autoComplete="name"
        hint="2~20자"
        value={values.name}
        onChange={(event) => handleChange('name', event.target.value)}
        error={errors.name}
      />
      <FormField
        id="profile-birthDate"
        label="생년월일 (선택)"
        type="date"
        autoComplete="bday"
        max={birthDateMax()}
        value={values.birthDate}
        onChange={(event) => handleChange('birthDate', event.target.value)}
        error={errors.birthDate}
      />
      <FormField
        id="profile-affiliation"
        label="소속 (선택)"
        type="text"
        autoComplete="organization"
        hint="50자 이하"
        value={values.affiliation}
        onChange={(event) => handleChange('affiliation', event.target.value)}
        error={errors.affiliation}
      />
      <FormField
        id="profile-job"
        label="직업 (선택)"
        type="text"
        autoComplete="organization-title"
        hint="50자 이하"
        value={values.job}
        onChange={(event) => handleChange('job', event.target.value)}
        error={errors.job}
      />
      <TextAreaField
        id="profile-bio"
        label="한줄소개 (선택)"
        rows={2}
        hint="100자 이하"
        value={values.bio}
        onChange={(event) => handleChange('bio', event.target.value)}
        error={errors.bio}
      />

      <AvatarField
        id="profile-avatar"
        value={avatar}
        onChange={handleAvatarChange}
        storedImageUrl={storedImageUrl}
        error={avatarError}
      />

      <div className="flex gap-2">
        <button type="submit" disabled={update.isPending} className={PRIMARY_BUTTON}>
          {update.isPending ? '저장 중…' : '저장'}
        </button>
        <button type="button" onClick={onDone} disabled={update.isPending} className={SECONDARY_BUTTON}>
          취소
        </button>
      </div>
    </form>
  )
}

/** 조회 ↔ 수정 전환. 수정 성공 시 useUpdateProfile 이 me 캐시를 갱신하므로 조회 화면은 자동으로 새 값을 보여 준다. */
export default function ProfileSection({ me }: MeProps) {
  const [editing, setEditing] = useState(false)

  if (editing) {
    return <ProfileForm me={me} onDone={() => setEditing(false)} />
  }
  return <ProfileView me={me} onEdit={() => setEditing(true)} />
}
