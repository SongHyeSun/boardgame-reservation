import type { PlayMode } from '../../types/boardgame.ts'
import { PLAY_MODE_LABEL, PLAY_MODES } from '../../utils/format.ts'

interface PlayModeFieldProps {
  value: PlayMode | ''
  onChange: (mode: PlayMode) => void
  /** 고를 수 있는 방식. null = 제한 없음(기타 게임·게임 미선택). 나머지 방식은 비활성으로 보여 준다 */
  supported: PlayMode[] | null
  error?: string
}

/**
 * 진행 방식 (오프라인 / 온라인). 파티는 방식 하나를 반드시 고른다.
 * 모양은 세그먼트 버튼이지만 실제 입력은 라디오라서 키보드(방향키)·폼 동작은 그대로다.
 */
export default function PlayModeField({ value, onChange, supported, error }: PlayModeFieldProps) {
  const onlyMode = supported !== null && supported.length === 1 ? supported[0] : undefined

  return (
    <fieldset>
      <legend className="mb-1.5 block text-small font-semibold text-ink">진행 방식</legend>
      <div className="flex gap-0.5 rounded-md bg-sunken p-[3px]">
        {PLAY_MODES.map((mode) => {
          const disabled = supported !== null && !supported.includes(mode)
          return (
            <label
              key={mode}
              className={`flex h-9 flex-1 items-center justify-center rounded-lg text-[14px] font-semibold text-ink-muted has-checked:bg-surface has-checked:text-ink has-checked:shadow-card has-focus-visible:outline-2 has-focus-visible:outline-felt ${
                disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'
              }`}
            >
              <input
                type="radio"
                name="playMode"
                value={mode}
                checked={value === mode}
                disabled={disabled}
                onChange={() => onChange(mode)}
                className="sr-only"
              />
              {PLAY_MODE_LABEL[mode]}
            </label>
          )
        })}
      </div>
      {onlyMode && (
        <p className="mt-1.5 text-small text-ink-muted">이 게임은 {PLAY_MODE_LABEL[onlyMode]}으로만 진행할 수 있어요.</p>
      )}
      {error && <p className="mt-1.5 text-small font-medium text-danger">{error}</p>}
    </fieldset>
  )
}
