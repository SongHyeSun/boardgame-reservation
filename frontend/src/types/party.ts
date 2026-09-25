// party/dto 기준

export type PartyStatus = 'RECRUITING' | 'CLOSED' | 'CANCELLED'

/** 목록/개설 응답 항목 */
export interface PartyResponse {
  id: number
  title: string
  boardGameId: number
  boardGameName: string
  hostNickname: string
  capacity: number
  currentCount: number
  status: PartyStatus
  playAt: string | null
}

export interface PartyMemberInfo {
  memberId: number
  nickname: string
  joinedAt: string
}

/** remaining = capacity - 참여자 수 (호스트 포함 기준) */
export interface PartyDetailResponse {
  id: number
  title: string
  description: string | null
  boardGameId: number
  boardGameName: string
  hostId: number
  hostNickname: string
  capacity: number
  remaining: number
  status: PartyStatus
  playAt: string | null
  members: PartyMemberInfo[]
}

export interface PartyCreateRequest {
  boardGameId: number
  title: string // 필수, 최대 100자
  description?: string | null // 최대 2000자
  capacity: number // 필수, 1 이상, 호스트 포함 인원. 게임의 min~max 범위는 서버가 검증
  /**
   * LocalDateTime. http/party.http 예시는 `2026-10-01T19:00:00` (초 포함).
   * `<input type="datetime-local">` 값은 초가 없으므로(`…T19:00`) 전송 전에 `:00`을 붙일 것.
   */
  playAt?: string | null
}

export interface JoinResponse {
  remaining: number
}

/** GET /api/parties 쿼리 파라미터 (전부 선택) */
export interface PartyFilter {
  status?: PartyStatus
  boardGameId?: number
}
