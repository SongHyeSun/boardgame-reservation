import { ChevronRight, X } from 'lucide-react'
import { useEffect } from 'react'
import { Link, NavLink } from 'react-router'
import type { MemberResponse } from '../types/auth.ts'
import { ROLE_LABEL } from '../utils/format.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import { buttonClass } from './buttonStyle.ts'
import Logo from './Logo.tsx'
import type { NavItem } from './navItems.ts'

interface MobileMenuProps {
  me: MemberResponse | null
  main: NavItem[]
  admin: NavItem[]
  loggingOut: boolean
  onLogout: () => void
  onClose: () => void
}

const itemClass = ({ isActive }: { isActive: boolean }) =>
  `flex h-13 items-center gap-3.5 px-5 text-[16px] ${
    isActive ? 'bg-felt-soft font-bold text-felt' : 'font-medium text-ink'
  }`

function MenuList({ items, onClose }: { items: NavItem[]; onClose: () => void }) {
  return (
    <ul className="flex flex-col py-2">
      {items.map(({ to, label, icon: Icon, end }) => (
        <li key={to}>
          <NavLink to={to} end={end} onClick={onClose} className={itemClass}>
            <Icon aria-hidden className="size-5 shrink-0" />
            {label}
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

/** 모바일(lg 미만) 전체 화면 메뉴. 열려 있을 때만 마운트한다. 링크를 누르거나 X·Escape 로 닫는다 */
export default function MobileMenu({ me, main, admin, loggingOut, onLogout, onClose }: MobileMenuProps) {
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose])

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="메뉴"
      className="fixed inset-0 z-(--z-dropdown) flex flex-col overflow-y-auto bg-surface lg:hidden"
    >
      <div className="flex h-(--header-h) shrink-0 items-center justify-between pl-4 pr-2">
        <Logo onClick={onClose} />
        <Button variant="icon" onClick={onClose} aria-label="메뉴 닫기" autoFocus>
          <X aria-hidden className="size-5" />
        </Button>
      </div>

      {me !== null && (
        <Link to="/me" onClick={onClose} className="mx-4 my-2 flex items-center gap-3.5 rounded-lg bg-table p-3.5">
          <Avatar avatar={me.avatar} size="lg" nickname={me.nickname} seat={me.id} />
          <span className="min-w-0 grow">
            <b className="block truncate text-title">{me.nickname}님</b>
            <span className="text-small text-ink-muted">{ROLE_LABEL[me.role]} · 내 정보 보기</span>
          </span>
          <ChevronRight aria-hidden className="size-[18px] shrink-0 text-ink-muted" />
        </Link>
      )}

      <nav aria-label="주 메뉴">
        <MenuList items={main} onClose={onClose} />
        {admin.length > 0 && (
          <>
            <div className="px-5 pb-1 pt-3 text-caption text-ink-muted">관리</div>
            <MenuList items={admin} onClose={onClose} />
          </>
        )}
      </nav>

      <div className="mt-auto flex flex-col gap-2 px-5 pb-[calc(24px+env(safe-area-inset-bottom))] pt-6">
        {me === null ? (
          <>
            <Link to="/login" onClick={onClose} className={buttonClass({ variant: 'secondary', block: true })}>
              로그인
            </Link>
            <Link to="/signup" onClick={onClose} className={buttonClass({ block: true })}>
              회원가입
            </Link>
          </>
        ) : (
          <Button variant="secondary" block onClick={onLogout} disabled={loggingOut}>
            {loggingOut ? '로그아웃 중…' : '로그아웃'}
          </Button>
        )}
      </div>
    </div>
  )
}
