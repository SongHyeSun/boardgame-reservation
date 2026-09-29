import { ApiError } from '../types/api.ts'
import type { ChatHistoryItem, ChatTurn } from '../types/chat.ts'

export const MAX_MESSAGE_LENGTH = 500

export const EXAMPLE_QUESTIONS: readonly string[] = [
  '3인용 1시간짜리 추천해줘',
  '처음 하는 사람도 쉬운 게임',
  '온라인으로 할 수 있는 게임',
]

/** ErrorCode.CHAT_LIMIT_EXCEEDED 문구와 동일해야 한다(백엔드 ErrorCode.java 기준) */
const LIMIT_EXCEEDED_MESSAGE = '오늘 사용할 수 있는 AI 추천 횟수를 모두 사용했어요'

/** 오늘 한도 소진이면 재시도해도 같은 에러라 재시도 버튼을 숨긴다. CHAT_BUSY·CHAT_UNAVAILABLE·네트워크 오류는 재시도 가능 */
export function isRetryable(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return true
  }
  return !(error.status === 429 && error.message === LIMIT_EXCEEDED_MESSAGE)
}

const MAX_HISTORY = 10
const MAX_HISTORY_CONTENT_LENGTH = 1000

/**
 * USER 다음이 ASSISTANT 인 완결된 교환 쌍만 포함한다(에러로 끝났거나 아직 응답이 없는 마지막 턴은 제외).
 * ASSISTANT 답변은 1000자로 잘라 서버 ChatHistoryItem 의 @Size(max=1000) 제약을 방어한다.
 */
export function buildHistory(turns: ChatTurn[]): ChatHistoryItem[] {
  const items: ChatHistoryItem[] = []
  for (let i = 0; i < turns.length; i++) {
    const turn = turns[i]
    if (turn.role !== 'USER') {
      continue
    }
    const next = turns[i + 1]
    if (next === undefined || next.role !== 'ASSISTANT') {
      continue
    }
    items.push({ role: 'USER', content: turn.content })
    items.push({ role: 'ASSISTANT', content: next.content.slice(0, MAX_HISTORY_CONTENT_LENGTH) })
  }
  return items.slice(-MAX_HISTORY)
}
