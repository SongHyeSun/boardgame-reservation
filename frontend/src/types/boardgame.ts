// boardgame/dto 기준

export type Difficulty = 'EASY' | 'NORMAL' | 'HARD'

export interface BoardGameResponse {
  id: number
  name: string
  minPlayers: number
  maxPlayers: number
  playTime: number
  difficulty: Difficulty
  description: string | null
  createdAt: string
}

/** 등록(POST) / 수정(PUT) 공용. minPlayers <= maxPlayers 는 서버가 최종 검증 */
export interface BoardGameRequest {
  name: string // 필수, 최대 100자
  minPlayers: number // 필수, 1 이상
  maxPlayers: number // 필수, 1 이상
  playTime: number // 필수, 1 이상 (분)
  difficulty: Difficulty
  description?: string | null // 최대 2000자
}

/** GET /api/boardgames 쿼리 파라미터 (전부 선택) */
export interface BoardGameFilter {
  players?: number
  difficulty?: Difficulty
  keyword?: string
}
