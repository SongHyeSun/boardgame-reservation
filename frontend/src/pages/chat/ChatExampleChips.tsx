import { EXAMPLE_QUESTIONS } from '../../utils/chat.ts'

interface ChatExampleChipsProps {
  onSelect: (text: string) => void
  disabled: boolean
}

/** 클릭 시 입력창에 채우기만 하고 자동 전송하지 않는다(하루 추천 횟수가 제한적이라 오클릭 낭비를 막기 위함) */
export default function ChatExampleChips({ onSelect, disabled }: ChatExampleChipsProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {EXAMPLE_QUESTIONS.map((question) => (
        <button
          key={question}
          type="button"
          disabled={disabled}
          onClick={() => onSelect(question)}
          className="rounded-full border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:border-indigo-400 hover:text-indigo-600 disabled:opacity-50"
        >
          {question}
        </button>
      ))}
    </div>
  )
}
