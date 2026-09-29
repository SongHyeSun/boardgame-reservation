import type { ChatErrorTurn, ChatTurn } from '../../types/chat.ts'
import ChatRecommendationCard from './ChatRecommendationCard.tsx'

interface ChatMessageBubbleProps {
  turn: ChatTurn
  disabled: boolean
  onRetry: (turn: ChatErrorTurn) => void
}

export default function ChatMessageBubble({ turn, disabled, onRetry }: ChatMessageBubbleProps) {
  if (turn.role === 'USER') {
    return (
      <div className="flex justify-end">
        <p className="max-w-[80%] whitespace-pre-wrap rounded bg-indigo-600 px-3 py-2 text-sm text-white">
          {turn.content}
        </p>
      </div>
    )
  }

  if (turn.role === 'ASSISTANT') {
    return (
      <div className="flex justify-start">
        <div className="max-w-[80%] space-y-2">
          <p className="whitespace-pre-wrap rounded border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900">
            {turn.content}
          </p>
          {turn.recommendations.length > 0 && (
            <div className="space-y-2">
              {turn.recommendations.map((recommendation) => (
                <ChatRecommendationCard key={recommendation.gameId} recommendation={recommendation} />
              ))}
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="flex justify-start">
      <div
        role="alert"
        className="max-w-[80%] space-y-2 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
      >
        <p>{turn.message}</p>
        {turn.retryable && (
          <button
            type="button"
            disabled={disabled}
            onClick={() => onRetry(turn)}
            className="rounded border border-red-300 bg-white px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
          >
            다시 시도
          </button>
        )}
      </div>
    </div>
  )
}
