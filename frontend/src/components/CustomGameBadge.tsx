interface CustomGameBadgeProps {
  customGame: boolean
}

/** 등록된 보드게임이 아니라 직접 입력한 게임(기타 게임) 파티일 때만 「기타 게임」을 보여 준다. customGame 이 false 면 아무것도 그리지 않는다. */
export default function CustomGameBadge({ customGame }: CustomGameBadgeProps) {
  if (!customGame) {
    return null
  }
  return (
    <span className="inline-block rounded bg-violet-100 px-2 py-0.5 text-xs font-medium text-violet-700">기타 게임</span>
  )
}
