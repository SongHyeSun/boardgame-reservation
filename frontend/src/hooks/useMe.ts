import { useQuery } from '@tanstack/react-query'
import { getMe } from '../api/auth.ts'

export const meQueryKey = ['me'] as const

/** data: 로그인 → MemberResponse, 비로그인 → null. 네트워크/5xx 오류만 error 로 올라온다. */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: getMe,
    staleTime: 60_000,
  })
}
