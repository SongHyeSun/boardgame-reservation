import { Navigate, Outlet, useSearchParams } from 'react-router'
import { useMe } from '../hooks/useMe.ts'
import { resolveRedirect } from '../utils/navigation.ts'
import ErrorMessage from './ErrorMessage.tsx'
import Loading from './Loading.tsx'

/**
 * 비로그인 전용 라우트 (/login, /signup).
 * 로그인 상태면 ?redirect= (없거나 안전하지 않으면 /parties) 로 보낸다.
 * 로그인 성공 후 이동도 여기서 일어난다: useLogin 이 me 캐시를 채우면 이 라우트가 리렌더되며 이동.
 */
export default function GuestRoute() {
  const { data: me, isPending, isError, error } = useMe()
  const [searchParams] = useSearchParams()

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  if (me) {
    return <Navigate to={resolveRedirect(searchParams.get('redirect'))} replace />
  }
  return <Outlet />
}
