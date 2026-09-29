import { useState } from 'react'
import type { Avatar as AvatarData } from '../types/auth.ts'

type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

const SIZE_CLASS: Record<AvatarSize, string> = {
  xs: 'size-6 text-[13px]',
  sm: 'size-8 text-[18px]',
  md: 'size-10 text-[22px]',
  lg: 'size-14 text-[31px]',
  xl: 'size-20 text-[44px]',
}

/** 이모지 아바타 바탕색. seat 값(memberId 등)을 6으로 나눈 나머지로 고른다 */
const SEAT_BG = ['bg-seat-red', 'bg-seat-blue', 'bg-seat-yellow', 'bg-seat-green', 'bg-seat-purple', 'bg-seat-orange']

interface AvatarProps {
  avatar: AvatarData
  size?: AvatarSize
  /** alt·aria-label 문구에 쓴다. 없으면 "프로필 이미지" */
  nickname?: string
  /** 파티장 표시(오른쪽 아래 노란 점) */
  host?: boolean
  /** 바탕색을 고르는 값(보통 memberId). 없으면 초록 */
  seat?: number
  /** 반응형 크기 덮어쓰기 등 배치용 추가 클래스 (예: 'lg:size-16 lg:text-[35px]') */
  className?: string
}

/**
 * 이모지 또는 이미지 아바타. 그릴 때는 type 으로 판단한다. (imageUrl 은 EMOJI 여도 저장된 이미지가 있으면 내려온다)
 * 이미지를 불러오지 못하면(삭제·네트워크 오류) 이모지로 대체한다.
 */
export default function Avatar({ avatar, size = 'md', nickname, host = false, seat, className = '' }: AvatarProps) {
  // 실패한 URL 을 기억한다. 새로 업로드해 URL 이 바뀌면 다시 이미지를 시도한다.
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const label = nickname ? `${nickname} 프로필 이미지` : '프로필 이미지'
  const imageUrl = avatar.type === 'IMAGE' ? avatar.imageUrl : null
  const seatBg = seat === undefined ? 'bg-seat-green' : SEAT_BG[((seat % 6) + 6) % 6]
  const circleClass = `inline-flex size-full select-none items-center justify-center overflow-hidden rounded-full leading-none ${seatBg}`

  // 파티장 점이 원 밖으로 나오므로 바깥 래퍼는 overflow 를 자르지 않는다
  const wrapperClass = `relative inline-flex shrink-0 ${SIZE_CLASS[size]} ${className}`
  const hostDot = host && (
    <span aria-hidden className="absolute -bottom-0.5 -right-0.5 size-3.5 rounded-full bg-meeple ring-2 ring-surface" />
  )

  if (imageUrl !== null && imageUrl !== failedUrl) {
    return (
      <span className={wrapperClass}>
        <span className={circleClass}>
          <img
            src={imageUrl}
            alt={label}
            onError={() => setFailedUrl(imageUrl)}
            className="h-full w-full object-cover"
          />
        </span>
        {hostDot}
      </span>
    )
  }
  return (
    <span className={wrapperClass}>
      <span role="img" aria-label={label} className={circleClass}>
        {avatar.emoji}
      </span>
      {hostDot}
    </span>
  )
}
