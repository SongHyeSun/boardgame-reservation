import { Navigate, Outlet, useLocation } from 'react-router'
import { useMe } from '../hooks/useMe.ts'
import ErrorMessage from './ErrorMessage.tsx'
import Loading from './Loading.tsx'

/** 로그인 필요 라우트. 비로그인이면 /login?redirect=현재경로 로 보낸다. */
export default function ProtectedRoute() {
  const { data: me, isPending, isError, error } = useMe()
  const location = useLocation()

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (me === null) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return <Outlet />
}
