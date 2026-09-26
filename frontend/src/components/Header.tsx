import { Link, NavLink } from 'react-router'
import { useLogout } from '../hooks/useAuth.ts'
import { useMe } from '../hooks/useMe.ts'
import { isAdmin, isSuperAdmin } from '../utils/role.ts'
import Avatar from './Avatar.tsx'
import ErrorMessage from './ErrorMessage.tsx'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'font-semibold text-indigo-600' : 'text-gray-700 hover:text-indigo-600'

function AuthMenu() {
  const { data: me, isPending, isError, error } = useMe()
  const logout = useLogout()

  // 로딩 중엔 비워 둔다 (로그인/회원가입 → 닉네임 깜빡임 방지)
  if (isPending) {
    return null
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (me === null) {
    return (
      <>
        <NavLink to="/login" className={navLinkClass}>
          로그인
        </NavLink>
        <NavLink to="/signup" className={navLinkClass}>
          회원가입
        </NavLink>
      </>
    )
  }
  return (
    <>
      <NavLink to="/parties/new" className={navLinkClass}>
        파티 만들기
      </NavLink>
      {isAdmin(me.role) && (
        <NavLink to="/boardgames/new" className={navLinkClass}>
          게임 등록
        </NavLink>
      )}
      {isSuperAdmin(me.role) && (
        <NavLink to="/admin/admin-requests" className={navLinkClass}>
          관리자 승인
        </NavLink>
      )}
      <NavLink to="/me" className={(state) => `flex items-center gap-2 ${navLinkClass(state)}`}>
        <Avatar avatar={me.avatar} size="sm" nickname={me.nickname} />
        {me.nickname}님
      </NavLink>
      <button
        type="button"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
        className="text-gray-700 hover:text-indigo-600 disabled:opacity-50"
      >
        {logout.isPending ? '로그아웃 중…' : '로그아웃'}
      </button>
      {logout.isError && <ErrorMessage message={logout.error.message} />}
    </>
  )
}

export default function Header() {
  return (
    <header className="border-b border-gray-200 bg-white">
      <nav className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3">
        <Link to="/" className="mr-2 text-lg font-bold text-indigo-600">
          보드게임 예약
        </Link>
        <NavLink to="/boardgames" end className={navLinkClass}>
          게임
        </NavLink>
        <NavLink to="/parties" end className={navLinkClass}>
          파티
        </NavLink>
        <div className="ml-auto flex items-center gap-x-5">
          <AuthMenu />
        </div>
      </nav>
    </header>
  )
}
