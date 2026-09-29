import { useRef, type KeyboardEvent } from 'react'
import { usePageActionBarHeight } from '../../hooks/usePageActionBar.ts'
import { MAX_MESSAGE_LENGTH } from '../../utils/chat.ts'

interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  onSubmit: (value: string) => void
  disabled: boolean
}

export default function ChatComposer({ value, onChange, onSubmit, disabled }: ChatComposerProps) {
  const canSubmit = !disabled && value.trim() !== ''
  const barRef = useRef<HTMLDivElement>(null)
  // 입력창이 화면 아래에 고정되므로 그 실제 높이만큼 토스트와 본문 여백을 비켜 준다
  usePageActionBarHeight(barRef)

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      if (canSubmit) {
        onSubmit(value)
      }
    }
  }

  // 큰 화면에서도 하단 고정(z-40). 안쪽은 760px 가운데 정렬
  return (
    <div
      ref={barRef}
      className="fixed inset-x-0 bottom-0 z-(--z-action-bar) border-t border-line bg-surface px-4 pt-3 pb-[calc(12px+env(safe-area-inset-bottom))] shadow-lifted"
    >
      <div className="mx-auto max-w-[760px] space-y-1">
        <div className="flex items-end gap-2">
          <textarea
            aria-label="AI에게 질문 입력"
            value={value}
            maxLength={MAX_MESSAGE_LENGTH}
            disabled={disabled}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={handleKeyDown}
            rows={2}
            className="w-full rounded border border-gray-300 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-gray-100"
          />
          <button
            type="button"
            disabled={!canSubmit}
            onClick={() => onSubmit(value)}
            className="shrink-0 rounded bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {disabled ? '추천을 찾는 중…' : '전송'}
          </button>
        </div>
        <p className="text-right text-xs text-gray-400">
          {value.length}/{MAX_MESSAGE_LENGTH}
        </p>
      </div>
    </div>
  )
}
