import { Navigate, Outlet } from 'react-router'
import { useMe } from '../hooks/useMe.ts'
import { isAdmin } from '../utils/role.ts'

/**
 * ADMIN 전용 라우트 (UX용, 실제 방어는 백엔드 403). SUPER_ADMIN 도 통과한다. (RoleHierarchy)
 * ProtectedRoute 안에 중첩해서 쓰므로 여기 도달했을 땐 로그인 상태다.
 */
export default function AdminRoute() {
  const { data: me } = useMe()

  if (!me || !isAdmin(me.role)) {
    return <Navigate to="/boardgames" replace state={{ notice: '관리자만 접근할 수 있습니다.' }} />
  }
  return <Outlet />
}
