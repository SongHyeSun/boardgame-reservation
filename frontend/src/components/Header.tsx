import { Menu } from 'lucide-react'
import { useCallback, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, NavLink } from 'react-router'
import { useLogout } from '../hooks/useAuth.ts'
import { useMe } from '../hooks/useMe.ts'
import Avatar from './Avatar.tsx'
import Button from './Button.tsx'
import { buttonClass } from './buttonStyle.ts'
import ErrorMessage from './ErrorMessage.tsx'
import Logo from './Logo.tsx'
import MobileMenu from './MobileMenu.tsx'
import { getNavItems } from './navItems.ts'
import NotificationBell from './NotificationBell.tsx'

const desktopNavClass = ({ isActive }: { isActive: boolean }) =>
  `relative inline-flex h-(--header-h) items-center px-3 text-body after:absolute after:inset-x-3 after:bottom-0 after:h-[3px] after:rounded-t-[3px] ${
    isActive ? 'font-semibold text-ink after:bg-meeple' : 'font-medium text-ink-muted hover:text-ink'
  }`

/**
 * 모바일(lg 미만): 로고 · 알림 · 아바타 · 햄버거 → 전체 화면 메뉴. lg 이상: 로고 · 가로 메뉴 · 알림 · 닉네임님 · 로그아웃.
 * 메뉴 항목은 navItems 하나를 두 곳이 나눠 쓴다.
 */
export default function Header() {
  const { data: me, isPending, isError, error } = useMe()
  const logout = useLogout()
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = useCallback(() => setMenuOpen(false), [])

  // 로딩 중엔 오른쪽을 비워 둔다 (로그인/회원가입 → 닉네임 깜빡임 방지)
  const resolved = !isPending && !isError
  const { main, admin } = getNavItems(me ?? null)

  return (
    <header className="sticky top-0 z-(--z-header) border-b border-line bg-surface">
      <div className="flex h-(--header-h) items-center gap-0.5 pl-4 pr-2 lg:gap-2 lg:px-8">
        <Logo />

        {/* 가로 메뉴 (lg 이상) */}
        <nav aria-label="주 메뉴" className="ml-6 mr-auto hidden items-center gap-1 lg:flex">
          {[...main, ...admin].map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} className={desktopNavClass}>
              {label}
            </NavLink>
          ))}
        </nav>
        <span className="mr-auto lg:hidden" />

        {isError && <ErrorMessage message={error.message} />}

        {resolved && me != null && (
          <>
            <NotificationBell />
            {/* 모바일은 아바타만, lg 는 닉네임님까지 */}
            <Link
              to="/me"
              aria-label={`${me.nickname}님 내 정보`}
              className="inline-flex h-10 items-center gap-2 rounded-full pl-1 pr-1 text-[14px] font-semibold text-ink hover:bg-sunken lg:pr-2.5"
            >
              <Avatar avatar={me.avatar} size="sm" nickname={me.nickname} seat={me.id} />
              <span className="hidden lg:inline">{me.nickname}님</span>
            </Link>
            <span className="hidden lg:block">
              <Button variant="ghost" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
                {logout.isPending ? '로그아웃 중…' : '로그아웃'}
              </Button>
            </span>
          </>
        )}
        {resolved && me === null && (
          <>
            <Link to="/login" className={buttonClass({ variant: 'ghost', size: 'sm' })}>
              로그인
            </Link>
            <span className="hidden lg:block">
              <Link to="/signup" className={buttonClass({ size: 'sm' })}>
                회원가입
              </Link>
            </span>
          </>
        )}

        <span className="lg:hidden">
          <Button variant="icon" onClick={() => setMenuOpen(true)} aria-label="메뉴 열기" aria-expanded={menuOpen}>
            <Menu aria-hidden className="size-5" />
          </Button>
        </span>
      </div>
      {logout.isError && (
        <div className="px-4 pb-2 lg:px-8">
          <ErrorMessage message={logout.error.message} />
        </div>
      )}

      {/* 헤더(z-30) 안에 두면 하단 고정 바(z-40) 아래로 깔리므로 body 로 뺀다 */}
      {menuOpen &&
        createPortal(
          <MobileMenu
            me={me ?? null}
            main={main}
            admin={admin}
            loggingOut={logout.isPending}
            onLogout={() => logout.mutate()}
            onClose={closeMenu}
          />,
          document.body,
        )}
    </header>
  )
}
