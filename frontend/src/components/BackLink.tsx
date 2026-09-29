import { ChevronLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

interface BackLinkProps {
  to: string
  children: ReactNode
}

/** 목록·상위 화면으로 돌아가는 링크. 화살표는 아이콘이 그리므로 문구에는 넣지 않는다 */
export default function BackLink({ to, children }: BackLinkProps) {
  return (
    <Link to={to} className="inline-flex h-8 items-center gap-1 text-[14px] font-semibold text-ink-muted hover:text-ink">
      <ChevronLeft aria-hidden className="size-[18px]" />
      {children}
    </Link>
  )
}
