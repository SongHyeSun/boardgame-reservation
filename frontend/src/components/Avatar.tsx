import { useState } from 'react'
import type { Avatar as AvatarData } from '../types/auth.ts'

type AvatarSize = 'sm' | 'md' | 'lg'

const SIZE_CLASS: Record<AvatarSize, string> = {
  sm: 'h-6 w-6 text-sm',
  md: 'h-10 w-10 text-xl',
  lg: 'h-20 w-20 text-4xl',
}

interface AvatarProps {
  avatar: AvatarData
  size?: AvatarSize
  /** alt·aria-label 문구에 쓴다. 없으면 "프로필 이미지" */
  nickname?: string
}

/**
 * 이모지 또는 이미지 아바타. 그릴 때는 type 으로 판단한다. (imageUrl 은 EMOJI 여도 저장된 이미지가 있으면 내려온다)
 * 이미지를 불러오지 못하면(삭제·네트워크 오류) 이모지로 대체한다.
 */
export default function Avatar({ avatar, size = 'md', nickname }: AvatarProps) {
  // 실패한 URL 을 기억한다. 새로 업로드해 URL 이 바뀌면 다시 이미지를 시도한다.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const label = nickname ? `${nickname} 프로필 이미지` : '프로필 이미지'
  const imageUrl = avatar.type === 'IMAGE' ? avatar.imageUrl : null
  const containerClass = `inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-gray-100 leading-none ${SIZE_CLASS[size]}`

  if (imageUrl !== null && imageUrl !== failedUrl) {
    return (
      <span className={containerClass}>
        <img
          src={imageUrl}
          alt={label}
          onError={() => setFailedUrl(imageUrl)}
          className="h-full w-full object-cover"
        />
      </span>
    )
  }
  return (
    <span role="img" aria-label={label} className={containerClass}>
      {avatar.emoji}
    </span>
  )
}
