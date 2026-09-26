import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approveAdminRequest, getAdminRequests, rejectAdminRequest } from '../api/adminRequests.ts'
import { ApiError } from '../types/api.ts'
import { refetchMeAfterError } from './useMe.ts'

const adminRequestsQueryKey = ['admin-requests'] as const

export function useAdminRequests() {
  return useQuery({
    queryKey: adminRequestsQueryKey,
    queryFn: getAdminRequests,
  })
}

/**
 * 승인/거절. 성공하면 목록을 다시 조회한다.
 * 이미 처리됐거나(409) 없는 회원(404)이면 목록이 서버와 어긋난 것이므로 실패해도 다시 조회하고, 서버 message 는 화면이 그대로 보여 준다.
 * 401(세션 만료)이면 me 도 다시 조회해 보호 라우트가 로그아웃 상태를 따르게 한다.
 * promise 를 return 하므로 재조회가 끝날 때까지 mutation 은 pending 이다.
 */
export function useDecideAdminRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ memberId, approved }: { memberId: number; approved: boolean }) =>
      approved ? approveAdminRequest(memberId) : rejectAdminRequest(memberId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminRequestsQueryKey }),
    onError: (error) => {
      const tasks: Promise<unknown>[] = []
      if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
        tasks.push(queryClient.invalidateQueries({ queryKey: adminRequestsQueryKey }))
      }
      const meTask = refetchMeAfterError(queryClient, error)
      if (meTask) {
        tasks.push(meTask)
      }
      return Promise.all(tasks)
    },
  })
}
