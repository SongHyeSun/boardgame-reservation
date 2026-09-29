import { Pause } from 'lucide-react'

interface GameStatusBadgeProps {
  visible: boolean
}

/** 게임이 숨김(운영 중지) 상태일 때만 「운영 중지」를 보여 준다. visible 이면 아무것도 그리지 않으므로 호출부에서 조건문이 필요 없다. */
export default function GameStatusBadge({ visible }: GameStatusBadgeProps) {
  if (visible) {
    return null
  }
  return (
    <span className="inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-sm border px-2 text-caption border-transparent bg-suspend text-on-suspend">
      <Pause aria-hidden className="size-3" />
      운영 중지
    </span>
  )
}
