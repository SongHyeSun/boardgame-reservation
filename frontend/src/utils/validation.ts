// 클라이언트 1차 검증. 규칙·메시지는 백엔드 member/dto (SignupRequest, LoginRequest), boardgame/dto (BoardGameRequest) 그대로. 최종 판정은 서버.
// 값은 호출 전에 email·nickname 을 trim 해서 넘긴다. (서버가 이메일을 trim, 닉네임은 검증 후 trim 함. 비밀번호는 trim 안 함)
// 길이는 자바 String.length() 와 같은 UTF-16 단위인 .length 로 잰다.

import type { LoginRequest, SignupRequest } from '../types/auth.ts'
import type { BoardGameFormValues } from '../types/boardgame.ts'

export type FieldErrors<T> = Partial<Record<keyof T, string>>

const EMAIL_MAX = 100
const PASSWORD_MIN = 8
const PASSWORD_MAX = 64
const NICKNAME_MIN = 2
const NICKNAME_MAX = 20

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

export function validateSignup(values: SignupRequest): FieldErrors<SignupRequest> {
  const errors: FieldErrors<SignupRequest> = {}

  const emailError = validateEmail(values.email)
  if (emailError) {
    errors.email = emailError
  }

  if (isBlank(values.password)) {
    errors.password = '비밀번호는 필수입니다.'
  } else if (values.password.length < PASSWORD_MIN || values.password.length > PASSWORD_MAX) {
    errors.password = `비밀번호는 ${PASSWORD_MIN}~${PASSWORD_MAX}자여야 합니다.`
  }

  if (isBlank(values.nickname)) {
    errors.nickname = '닉네임은 필수입니다.'
  } else if (values.nickname.length < NICKNAME_MIN || values.nickname.length > NICKNAME_MAX) {
    errors.nickname = `닉네임은 ${NICKNAME_MIN}~${NICKNAME_MAX}자여야 합니다.`
  }

  return errors
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
