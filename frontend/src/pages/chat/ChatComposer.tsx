import type { KeyboardEvent } from 'react'
import { MAX_MESSAGE_LENGTH } from '../../utils/chat.ts'

interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  onSubmit: (value: string) => void
  disabled: boolean
}

export default function ChatComposer({ value, onChange, onSubmit, disabled }: ChatComposerProps) {
  const canSubmit = !disabled && value.trim() !== ''

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      if (canSubmit) {
        onSubmit(value)
      }
    }
  }

  return (
    <div className="space-y-1">
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
  )
}
