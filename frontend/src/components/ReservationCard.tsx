import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { ReservationSummary } from '../types/reservation.ts'
import { formatDateTime } from '../utils/format.ts'
import { cancelReasonText, formatPeriod } from '../utils/reservation.ts'
import Avatar from './Avatar.tsx'
import GameImage from './GameImage.tsx'
import GameStatusBadge from './GameStatusBadge.tsx'
import ReservationStatusBadge from './ReservationStatusBadge.tsx'

interface ReservationCardProps {
  reservation: ReservationSummary
  /** 서울 기준 오늘(`yyyy-MM-dd`). "대여 완료" 표시 판단에 쓴다 */
  today: string
  /** 카드 아래 버튼 영역 (취소·승인·거절 등) */
  actions?: ReactNode
}

/**
 * 내 예약·관리자 예약 목록이 함께 쓰는 카드. 신청자(requester)가 있으면(관리자 목록) 그 정보도 보여 준다.
 * 게임이 운영 중지 상태면 「운영 중지」 배지, 운영 중지로 취소된 건은 그 사유를 문구로 알린다.
 */
export default function ReservationCard({ reservation, today, actions }: ReservationCardProps) {
  const requester = 'requester' in reservation ? reservation.requester : null
  const cancelText = reservation.status === 'CANCELLED' ? cancelReasonText(reservation.cancelReason) : null

  return (
    <li className="flex gap-4 rounded border border-gray-200 bg-white p-4">
      <GameImage imageUrl={reservation.imageUrl} name={reservation.boardGameName} className="w-24 shrink-0 self-start" />
      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h2 className="flex flex-wrap items-center gap-1.5 font-semibold">
            <Link to={`/boardgames/${reservation.boardGameId}`} className="hover:text-indigo-600">
              {reservation.boardGameName}
            </Link>
            <GameStatusBadge visible={reservation.boardGameVisible} />
          </h2>
          <ReservationStatusBadge reservation={reservation} today={today} />
        </div>

        {requester !== null && (
          <p className="flex flex-wrap items-center gap-1.5 text-sm text-gray-700">
            <Avatar avatar={requester.avatar} size="sm" nickname={requester.nickname} />
            <span className="font-medium">{requester.nickname}</span>
            {requester.name !== null && <span className="text-gray-500">{requester.name}</span>}
          </p>
        )}

        <p className="text-sm text-gray-700">{formatPeriod(reservation.startDate, reservation.endDate)}</p>
        {reservation.status === 'REJECTED' && reservation.rejectReason !== null && (
          <p className="text-sm text-red-700">거절 사유: {reservation.rejectReason}</p>
        )}
        {cancelText !== null && <p className="text-sm text-gray-600">{cancelText}</p>}
        <p className="text-xs text-gray-500">신청 {formatDateTime(reservation.createdAt)}</p>

        {actions && <div className="flex flex-wrap gap-2 pt-1">{actions}</div>}
      </div>
    </li>
  )
}
