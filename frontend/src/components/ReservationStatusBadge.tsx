import type { ReservationStatus } from '../types/reservation.ts'
import { RESERVATION_COMPLETED_LABEL, RESERVATION_STATUS_LABEL } from '../utils/format.ts'
import { displayStatus, type ReservationDisplayStatus } from '../utils/reservation.ts'

const BADGE_CLASS: Record<ReservationDisplayStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  CANCELLED: 'bg-gray-200 text-gray-700',
  COMPLETED: 'bg-blue-100 text-blue-700',
}

interface ReservationStatusBadgeProps {
  reservation: { status: ReservationStatus; endDate: string }
  /** 서울 기준 오늘(`yyyy-MM-dd`). 종료일이 지난 승인 건을 "대여 완료"로 바꿔 보여 주는 데 쓴다 */
  today: string
}

export default function ReservationStatusBadge({ reservation, today }: ReservationStatusBadgeProps) {
  const status = displayStatus(reservation, today)
  const label = status === 'COMPLETED' ? RESERVATION_COMPLETED_LABEL : RESERVATION_STATUS_LABEL[status]
  return <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${BADGE_CLASS[status]}`}>{label}</span>
}
