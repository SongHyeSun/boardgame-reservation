import { Check } from 'lucide-react'
import type { ReservationStatus } from '../types/reservation.ts'
import { RESERVATION_COMPLETED_LABEL, RESERVATION_STATUS_LABEL } from '../utils/format.ts'
import { displayStatus, type ReservationDisplayStatus } from '../utils/reservation.ts'

const BADGE_CLASS: Record<ReservationDisplayStatus, string> = {
  PENDING: 'bg-meeple-soft text-meeple-ink',
  APPROVED: 'bg-felt-soft text-felt',
  REJECTED: 'bg-danger-soft text-danger',
  CANCELLED: 'bg-sunken text-ink-muted',
  COMPLETED: 'bg-done-soft text-done',
}

interface ReservationStatusBadgeProps {
  reservation: { status: ReservationStatus; endDate: string }
  /** 서울 기준 오늘(`yyyy-MM-dd`). 종료일이 지난 승인 건을 "대여 완료"로 바꿔 보여 주는 데 쓴다 */
  today: string
}

export default function ReservationStatusBadge({ reservation, today }: ReservationStatusBadgeProps) {
  const status = displayStatus(reservation, today)
  const label = status === 'COMPLETED' ? RESERVATION_COMPLETED_LABEL : RESERVATION_STATUS_LABEL[status]
  return (
    <span className={`inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-sm border px-2 text-caption border-transparent ${BADGE_CLASS[status]}`}>
      {status === 'APPROVED' && <Check aria-hidden className="size-3" />}
      {label}
    </span>
  )
}
