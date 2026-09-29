interface RemainChipProps {
  remaining: number
}

/** "남은 자리 N" 노란 칩. meeple 은 작은 강조에만 쓰는 색이라 이 크기로만 쓴다. */
export default function RemainChip({ remaining }: RemainChipProps) {
  return (
    <span className="inline-flex items-baseline gap-1 rounded-full bg-meeple py-0.5 pl-2 pr-2.5 text-caption text-on-meeple">
      남은 자리
      <b className="font-display text-[16px]/5 font-normal">{remaining}</b>
    </span>
  )
}
