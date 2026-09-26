import type { PlayMode } from '../types/boardgame.ts'
import { PLAY_MODE_LABEL } from '../utils/format.ts'

const BADGE_CLASS: Record<PlayMode, string> = {
  OFFLINE: 'bg-teal-100 text-teal-700',
  ONLINE: 'bg-sky-100 text-sky-700',
}

interface PlayModeBadgeProps {
  mode: PlayMode
}

/** 진행 방식 한 개. 게임처럼 여러 방식을 지원하면 호출부에서 availablePlayModes(game) 로 map 한다. */
export default function PlayModeBadge({ mode }: PlayModeBadgeProps) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${BADGE_CLASS[mode]}`}>
      {PLAY_MODE_LABEL[mode]}
    </span>
  )
}
