import { Link } from 'react-router'

interface FilterChipProps {
  to: string
  current: boolean
  children: string
}

/** URL 로 상태를 바꾸는 필터 칩(링크). 선택된 칩은 aria-current="page" */
export default function FilterChip({ to, current, children }: FilterChipProps) {
  return (
    <Link
      to={to}
      aria-current={current ? 'page' : undefined}
      className={`inline-flex h-9 shrink-0 items-center whitespace-nowrap rounded-full border px-3.5 text-[14px] ${
        current
          ? 'border-felt bg-felt-soft font-semibold text-felt'
          : 'border-line-strong bg-surface font-medium text-ink hover:bg-sunken'
      }`}
    >
      {children}
    </Link>
  )
}
