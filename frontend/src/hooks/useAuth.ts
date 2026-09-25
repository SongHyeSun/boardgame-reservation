import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { login, logout, signup } from '../api/auth.ts'
import { meQueryKey } from './useMe.ts'

/**
 * 성공 응답(MemberResponse)이 /me 와 같은 형태라 캐시에 바로 넣는다.
 * invalidate 만 하면 재조회 전까지 캐시가 null 이라, 이동한 보호 페이지에서 다시 /login 으로 튕긴다.
 * 이동은 GuestRoute 가 me 를 보고 처리한다.
 */
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: login,
    onSuccess: (member) => {
      queryClient.setQueryData(meQueryKey, member)
    },
  })
}

export function useSignup() {
  return useMutation({ mutationFn: signup })
}

/**
 * 이동을 먼저 한 뒤 캐시를 정리한다. (보호 페이지에서 로그아웃해도 ProtectedRoute 가 /login?redirect= 로 보내지 않도록)
 * queryClient.clear() 는 마운트된 useMe 옵저버(Header)를 갱신하지 않아 me 만 null 로 직접 넣고 나머지를 제거한다.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      navigate('/parties', { replace: true })
      queryClient.setQueryData(meQueryKey, null)
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] })
    },
  })
}
