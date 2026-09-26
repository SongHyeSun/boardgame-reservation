import { useQuery, type QueryClient } from '@tanstack/react-query'
import { getMe } from '../api/auth.ts'
import { ApiError } from '../types/api.ts'

export const meQueryKey = ['me'] as const

/**
 * 세션이 끊겼다면(401: 만료·관리자 승인으로 인한 세션 무효화) me 를 다시 조회해 화면이 로그아웃 상태를 따르게 한다.
 * `also` 에 준 상태(예: 관리자 신청이 409)도 me 가 서버와 어긋난 것이므로 다시 조회한다.
 * mutation 의 onError 에서 return 하면 재조회가 끝날 때까지 pending 이 유지된다.
 */
export function refetchMeAfterError(
  queryClient: QueryClient,
  error: Error,
  also: readonly number[] = [],
): Promise<void> | undefined {
  if (error instanceof ApiError && (error.status === 401 || also.includes(error.status))) {
    return queryClient.invalidateQueries({ queryKey: meQueryKey })
  }
  return undefined
}

/** data: 로그인 → MemberResponse, 비로그인 → null. 네트워크/5xx 오류만 error 로 올라온다. */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: getMe,
    staleTime: 60_000,
  })
}
