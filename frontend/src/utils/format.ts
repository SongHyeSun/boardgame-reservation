// 표시용 포맷. 난이도·파티 상태 라벨과 날짜 표기는 화면에서 직접 만들지 말고 여기 것을 쓴다.

import type { Difficulty } from '../types/boardgame.ts'
import type { PartyStatus } from '../types/party.ts'

export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'NORMAL', 'HARD']

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
}

export function isDifficulty(value: string | null): value is Difficulty {
  return DIFFICULTIES.some((difficulty) => difficulty === value)
}

export const PARTY_STATUSES: readonly PartyStatus[] = ['RECRUITING', 'CLOSED', 'CANCELLED']

export const PARTY_STATUS_LABEL: Record<PartyStatus, string> = {
  RECRUITING: '모집 중',
  CLOSED: '마감',
  CANCELLED: '취소',
}

export function isPartyStatus(value: string | null): value is PartyStatus {
  return PARTY_STATUSES.some((status) => status === value)
}

/** 3~4명, 최소·최대가 같으면 4명 */
export function formatPlayers(minPlayers: number, maxPlayers: number): string {
  return minPlayers === maxPlayers ? `${minPlayers}명` : `${minPlayers}~${maxPlayers}명`
}

/** LocalDateTime 문자열(`2026-10-01T19:00:00`, 타임존 없음) → `2026. 10. 1. 오후 7:00`. 파싱이 안 되면 원문 */
export function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** 플레이 일시는 선택 입력이라 없으면 일정 미정 */
export function formatPlayAt(playAt: string | null): string {
  return playAt ? formatDateTime(playAt) : '일정 미정'
}

/**
 * datetime-local 값(`2026-10-01T19:00`) → 서버 LocalDateTime 형식(`2026-10-01T19:00:00`, http/party.http 예시와 동일).
 * 미입력은 null. 이미 초가 붙어 있으면 그대로 둔다.
 */
export function toPlayAtRequest(value: string): string | null {
  const trimmed = value.trim()
  if (trimmed === '') {
    return null
  }
  return /T\d{2}:\d{2}$/.test(trimmed) ? `${trimmed}:00` : trimmed
}
