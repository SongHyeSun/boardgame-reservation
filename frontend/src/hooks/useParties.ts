import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { closeParty, createParty, getParties, getParty, joinParty, leaveParty } from '../api/parties.ts'
import { ApiError } from '../types/api.ts'
import type { PartyCreateRequest, PartyDetailResponse, PartyFilter } from '../types/party.ts'
import { meQueryKey } from './useMe.ts'

export function useParties(filter: PartyFilter = {}) {
  return useQuery({
    queryKey: ['parties', filter],
    queryFn: () => getParties(filter),
  })
}

export function useParty(id: number) {
  return useQuery({
    queryKey: ['party', id],
    queryFn: () => getParty(id),
  })
}

function invalidateParty(queryClient: QueryClient, id: number) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['party', id] }),
    queryClient.invalidateQueries({ queryKey: ['parties'] }),
  ])
}

/**
 * 요청이 실패하면 화면의 상태가 서버와 어긋났을 수 있다. (정원 마감·이미 참여·이미 마감 등 409/400)
 * 서버 message 는 mutation.error 로 화면이 그대로 보여 주고, 여기서는 상세·목록을 다시 조회한다.
 * 401(세션 만료)이면 me 도 다시 조회해 버튼과 보호 라우트가 로그아웃 상태를 따르게 한다.
 * promise 를 return 하므로 재조회가 끝날 때까지 mutation 은 pending 이다.
 */
function refetchAfterError(queryClient: QueryClient, error: Error, partyId?: number) {
  if (!(error instanceof ApiError)) {
    return undefined
  }
  const tasks: Promise<unknown>[] = []
  if (partyId !== undefined) {
    tasks.push(invalidateParty(queryClient, partyId))
  }
  if (error.status === 401) {
    tasks.push(queryClient.invalidateQueries({ queryKey: meQueryKey }))
  }
  return Promise.all(tasks)
}

export function useCreateParty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: PartyCreateRequest) => createParty(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['parties'] }),
    onError: (error) => refetchAfterError(queryClient, error),
  })
}

/**
 * 성공 응답의 remaining 을 상세 캐시에 바로 반영한 뒤 재조회한다.
 * 재조회가 끝날 때까지 pending 을 유지해, 참여자 목록이 갱신되기 전에 "참여하기"가 다시 눌리지 않게 한다.
 */
export function useJoinParty(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => joinParty(id),
    onSuccess: ({ remaining }) => {
      queryClient.setQueryData<PartyDetailResponse>(['party', id], (party) => party && { ...party, remaining })
      return invalidateParty(queryClient, id)
    },
    onError: (error) => refetchAfterError(queryClient, error, id),
  })
}

export function useLeaveParty(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => leaveParty(id),
    onSuccess: () => invalidateParty(queryClient, id),
    onError: (error) => refetchAfterError(queryClient, error, id),
  })
}

/** 호스트 전용 (서버가 403 으로 최종 검사) */
export function useCloseParty(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => closeParty(id),
    onSuccess: () => invalidateParty(queryClient, id),
    onError: (error) => refetchAfterError(queryClient, error, id),
  })
}
