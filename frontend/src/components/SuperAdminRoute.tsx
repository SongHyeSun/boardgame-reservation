import { Navigate, Outlet } from 'react-router'
import { useMe } from '../hooks/useMe.ts'
import { isSuperAdmin } from '../utils/role.ts'

/**
 * SUPER_ADMIN 전용 라우트 (UX용, 실제 방어는 백엔드 403).
 * ProtectedRoute 안에 중첩해서 쓰므로 여기 도달했을 땐 로그인 상태다.
 * 안내 문구(state.notice)를 보여 주는 화면이 게임 목록이라 AdminRoute 와 같이 /boardgames 로 보낸다.
 */
export default function SuperAdminRoute() {
  const { data: me } = useMe()

  if (!me || !isSuperAdmin(me.role)) {
    return <Navigate to="/boardgames" replace state={{ notice: '최고 관리자만 접근할 수 있습니다.' }} />
  }
  return <Outlet />
}
