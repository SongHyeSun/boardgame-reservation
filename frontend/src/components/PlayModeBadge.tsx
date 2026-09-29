import { Armchair, Wifi } from 'lucide-react'
import type { PlayMode } from '../types/boardgame.ts'
import { PLAY_MODE_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<PlayMode, string> = {
  OFFLINE: 'border-line-strong bg-surface text-ink',
  ONLINE: 'border-info bg-surface text-info',
}

interface PlayModeBadgeProps {
  mode: PlayMode
}

/** 진행 방식 한 개. 게임처럼 여러 방식을 지원하면 호출부에서 availablePlayModes(game) 로 map 한다. */
export default function PlayModeBadge({ mode }: PlayModeBadgeProps) {
  const Icon = mode === 'ONLINE' ? Wifi : Armchair
  return (
    <span className={`inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-sm border px-2 text-caption ${BADGE_CLASS[mode]}`}>
      <Icon aria-hidden className="size-3" />
      {PLAY_MODE_LABEL[mode]}
    </span>
  )
}
