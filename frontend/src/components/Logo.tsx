import { Link } from 'react-router'

interface LogoProps {
  onClick?: () => void
}

/** 헤더·모바일 메뉴 공용 로고. 펠트 정사각형에 미플 점 3개 */
export default function Logo({ onClick }: LogoProps) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className="flex items-center gap-2 whitespace-nowrap font-display text-[18px] leading-none text-ink"
    >
      <svg viewBox="0 0 26 26" aria-hidden className="size-[26px] shrink-0">
        <rect width="26" height="26" rx="7" className="fill-felt" />
        <circle cx="8" cy="8" r="2.5" className="fill-meeple" />
        <circle cx="13" cy="13" r="2.5" className="fill-meeple" />
        <circle cx="18" cy="18" r="2.5" className="fill-meeple" />
      </svg>
      보드게임 동아리
    </Link>
  )
}
