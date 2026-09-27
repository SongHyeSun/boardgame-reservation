// 예약 화면 판단 로직. 서버가 상태를 바꾸지 않는 "대여 완료"와 취소 가능 여부를 화면이 같은 규칙으로 계산한다.
// today 는 todayInSeoul() 값(`yyyy-MM-dd`). 취소 가능 여부의 최종 판정은 서버(409 CANNOT_CANCEL_RESERVATION).

import type { CancelReason, ReservationStatus } from '../types/reservation.ts'
import { durationDays, formatIsoDate } from './date.ts'

/** 화면에 그리는 상태 = 서버 상태 + 대여 완료 */
export type ReservationDisplayStatus = ReservationStatus | 'COMPLETED'

/** 종료일이 지난 승인 건은 "대여 완료". 종료일 당일까지는 아직 대여 중이다 */
export function displayStatus(
  reservation: { status: ReservationStatus; endDate: string },
  today: string,
): ReservationDisplayStatus {
  return reservation.status === 'APPROVED' && reservation.endDate < today ? 'COMPLETED' : reservation.status
}

/** 서버 Reservation.canBeCancelledByMember 와 같은 규칙: 활성(승인 대기·승인) + 시작일 전날까지 (시작일 당일부터는 불가) */
export function canCancel(reservation: { status: ReservationStatus; startDate: string }, today: string): boolean {
  return (reservation.status === 'PENDING' || reservation.status === 'APPROVED') && today < reservation.startDate
}

/** `2026. 10. 5. (당일)` / `2026. 10. 5. ~ 2026. 10. 7. (3일)` */
export function formatPeriod(startDate: string, endDate: string): string {
  if (startDate === endDate) {
    return `${formatIsoDate(startDate)} (당일)`
  }
  return `${formatIsoDate(startDate)} ~ ${formatIsoDate(endDate)} (${durationDays(startDate, endDate)}일)`
}

export function cancelReasonText(reason: CancelReason | null): string | null {
  switch (reason) {
    case 'GAME_SUSPENDED':
      return '게임 운영 중지로 취소됨'
    case 'MEMBER':
      return '본인이 취소함'
    case null:
      return null
  }
}
