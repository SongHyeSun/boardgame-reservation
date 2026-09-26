// 표시용 포맷. (F5에서 파티 상태 등 나머지 표시를 여기에 이어서 정리)

import type { Difficulty } from '../types/boardgame.ts'

export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'NORMAL', 'HARD']

export const DIFFICULTY_LABEL: Record<Difficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
}

export function isDifficulty(value: string | null): value is Difficulty {
  return DIFFICULTIES.some((difficulty) => difficulty === value)
}

/** 3~4명, 최소·최대가 같으면 4명 */
export function formatPlayers(minPlayers: number, maxPlayers: number): string {
  return minPlayers === maxPlayers ? `${minPlayers}명` : `${minPlayers}~${maxPlayers}명`
}

/** LocalDateTime 문자열(`2026-10-01T19:00:00`, 타임존 없음) → `2026. 10. 1. 오후 7:00`. 없으면 일정 미정 */
export function formatPlayAt(playAt: string | null): string {
  if (!playAt) {
    return '일정 미정'
  }
  const date = new Date(playAt)
  if (Number.isNaN(date.getTime())) {
    return playAt
  }
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}
