import type { PlayMode } from '../../types/boardgame.ts'
import { PLAY_MODE_LABEL, PLAY_MODES } from '../../utils/format.ts'

interface PlayModeFieldProps {
  value: PlayMode | ''
  onChange: (mode: PlayMode) => void
  /** 고를 수 있는 방식. null = 제한 없음(기타 게임·게임 미선택). 나머지 방식은 비활성으로 보여 준다 */
  supported: PlayMode[] | null
  error?: string
}

/** 진행 방식 라디오 (오프라인 / 온라인). 파티는 방식 하나를 반드시 고른다. */
export default function PlayModeField({ value, onChange, supported, error }: PlayModeFieldProps) {
  const onlyMode = supported !== null && supported.length === 1 ? supported[0] : undefined

  return (
    <fieldset>
      <legend className="mb-1 block text-sm font-medium text-gray-700">진행 방식</legend>
      <div className="flex gap-4">
        {PLAY_MODES.map((mode) => {
          const disabled = supported !== null && !supported.includes(mode)
          return (
            <label
              key={mode}
              className={`flex items-center gap-1.5 text-sm ${disabled ? 'text-gray-400' : 'text-gray-700'}`}
            >
              <input
                type="radio"
                name="playMode"
                value={mode}
                checked={value === mode}
                disabled={disabled}
                onChange={() => onChange(mode)}
              />
              {PLAY_MODE_LABEL[mode]}
            </label>
          )
        })}
      </div>
      {onlyMode && <p className="mt-1 text-xs text-gray-500">이 게임은 {PLAY_MODE_LABEL[onlyMode]}으로만 진행할 수 있어요.</p>}
      {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
    </fieldset>
  )
}
