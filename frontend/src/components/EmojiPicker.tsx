import { AVATAR_EMOJIS } from '../utils/avatar.ts'

interface EmojiPickerProps {
  value: string
  onChange: (emoji: string) => void
}

/** 고정 목록(utils/avatar.ts, 백엔드 AvatarEmojis 와 동일)에서 아바타 이모지를 고른다. */
export default function EmojiPicker({ value, onChange }: EmojiPickerProps) {
  return (
    <div role="group" aria-label="아바타 이모지 선택" className="flex flex-wrap gap-2">
      {AVATAR_EMOJIS.map((emoji) => {
        const selected = emoji === value
        return (
          <button
            key={emoji}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(emoji)}
            className={`flex h-10 w-10 items-center justify-center rounded-full border text-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
              selected ? 'border-indigo-600 bg-indigo-50' : 'border-gray-300 bg-white hover:bg-gray-50'
            }`}
          >
            {emoji}
          </button>
        )
      })}
    </div>
  )
}
