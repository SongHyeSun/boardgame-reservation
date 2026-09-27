import type {
  AdminReservationResponse,
  AvailabilityResponse,
  ReservationCreateRequest,
  ReservationResponse,
  ReservationStatus,
} from '../types/reservation.ts'
import { request } from './client.ts'

/** 누구나. from~to 양끝 포함 최대 62일. 온라인 전용(409)·숨김 게임(409)·범위 오류(400) */
export function getAvailability(boardGameId: number, from: string, to: string): Promise<AvailabilityResponse[]> {
  return request<AvailabilityResponse[]>({
    method: 'GET',
    url: `/boardgames/${boardGameId}/availability`,
    params: { from, to },
  })
}

/** 로그인. 201, PENDING 으로 생성되며 승인 전에도 재고를 점유한다 */
export function createReservation(body: ReservationCreateRequest): Promise<ReservationResponse> {
  return request<ReservationResponse>({ method: 'POST', url: '/reservations', data: body })
}

/** 최신순. status 를 생략하면 전체 */
export function getMyReservations(status?: ReservationStatus): Promise<ReservationResponse[]> {
  return request<ReservationResponse[]>({ method: 'GET', url: '/reservations/me', params: { status } })
}

/** 본인 예약만(남의 예약은 404). PENDING·APPROVED 이고 시작일 전날까지만 가능(아니면 409) */
export function cancelReservation(id: number): Promise<ReservationResponse> {
  return request<ReservationResponse>({ method: 'PATCH', url: `/reservations/${id}/cancel` })
}

/** ADMIN. 내가 등록한 게임의 예약. 서버는 status 를 생략하면 PENDING 이라 항상 명시해서 보낸다 */
export function getAdminReservations(status: ReservationStatus): Promise<AdminReservationResponse[]> {
  return request<AdminReservationResponse[]>({ method: 'GET', url: '/admin/reservations', params: { status } })
}

/** 게임 소유 관리자만. PENDING 만 가능 */
export function approveReservation(id: number): Promise<AdminReservationResponse> {
  return request<AdminReservationResponse>({ method: 'PATCH', url: `/admin/reservations/${id}/approve` })
}

/** 게임 소유 관리자만. 사유는 선택(최대 100자)이라 없으면 본문을 보내지 않는다 */
export function rejectReservation(id: number, reason: string | null): Promise<AdminReservationResponse> {
  return request<AdminReservationResponse>({
    method: 'PATCH',
    url: `/admin/reservations/${id}/reject`,
    data: reason === null ? undefined : { reason },
  })
}
