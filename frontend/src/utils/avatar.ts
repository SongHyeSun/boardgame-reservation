// 아바타 공통 규칙. 백엔드 member/domain/AvatarEmojis.java, global/file(5MB, jpg/jpeg/png/webp) 와 같은 값이어야 한다.

import type { Avatar, AvatarType } from '../types/auth.ts'

/** AvatarEmojis.ALLOWED 와 같은 목록·같은 문자열 (♟️ 은 U+265F + U+FE0F 두 코드포인트) */
export const AVATAR_EMOJIS: readonly string[] = [
  '🎲', '🃏', '♟️', '🧩', '🎯', '🏆', '🐉', '🦊',
  '🐱', '🐻', '🐧', '🦉', '🌟', '🔥', '🍀', '🎩',
]

/** AvatarEmojis.DEFAULT */
export const DEFAULT_AVATAR_EMOJI = '🎲'

export const AVATAR_IMAGE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp']
export const AVATAR_IMAGE_ACCEPT = '.jpg,.jpeg,.png,.webp'
export const AVATAR_IMAGE_MAX_BYTES = 5 * 1024 * 1024

export const AVATAR_IMAGE_INVALID_MESSAGE = '지원하지 않는 이미지 파일입니다.'
export const AVATAR_IMAGE_TOO_LARGE_MESSAGE = '이미지는 5MB 이하만 업로드할 수 있습니다.'
export const AVATAR_IMAGE_REQUIRED_MESSAGE = '이미지를 선택해 주세요.'

/**
 * 아바타 입력 상태 (가입·내 정보 수정 공용). 두 가지 불변식을 항상 지킨다.
 * - mode 가 EMOJI 면 file 은 null  (서버는 image 파트가 있으면 avatarType 과 무관하게 IMAGE 로 처리하므로)
 * - mode 가 IMAGE 면 removeImage 는 false  (image·IMAGE 와 removeImage 를 같이 보내면 서버가 400)
 */
export interface AvatarFormState {
  mode: AvatarType
  emoji: string
  /** 새로 고른 파일. 이미지 모드에서만 유지된다 */
  file: File | null
  /** 서버에 저장된 이미지 삭제 (수정 화면 전용) */
  removeImage: boolean
}

export const INITIAL_AVATAR_STATE: AvatarFormState = {
  mode: 'EMOJI',
  emoji: DEFAULT_AVATAR_EMOJI,
  file: null,
  removeImage: false,
}

/** 내 정보 수정 화면의 시작 상태: 현재 아바타 그대로 */
export function avatarStateFrom(avatar: Avatar): AvatarFormState {
  return { mode: avatar.type, emoji: avatar.emoji, file: null, removeImage: false }
}

/**
 * 모드 전환. 이모지 모드로 바꾸면 선택해 둔 새 파일을 버리고(다시 이미지 모드로 와도 복원하지 않음),
 * 이미지 모드로 바꾸면 삭제 표시를 푼다.
 */
export function changeAvatarMode(state: AvatarFormState, mode: AvatarType): AvatarFormState {
  if (state.mode === mode) {
    return state
  }
  return mode === 'EMOJI' ? { ...state, mode, file: null } : { ...state, mode, removeImage: false }
}

export interface AvatarRequestParts {
  avatarType: AvatarType
  avatarEmoji: string
  removeImage: boolean
  /** multipart 의 image 파트. null 이면 파트 자체를 만들지 않는다 */
  image: File | null
}

/**
 * 폼 상태 → 요청 조각. 가입·내 정보 수정이 이 함수만으로 아바타 payload 를 만든다.
 * changeAvatarMode 를 거치지 않았더라도 불변식이 깨지지 않도록 여기서 한 번 더 막는다:
 * image 는 "이미지 모드 + 새 파일 있음" 일 때만, removeImage 는 이모지 모드일 때만 나간다.
 */
export function toAvatarRequest(state: AvatarFormState): AvatarRequestParts {
  const imageMode = state.mode === 'IMAGE'
  return {
    avatarType: state.mode,
    avatarEmoji: state.emoji,
    removeImage: !imageMode && state.removeImage,
    image: imageMode ? state.file : null,
  }
}
