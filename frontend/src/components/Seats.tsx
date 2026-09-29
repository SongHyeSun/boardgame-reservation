/** 파티 좌석 점(●●○○) + "참여/정원". 정원이 많으면 점 없이 숫자만 보여 준다. */
const MAX_DOTS = 8

interface SeatsProps {
  current: number
  capacity: number
}

export default function Seats({ current, capacity }: SeatsProps) {
  const showDots = capacity <= MAX_DOTS
  return (
    <span className="inline-flex items-center gap-1" role="img" aria-label={`${capacity}명 중 ${current}명 참여`}>
      {showDots &&
        Array.from({ length: capacity }, (_, index) => (
          <i
            key={index}
            aria-hidden
            className={`size-3 rounded-full ${index < current ? 'bg-felt' : 'ring-[1.5px] ring-inset ring-line-strong'}`}
          />
        ))}
      <span aria-hidden className={`text-small font-semibold tabular-nums text-ink ${showDots ? 'ml-1.5' : ''}`}>
        {showDots ? `${current}/${capacity}` : `${current}/${capacity}명`}
      </span>
    </span>
  )
}
