import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { AVATAR_IMAGE_ACCEPT } from '../utils/avatar.ts'
import { validateAvatarImage } from '../utils/validation.ts'

interface ImageInputProps {
  id: string
  /** 새로 고른 파일 (부모가 들고 있는 값). 이 값이 곧 미리보기 기준이다 */
  file: File | null
  onChange: (file: File | null) => void
  /** 파일을 고르지 않았을 때 보여 줄 서버 저장 이미지 (수정 화면) */
  currentUrl?: string | null
  /** 제출 검증 등 부모가 내려주는 오류 */
  error?: string
}

/**
 * 이미지 선택 + 미리보기 + 제거. 형식(jpg/jpeg/png/webp)·5MB 는 선택 즉시 검사하고,
 * 통과하지 못한 파일은 부모에 올리지 않는다(선택을 비우고 오류만 보여 줌).
 * file 은 이 컴포넌트의 핸들러로만 바뀐다. (이모지 모드로 전환하면 이 컴포넌트가 언마운트되며 파일은 부모가 버린다)
 * 미리보기 object URL 은 effect 가 아니라 핸들러에서 만들고 교체·언마운트 시 해제한다.
 */
export default function ImageInput({ id, file, onChange, currentUrl = null, error }: ImageInputProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const previewRef = useRef<string | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [selectError, setSelectError] = useState<string | null>(null)

  useEffect(
    () => () => {
      if (previewRef.current !== null) {
        URL.revokeObjectURL(previewRef.current)
      }
    },
    [],
  )

  /** 미리보기를 새 파일로 교체(null 이면 제거)하고 이전 URL 은 해제한다. native input 값도 파일과 맞춘다. */
  function replaceSelection(selected: File | null) {
    if (previewRef.current !== null) {
      URL.revokeObjectURL(previewRef.current)
    }
    const url = selected === null ? null : URL.createObjectURL(selected)
    previewRef.current = url
    setPreviewUrl(url)
    if (selected === null && inputRef.current) {
      inputRef.current.value = ''
    }
    onChange(selected)
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null
    if (selected === null) {
      setSelectError(null)
      replaceSelection(null)
      return
    }
    const message = validateAvatarImage(selected)
    setSelectError(message ?? null)
    replaceSelection(message === undefined ? selected : null)
  }

  function handleClear() {
    setSelectError(null)
    replaceSelection(null)
  }

  const shownError = selectError ?? error
  const shownPreview = file !== null ? previewUrl : null
  const shownUrl = shownPreview ?? currentUrl

  return (
    <div className="space-y-2">
      {shownUrl && (
        <div className="flex items-center gap-3">
          <img
            src={shownUrl}
            alt={shownPreview ? '선택한 이미지 미리보기' : '현재 프로필 이미지'}
            className="h-20 w-20 rounded-full border border-gray-200 object-cover"
          />
          <span className="text-xs text-gray-500">{shownPreview ? '선택한 이미지' : '현재 이미지'}</span>
        </div>
      )}
      <input
        ref={inputRef}
        id={id}
        type="file"
        accept={AVATAR_IMAGE_ACCEPT}
        onChange={handleChange}
        aria-invalid={shownError ? true : undefined}
        aria-describedby={shownError ? `${id}-error` : `${id}-hint`}
        className="block w-full text-sm text-gray-600 file:mr-3 file:rounded file:border file:border-gray-300 file:bg-white file:px-3 file:py-1.5 file:text-sm file:text-gray-700 hover:file:bg-gray-50"
      />
      <p id={`${id}-hint`} className="text-xs text-gray-500">
        jpg, png, webp · 5MB 이하
      </p>
      {file !== null && (
        <button type="button" onClick={handleClear} className="text-sm text-gray-600 hover:underline">
          선택 취소
        </button>
      )}
      {shownError && (
        <p id={`${id}-error`} className="text-sm text-red-600">
          {shownError}
        </p>
      )}
    </div>
  )
}
