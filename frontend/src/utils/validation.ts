// 클라이언트 1차 검증. 규칙·메시지는 백엔드 member/dto (SignupRequest, UpdateProfileRequest, ChangePasswordRequest, LoginRequest), boardgame/dto (BoardGameRequest), party/dto (PartyCreateRequest) 그대로. 최종 판정은 서버.
// 값은 호출 전에 email·닉네임·이름·소속·직업·한줄소개를 trim 해서 넘긴다. (서버가 trim 하고 비었으면 null 로 저장. 비밀번호는 trim 안 함)
// 길이는 자바 String.length() 와 같은 UTF-16 단위인 .length 로 잰다.

import type {
  LoginRequest,
  PasswordFormValues,
  ProfileFormValues,
  SignupFormValues,
} from '../types/auth.ts'
import type { BoardGameFormValues, BoardGameResponse } from '../types/boardgame.ts'
import type { PartyFormValues } from '../types/party.ts'
import {
  AVATAR_IMAGE_INVALID_MESSAGE,
  AVATAR_IMAGE_MAX_BYTES,
  AVATAR_IMAGE_REQUIRED_MESSAGE,
  AVATAR_IMAGE_TOO_LARGE_MESSAGE,
  AVATAR_IMAGE_TYPES,
  type AvatarFormState,
} from './avatar.ts'

export type FieldErrors<T> = Partial<Record<keyof T, string>>

const EMAIL_MAX = 100
const PASSWORD_MIN = 8
const PASSWORD_MAX = 64
const NICKNAME_MIN = 2
const NICKNAME_MAX = 20
const NAME_MIN = 2
const NAME_MAX = 20
const AFFILIATION_MAX = 50
const JOB_MAX = 50
const BIO_MAX = 100

// 서버(Hibernate @Email)가 통과시키는 값을 클라이언트가 막지 않도록 일부러 느슨하게
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/

function isBlank(value: string): boolean {
  return value.trim() === ''
}

function validateEmail(email: string): string | undefined {
  if (isBlank(email)) {
    return '이메일은 필수입니다.'
  }
  if (!EMAIL_PATTERN.test(email)) {
    return '이메일 형식이 올바르지 않습니다.'
  }
  if (email.length > EMAIL_MAX) {
    return `이메일은 ${EMAIL_MAX}자 이하여야 합니다.`
  }
  return undefined
}

function validatePassword(password: string): string | undefined {
  if (isBlank(password)) {
    return '비밀번호는 필수입니다.'
  }
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `비밀번호는 ${PASSWORD_MIN}~${PASSWORD_MAX}자여야 합니다.`
  }
  return undefined
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/** 생년월일 입력의 max 값이자 검증 기준: 어제(`YYYY-MM-DD`, 브라우저 로컬 날짜). 서버는 @Past 라 오늘은 안 된다. */
export function birthDateMax(): string {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  return `${yesterday.getFullYear()}-${pad2(yesterday.getMonth() + 1)}-${pad2(yesterday.getDate())}`
}

function validateBirthDate(birthDate: string): string | undefined {
  if (birthDate === '') {
    return undefined
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) {
    return '생년월일 형식이 올바르지 않습니다.'
  }
  // 같은 형식의 ISO 날짜 문자열이라 사전순 비교가 날짜 비교와 같다
  return birthDate > birthDateMax() ? '생년월일은 과거 날짜여야 합니다.' : undefined
}

/** 가입·내 정보 수정이 공유하는 프로필 필드(닉네임·이름·생년월일·소속·직업·한줄소개) 검증 */
function validateProfileFields(values: ProfileFormValues): FieldErrors<ProfileFormValues> {
  const errors: FieldErrors<ProfileFormValues> = {}

  if (isBlank(values.nickname)) {
    errors.nickname = '닉네임은 필수입니다.'
  } else if (values.nickname.length < NICKNAME_MIN || values.nickname.length > NICKNAME_MAX) {
    errors.nickname = `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자여야 합니다.`
  }

  if (isBlank(values.name)) {
    errors.name = '이름은 필수입니다.'
  } else if (values.name.length < NAME_MIN || values.name.length > NAME_MAX) {
    errors.name = `이름은 ${NAME_MIN}~${NAME_MAX}자여야 합니다.`
  }

  const birthDateError = validateBirthDate(values.birthDate)
  if (birthDateError) {
    errors.birthDate = birthDateError
  }

  if (values.affiliation.length > AFFILIATION_MAX) {
    errors.affiliation = `소속은 ${AFFILIATION_MAX}자 이하여야 합니다.`
  }
  if (values.job.length > JOB_MAX) {
    errors.job = `직업은 ${JOB_MAX}자 이하여야 합니다.`
  }
  if (values.bio.length > BIO_MAX) {
    errors.bio = `한줄소개는 ${BIO_MAX}자 이하여야 합니다.`
  }

  return errors
}

export function validateSignup(values: SignupFormValues): FieldErrors<SignupFormValues> {
  const errors: FieldErrors<SignupFormValues> = validateProfileFields(values)

  const emailError = validateEmail(values.email)
  if (emailError) {
    errors.email = emailError
  }

  const passwordError = validatePassword(values.password)
  if (passwordError) {
    errors.password = passwordError
  }

  return errors
}

export function validateProfile(values: ProfileFormValues): FieldErrors<ProfileFormValues> {
  return validateProfileFields(values)
}

/** 새 비밀번호 규칙은 가입과 동일. "확인" 입력 일치는 클라이언트에서만 본다. */
export function validateChangePassword(values: PasswordFormValues): FieldErrors<PasswordFormValues> {
  const errors: FieldErrors<PasswordFormValues> = {}

  if (isBlank(values.currentPassword)) {
    errors.currentPassword = '현재 비밀번호는 필수입니다.'
  }

  if (isBlank(values.newPassword)) {
    errors.newPassword = '새 비밀번호는 필수입니다.'
  } else {
    const passwordError = validatePassword(values.newPassword)
    if (passwordError) {
      errors.newPassword = passwordError
    }
  }

  if (values.newPasswordConfirm !== values.newPassword) {
    errors.newPasswordConfirm = '새 비밀번호가 일치하지 않습니다.'
  }

  return errors
}

/** 형식(jpg/jpeg/png/webp)·크기(5MB) 1차 검증. 서버는 파일 시그니처까지 확인한다. */
export function validateAvatarImage(file: File): string | undefined {
  if (!AVATAR_IMAGE_TYPES.includes(file.type)) {
    return AVATAR_IMAGE_INVALID_MESSAGE
  }
  if (file.size > AVATAR_IMAGE_MAX_BYTES) {
    return AVATAR_IMAGE_TOO_LARGE_MESSAGE
  }
  return undefined
}

/**
 * 아바타 입력 검증. 이미지 모드인데 새 파일도 저장된 이미지도 없으면 서버가 400(INVALID_AVATAR)이므로 미리 막는다.
 * @param hasStoredImage 서버에 저장된 이미지가 있는지 (가입 화면은 항상 false)
 */
export function validateAvatar(state: AvatarFormState, hasStoredImage: boolean): string | undefined {
  if (state.mode !== 'IMAGE') {
    return undefined
  }
  if (state.file) {
    return validateAvatarImage(state.file)
  }
  return hasStoredImage ? undefined : AVATAR_IMAGE_REQUIRED_MESSAGE
}

const BOARDGAME_NAME_MAX = 100
const BOARDGAME_DESCRIPTION_MAX = 2000

/** 1 이상의 정수 문자열이면 숫자, 아니면 null (빈 값·음수·소수·숫자 아닌 문자 포함) */
export function parsePositiveInteger(value: string): number | null {
  const trimmed = value.trim()
  if (!/^\d+$/.test(trimmed)) {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : null
}

function validatePositiveInteger(value: string, requiredMessage: string, minMessage: string): string | undefined {
  if (isBlank(value)) {
    return requiredMessage
  }
  return parsePositiveInteger(value) === null ? minMessage : undefined
}

/**
 * 보드게임 등록/수정 폼 검증. 이름·설명은 trim 한 값을 전송하므로 trim 기준으로 잰다.
 * 난이도는 select 의 고정 옵션이라 검사하지 않는다. min > max 는 서버에선 서비스 검증(400)이지만 여기서 미리 막는다.
 */
export function validateBoardGame(values: BoardGameFormValues): FieldErrors<BoardGameFormValues> {
  const errors: FieldErrors<BoardGameFormValues> = {}

  const name = values.name.trim()
  if (name === '') {
    errors.name = '이름은 필수입니다.'
  } else if (name.length > BOARDGAME_NAME_MAX) {
    errors.name = `이름은 ${BOARDGAME_NAME_MAX}자 이하여야 합니다.`
  }

  const minError = validatePositiveInteger(values.minPlayers, '최소 인원은 필수입니다.', '최소 인원은 1명 이상이어야 합니다.')
  if (minError) {
    errors.minPlayers = minError
  }
  const maxError = validatePositiveInteger(values.maxPlayers, '최대 인원은 필수입니다.', '최대 인원은 1명 이상이어야 합니다.')
  if (maxError) {
    errors.maxPlayers = maxError
  }
  const minPlayers = parsePositiveInteger(values.minPlayers)
  const maxPlayers = parsePositiveInteger(values.maxPlayers)
  if (minPlayers !== null && maxPlayers !== null && minPlayers > maxPlayers) {
    errors.minPlayers = '최소 인원은 최대 인원보다 클 수 없습니다.'
  }

  const playTimeError = validatePositiveInteger(
    values.playTime,
    '플레이 시간은 필수입니다.',
    '플레이 시간은 1분 이상이어야 합니다.',
  )
  if (playTimeError) {
    errors.playTime = playTimeError
  }

  if (values.description.trim().length > BOARDGAME_DESCRIPTION_MAX) {
    errors.description = `설명은 ${BOARDGAME_DESCRIPTION_MAX}자 이하여야 합니다.`
  }

  return errors
}

const PARTY_TITLE_MAX = 100
const PARTY_DESCRIPTION_MAX = 2000

/**
 * 파티 개설 폼 검증 (party/dto PartyCreateRequest). 제목·설명은 trim 한 값을 전송하므로 trim 기준으로 잰다.
 * 정원은 호스트 포함 인원이며 선택한 게임의 minPlayers~maxPlayers 안이어야 한다. (서버는 서비스에서 400 INVALID_CAPACITY)
 * playAt 은 선택 값이라 검사하지 않는다.
 */
export function validateParty(
  values: PartyFormValues,
  boardGame: BoardGameResponse | undefined,
): FieldErrors<PartyFormValues> {
  const errors: FieldErrors<PartyFormValues> = {}

  if (boardGame === undefined) {
    errors.boardGameId = '보드게임을 선택해 주세요.'
  }

  const title = values.title.trim()
  if (title === '') {
    errors.title = '제목은 필수입니다.'
  } else if (title.length > PARTY_TITLE_MAX) {
    errors.title = `제목은 ${PARTY_TITLE_MAX}자 이하여야 합니다.`
  }

  if (values.description.trim().length > PARTY_DESCRIPTION_MAX) {
    errors.description = `설명은 ${PARTY_DESCRIPTION_MAX}자 이하여야 합니다.`
  }

  const capacityError = validatePositiveInteger(
    values.capacity,
    '모집 인원은 필수입니다.',
    '모집 인원은 1명 이상이어야 합니다.',
  )
  if (capacityError) {
    errors.capacity = capacityError
  } else if (boardGame !== undefined) {
    const capacity = parsePositiveInteger(values.capacity)
    if (capacity !== null && (capacity < boardGame.minPlayers || capacity > boardGame.maxPlayers)) {
      errors.capacity = `모집 인원은 ${boardGame.minPlayers}~${boardGame.maxPlayers}명이어야 합니다.`
    }
  }

  return errors
}

/** 로그인은 서버도 @NotBlank 만 검사한다. 형식·길이 규칙은 적용하지 않는다. */
export function validateLogin(values: LoginRequest): FieldErrors<LoginRequest> {
  const errors: FieldErrors<LoginRequest> = {}

  if (isBlank(values.email)) {
    errors.email = '이메일은 필수입니다.'
  }
  if (isBlank(values.password)) {
    errors.password = '비밀번호는 필수입니다.'
  }

  return errors
}
