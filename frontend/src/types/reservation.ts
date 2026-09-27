// reservation/dto 기준 (ReservationResponse, AdminReservationResponse, AvailabilityResponse, ReservationCreateRequest, ReservationRejectRequest)
// 날짜(LocalDate)는 `yyyy-MM-dd` 문자열로 주고받는다.

import type { Avatar } from './auth.ts'

export type ReservationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

/** MEMBER = 본인 취소, GAME_SUSPENDED = 게임 운영 중지(숨기기)로 서버가 취소 */
export type CancelReason = 'MEMBER' | 'GAME_SUSPENDED'

/** 날짜별 남은 수량 (stock - 그 날짜를 포함하는 활성 예약 수). 0 이면 "예약 마감" */
export interface AvailabilityResponse {
  date: string
  available: number
}

/** POST /reservations. 당일 대여는 startDate = endDate */
export interface ReservationCreateRequest {
  boardGameId: number
  startDate: string
  endDate: string
}

/**
 * 내 예약. "대여 완료"는 서버가 상태를 바꾸지 않으므로 화면이 APPROVED + endDate 지남으로 판단한다.
 * boardGameVisible 이 false 면 게임이 운영 중지 상태다.
 */
export interface ReservationResponse {
  id: number
  boardGameId: number
  boardGameName: string
  imageUrl: string | null // `/api/files/...`
  boardGameVisible: boolean
  startDate: string
  endDate: string
  status: ReservationStatus
  cancelReason: CancelReason | null
  rejectReason: string | null
  createdAt: string
}

/** 신청자. name 은 선택 입력이라 null 일 수 있다 */
export interface ReservationRequester {
  id: number
  nickname: string
  name: string | null
  avatar: Avatar
}

/** 소유 관리자용: ReservationResponse 의 필드 + 신청자 */
export interface AdminReservationResponse extends ReservationResponse {
  requester: ReservationRequester
}

/** 예약 카드(내 예약·관리자 목록)가 공통으로 그리는 필드 */
export type ReservationSummary = ReservationResponse | AdminReservationResponse
