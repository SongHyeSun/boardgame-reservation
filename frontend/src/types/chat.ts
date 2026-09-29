// chat/dto 기준 (ChatRequest, ChatResponse, ChatRecommendationResponse, ChatHistoryItem, ChatUsageResponse)
// 웹 검색은 범위 밖이라 sources 없음 (백엔드 ChatResponse 와 동일)

import type { Difficulty } from './boardgame.ts'

export type ChatRole = 'USER' | 'ASSISTANT'

/** 요청에 함께 보내는 과거 대화 한 턴. content 최대 1000자(서버 제한) */
export interface ChatHistoryItem {
  role: ChatRole
  content: string
}

/** POST /api/chat/recommend 요청 바디 */
export interface ChatRequest {
  message: string // 1~500자
  history?: ChatHistoryItem[] // 최대 10개
}

/** 추천 카드 한 건. 게임 상세 필드는 DB 기준, reason 만 LLM 이 준 값 */
export interface ChatRecommendation {
  gameId: number
  name: string
  imageUrl: string | null
  minPlayers: number
  maxPlayers: number
  playTime: number
  difficulty: Difficulty
  offlineAvailable: boolean
  onlineAvailable: boolean
  reason: string
}

/** POST /api/chat/recommend 응답 */
export interface ChatResponse {
  answer: string
  recommendations: ChatRecommendation[]
  remainingToday: number
}

/** GET /api/chat/usage 응답 */
export interface ChatUsageResponse {
  limit: number
  used: number
  remaining: number
}

// ---- 프론트 전용 대화 state (서버 저장 없음, 새로고침 시 초기화) ----

export interface ChatUserTurn {
  role: 'USER'
  id: string
  content: string
}

export interface ChatAssistantTurn {
  role: 'ASSISTANT'
  id: string
  content: string
  recommendations: ChatRecommendation[]
}

/** 실패한 턴. AI 말풍선 자리에 에러 스타일로 표시한다 */
export interface ChatErrorTurn {
  role: 'ERROR'
  id: string
  message: string
  retryable: boolean // false = 오늘 추천 횟수 소진 — 재시도해도 같은 에러라 버튼을 숨긴다
  retryMessage: string // 재시도 시 다시 보낼 원본 사용자 메시지
}

export type ChatTurn = ChatUserTurn | ChatAssistantTurn | ChatErrorTurn
