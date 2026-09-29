import {
  CalendarDays,
  ClipboardCheck,
  Dices,
  MessageSquare,
  ShieldCheck,
  SquarePlus,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { MemberResponse } from '../types/auth.ts'
import { isAdmin, isSuperAdmin } from '../utils/role.ts'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** NavLink end — 하위 경로에서는 활성으로 보이지 않게 */
  end?: boolean
}

interface NavItems {
  main: NavItem[]
  /** 관리 그룹(관리자만) */
  admin: NavItem[]
}

/** 가로 메뉴(lg)와 모바일 전체 화면 메뉴가 같은 목록을 쓴다 */
export function getNavItems(me: MemberResponse | null): NavItems {
  const main: NavItem[] = [
    { to: '/boardgames', label: '보드게임', icon: Dices, end: true },
    { to: '/parties', label: '파티', icon: Users, end: true },
  ]
  const admin: NavItem[] = []
  if (me !== null) {
    main.push(
      { to: '/chat', label: 'AI 추천', icon: MessageSquare },
      { to: '/me/reservations', label: '내 예약', icon: CalendarDays },
    )
    if (isAdmin(me.role)) {
      admin.push(
        { to: '/boardgames/new', label: '게임 등록', icon: SquarePlus },
        { to: '/admin/reservations', label: '예약 관리', icon: ClipboardCheck },
      )
    }
    if (isSuperAdmin(me.role)) {
      admin.push({ to: '/admin/admin-requests', label: '관리자 승인', icon: ShieldCheck })
    }
  }
  return { main, admin }
}
