// party/dto 기준

import type { Avatar } from './auth.ts'
import type { BoardGameResponse, PlayMode } from './boardgame.ts'

export type PartyStatus = 'RECRUITING' | 'CLOSED' | 'CANCELLED'

/**
 * 목록/개설 응답 항목.
 * 기타 게임(직접 입력) 파티는 boardGameId 가 null, customGame 이 true 다. 표시할 게임 이름은 항상 gameName.
 */
export interface PartyResponse {
  id: number
  title: string
  boardGameId: number | null
  gameName: string
  customGame: boolean
  boardGameVisible: boolean // false = 게임 운영 중지 (기타 게임 파티는 항상 true)
  playMode: PlayMode
  hostNickname: string
  hostAvatar: Avatar
  capacity: number
  currentCount: number
  status: PartyStatus
  playAt: string | null
}

export interface PartyMemberInfo {
  memberId: number
  nickname: string
  avatar: Avatar
  joinedAt: string
}

/**
 * remaining = capacity - 참여자(JOINED) 수 (호스트 포함 기준).
 * onlineLink 는 호스트·참여자에게만 값이 있고 그 외(비로그인·미참여·내보내진 회원)는 null 이다.
 * 링크를 등록하지 않은 파티도 null 이라 "비공개"와 "없음"은 응답만으로 구분되지 않는다.
 */
export interface PartyDetailResponse {
  id: number
  title: string
  description: string | null
  boardGameId: number | null
  gameName: string
  customGame: boolean
  boardGameVisible: boolean // false = 게임 운영 중지
  hostId: number
  hostNickname: string
  hostAvatar: Avatar
  capacity: number
  remaining: number
  status: PartyStatus
  playAt: string | null
  playMode: PlayMode
  onlinePlatform: string | null // ONLINE 일 때만 값이 있을 수 있음
  onlineLink: string | null
  location: string | null // OFFLINE 일 때만 값이 있을 수 있음
  members: PartyMemberInfo[]
}

interface PartyCreateBase {
  title: string // 필수, 최대 100자
  description?: string | null // 최대 2000자
  capacity: number // 필수, 1 이상, 호스트 포함 인원. 보드게임은 게임의 min~max, 기타 게임은 2~20 (서버 검증)
  /**
   * LocalDateTime. http/party.http 예시는 `2026-10-01T19:00:00` (초 포함).
   * `<input type="datetime-local">` 값은 초가 없으므로(`…T19:00`) 전송 전에 `:00`을 붙일 것.
   */
  playAt?: string | null
  playMode: PlayMode // 필수. 보드게임은 게임이 지원하는 방식만
  /** 아래 세 값은 방식과 맞는 것만 보낸다. (서버도 안 맞는 값은 버림) */
  onlinePlatform?: string | null // ONLINE, 최대 30자
  onlineLink?: string | null // ONLINE, 최대 300자, http:// / https:// 만
  location?: string | null // OFFLINE, 최대 100자
}

/** 게임은 등록된 보드게임(boardGameId) 또는 기타 게임 이름(customGameName, 최대 50자) 중 정확히 하나 */
export type PartyCreateRequest = PartyCreateBase &
  ({ boardGameId: number; customGameName?: never } | { customGameName: string; boardGameId?: never })

/** 개설 폼에서 고른 게임. NONE = 아직 안 고름, CUSTOM = 기타 게임 이름 직접 입력 */
export type PartyGameChoice =
  | { kind: 'NONE' }
  | { kind: 'BOARDGAME'; boardGame: BoardGameResponse }
  | { kind: 'CUSTOM' }

/** 개설 폼의 입력 상태 (프론트 전용). 숫자·선택 값은 문자열로 들고 있다가 제출 시 변환한다. */
export interface PartyFormValues {
  game: PartyGameChoice
  customGameName: string // game 이 CUSTOM 일 때만 쓰인다 (다른 게임으로 바꿔도 입력값은 남겨 둔다)
  title: string
  description: string
  capacity: string
  playAt: string // datetime-local 값 (`YYYY-MM-DDTHH:mm`), 미입력은 ''
  playMode: PlayMode | '' // 미선택은 ''
  onlinePlatform: string
  onlineLink: string
  location: string
}

export interface JoinResponse {
  remaining: number
}

/** GET /api/parties 쿼리 파라미터 (전부 선택) */
export interface PartyFilter {
  status?: PartyStatus
  boardGameId?: number // 기타 게임 파티는 자연히 제외된다
  playMode?: PlayMode
}
