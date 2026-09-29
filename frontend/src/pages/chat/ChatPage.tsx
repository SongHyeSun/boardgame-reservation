import { useEffect, useRef, useState } from 'react'
import { useChatRecommend, useChatUsage } from '../../hooks/useChat.ts'
import { ApiError } from '../../types/api.ts'
import type { ChatErrorTurn, ChatTurn } from '../../types/chat.ts'
import { buildHistory, isRetryable } from '../../utils/chat.ts'
import ChatComposer from './ChatComposer.tsx'
import ChatExampleChips from './ChatExampleChips.tsx'
import ChatMessageBubble from './ChatMessageBubble.tsx'

function randomId(): string {
  return crypto.randomUUID()
}

function toErrorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다'
}

export default function ChatPage() {
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  const usage = useChatUsage()
  const recommend = useChatRecommend()
  const quotaExhausted = usage.data?.remaining === 0

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [turns, recommend.isPending])

  function handleSend(rawMessage: string) {
    const message = rawMessage.trim()
    if (message === '' || recommend.isPending) {
      return
    }
    const history = buildHistory(turns)
    setTurns((prev) => [...prev, { role: 'USER', id: randomId(), content: message }])
    setDraft('')

    recommend.mutate(
      { message, history },
      {
        onSuccess: (data) => {
          setTurns((prev) => [
            ...prev,
            { role: 'ASSISTANT', id: randomId(), content: data.answer, recommendations: data.recommendations },
          ])
        },
        onError: (error) => {
          setTurns((prev) => [
            ...prev,
            {
              role: 'ERROR',
              id: randomId(),
              message: toErrorMessage(error),
              retryable: isRetryable(error),
              retryMessage: message,
            },
          ])
        },
      },
    )
  }

  /**
   * handleSend 를 재사용하지 않는다 — 실패한 USER 턴을 다시 추가하면 사용자 말풍선이 중복되므로,
   * 해당 ERROR 턴만 제거하고 기존 USER 턴은 그대로 둔 채 같은 메시지로 재요청한다.
   */
  function handleRetry(errorTurn: ChatErrorTurn) {
    if (recommend.isPending) {
      return
    }
    const history = buildHistory(turns)
    setTurns((prev) => prev.filter((turn) => turn.id !== errorTurn.id))

    recommend.mutate(
      { message: errorTurn.retryMessage, history },
      {
        onSuccess: (data) => {
          setTurns((prev) => [
            ...prev,
            { role: 'ASSISTANT', id: randomId(), content: data.answer, recommendations: data.recommendations },
          ])
        },
        onError: (error) => {
          setTurns((prev) => [
            ...prev,
            {
              role: 'ERROR',
              id: randomId(),
              message: toErrorMessage(error),
              retryable: isRetryable(error),
              retryMessage: errorTurn.retryMessage,
            },
          ])
        },
      },
    )
  }

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-bold">AI 게임 추천</h1>
        {usage.data && <p className="text-sm text-gray-500">오늘 남은 추천 {usage.data.remaining}회</p>}
      </div>

      <div className="max-h-[60vh] space-y-3 overflow-y-auto rounded border border-gray-200 bg-white p-4">
        {turns.length === 0 && <ChatExampleChips onSelect={setDraft} disabled={quotaExhausted} />}
        {turns.map((turn) => (
          <ChatMessageBubble key={turn.id} turn={turn} disabled={recommend.isPending} onRetry={handleRetry} />
        ))}
        {recommend.isPending && <p className="text-sm text-gray-500">추천을 찾는 중…</p>}
        <div ref={bottomRef} />
      </div>

      <p className="text-xs text-gray-400">
        무료 AI 서비스를 사용하므로 입력 내용이 서비스 개선에 활용될 수 있어요. 개인정보는 입력하지 마세요.
      </p>

      {quotaExhausted && (
        <p role="alert" className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">
          오늘 추천 횟수를 모두 사용했어요. 내일 다시 이용해주세요
        </p>
      )}

      <ChatComposer value={draft} onChange={setDraft} onSubmit={handleSend} disabled={recommend.isPending || quotaExhausted} />
    </section>
  )
}
