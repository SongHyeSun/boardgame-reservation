import type { AvatarType } from '../types/auth.ts'
import { changeAvatarMode, type AvatarFormState } from '../utils/avatar.ts'
import EmojiPicker from './EmojiPicker.tsx'
import ImageInput from './ImageInput.tsx'

interface AvatarFieldProps {
  /** id 접두사 (한 화면에 하나만 쓰므로 페이지가 정한다) */
  id: string
  value: AvatarFormState
  onChange: (next: AvatarFormState) => void
  /** 서버에 저장된 이미지 URL. 수정 화면에서만 넘긴다(가입 화면은 생략) */
  storedImageUrl?: string | null
  /** 제출 검증 오류 */
  error?: string
}

const MODES: readonly { value: AvatarType; label: string }[] = [
  { value: 'EMOJI', label: '이모지' },
  { value: 'IMAGE', label: '이미지' },
]

/**
 * 가입·내 정보 수정이 공유하는 아바타 입력 (이모지 선택 또는 이미지 업로드).
 * 상태 전이 규칙(이모지 모드 전환 시 새 파일 초기화, 이미지 모드 전환 시 삭제 표시 해제)은 utils/avatar.ts 의
 * changeAvatarMode 가 담당하고, 요청 조립은 toAvatarRequest 가 한다. 여기서는 그 함수만 호출한다.
 */
export default function AvatarField({ id, value, onChange, storedImageUrl = null, error }: AvatarFieldProps) {
  return (
    <fieldset>
      <legend className="mb-1 block text-sm font-medium text-gray-700">프로필 아바타</legend>

      <div className="mb-3 flex gap-4">
        {MODES.map((mode) => (
          <label key={mode.value} className="flex items-center gap-1.5 text-sm text-gray-700">
            <input
              type="radio"
              name={`${id}-mode`}
              checked={value.mode === mode.value}
              onChange={() => onChange(changeAvatarMode(value, mode.value))}
            />
            {mode.label}
          </label>
        ))}
      </div>

      {value.mode === 'EMOJI' ? (
        <div className="space-y-3">
          <EmojiPicker value={value.emoji} onChange={(emoji) => onChange({ ...value, emoji })} />
          {storedImageUrl && (
            <label className="flex items-center gap-1.5 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={value.removeImage}
                onChange={(event) => onChange({ ...value, removeImage: event.target.checked })}
              />
              저장된 프로필 이미지 삭제
            </label>
          )}
        </div>
      ) : (
        <ImageInput
          id={`${id}-image`}
          file={value.file}
          onChange={(file) => onChange({ ...value, file })}
          currentUrl={storedImageUrl}
          error={error}
        />
      )}
    </fieldset>
  )
}
