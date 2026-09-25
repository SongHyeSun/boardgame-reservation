import { Link, NavLink } from 'react-router'
import { useMe } from '../hooks/useMe.ts'
import ErrorMessage from './ErrorMessage.tsx'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'font-semibold text-indigo-600' : 'text-gray-700 hover:text-indigo-600'

function AuthMenu() {
  const { data: me, isPending, isError, error } = useMe()

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
      {me.role === 'ADMIN' && (
        <NavLink to="/boardgames/new" className={navLinkClass}>
          게임 등록
        </NavLink>
      )}
      <span className="text-gray-900">{me.nickname}님</span>
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
