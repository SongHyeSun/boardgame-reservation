interface GameStatusBadgeProps {
  visible: boolean
}

/** 게임이 숨김(운영 중지) 상태일 때만 「운영 중지」를 보여 준다. visible 이면 아무것도 그리지 않으므로 호출부에서 조건문이 필요 없다. */
export default function GameStatusBadge({ visible }: GameStatusBadgeProps) {
  if (visible) {
    return null
  }
  return (
    <span className="inline-block rounded bg-orange-100 px-2 py-0.5 text-xs font-medium text-orange-700">운영 중지</span>
  )
}
