import { useMutation, useQuery, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import {
  approveReservation,
  cancelReservation,
  createReservation,
  getAdminReservations,
  getAvailability,
  getMyReservations,
  rejectReservation,
} from '../api/reservations.ts'
import { ApiError } from '../types/api.ts'
import type { ReservationCreateRequest, ReservationStatus } from '../types/reservation.ts'
import { refetchMeAfterError } from './useMe.ts'

const availabilityKey = (boardGameId: number): QueryKey => ['availability', boardGameId]
const myReservationsKey: QueryKey = ['reservations', 'me']
const adminReservationsKey: QueryKey = ['admin-reservations']

/**
 * 날짜별 남은 수량. 온라인 전용·운영 중지 게임은 서버가 409 라 호출하는 쪽이 조회하지 않는다.
 * 신청 직전에 다른 사람이 마감시킬 수 있으므로 창 포커스 시 재조회하는 기본 동작을 그대로 둔다.
 */
export function useAvailability(boardGameId: number, from: string, to: string) {
  return useQuery({
    queryKey: ['availability', boardGameId, from, to],
    queryFn: () => getAvailability(boardGameId, from, to),
  })
}

/** status 가 null 이면 전체 */
export function useMyReservations(status: ReservationStatus | null) {
  return useQuery({
    queryKey: ['reservations', 'me', status ?? 'ALL'],
    queryFn: () => getMyReservations(status ?? undefined),
  })
}

export function useAdminReservations(status: ReservationStatus) {
  return useQuery({
    queryKey: ['admin-reservations', status],
    queryFn: () => getAdminReservations(status),
  })
}

/**
 * 요청이 실패하면 화면이 서버와 어긋났을 수 있다. (재고 마감·이미 처리된 예약·이미 취소된 예약 등 409/404)
 * 서버 message 는 mutation.error 로 화면이 그대로 보여 주고, 여기서는 관련 조회를 다시 한다.
 * 401(세션 만료)이면 me 도 다시 조회해 보호 라우트가 로그아웃 상태를 따르게 한다.
 * promise 를 return 하므로 재조회가 끝날 때까지 mutation 은 pending 이다.
 */
function refetchAfterError(queryClient: QueryClient, error: Error, queryKeys: readonly QueryKey[]) {
  if (!(error instanceof ApiError)) {
    return undefined
  }
  return Promise.all([
    ...queryKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    refetchMeAfterError(queryClient, error),
  ])
}

/** 신청은 재고를 점유하므로 그 게임의 달력과 내 예약을 다시 조회한다. 실패(마감·중복·기간 위반)도 달력을 새로 받는다 */
export function useCreateReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ReservationCreateRequest) => createReservation(body),
    onSuccess: (_, body) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: availabilityKey(body.boardGameId) }),
        queryClient.invalidateQueries({ queryKey: myReservationsKey }),
      ]),
    onError: (error, body) => refetchAfterError(queryClient, error, [availabilityKey(body.boardGameId)]),
  })
}

/** mutate 인자 = 예약 id. 취소하면 재고 점유가 풀리므로 그 게임의 달력도 다시 조회한다 */
export function useCancelReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => cancelReservation(id),
    onSuccess: (reservation) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: myReservationsKey }),
        queryClient.invalidateQueries({ queryKey: availabilityKey(reservation.boardGameId) }),
      ]),
    onError: (error) => refetchAfterError(queryClient, error, [myReservationsKey]),
  })
}

type ReservationDecision = { id: number; approved: true } | { id: number; approved: false; reason: string | null }

/**
 * 소유 관리자의 승인/거절. 성공하면 관리자 목록을 다시 조회한다.
 * 거절은 재고 점유가 풀리므로 달력도 다시 조회한다. (승인은 신청 때 이미 점유했으므로 달력이 그대로다)
 * 이미 처리됐거나(409) 없는 예약(404)이어도 목록이 어긋난 것이므로 실패 시 같은 목록을 다시 조회한다.
 */
export function useDecideReservation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (decision: ReservationDecision) =>
      decision.approved ? approveReservation(decision.id) : rejectReservation(decision.id, decision.reason),
    onSuccess: (_, decision) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: adminReservationsKey }),
        decision.approved ? undefined : queryClient.invalidateQueries({ queryKey: ['availability'] }),
      ]),
    onError: (error) => refetchAfterError(queryClient, error, [adminReservationsKey]),
  })
}
