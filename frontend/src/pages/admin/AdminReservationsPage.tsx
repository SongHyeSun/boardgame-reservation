import { useState } from 'react'
import { useSearchParams } from 'react-router'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import ReservationCard from '../../components/ReservationCard.tsx'
import StatusFilterTabs from '../../components/StatusFilterTabs.tsx'
import { useAdminReservations, useDecideReservation } from '../../hooks/useReservations.ts'
import type { AdminReservationResponse, ReservationStatus } from '../../types/reservation.ts'
import { todayInSeoul } from '../../utils/date.ts'
import { isReservationStatus, RESERVATION_STATUS_LABEL, RESERVATION_STATUSES } from '../../utils/format.ts'
import { formatPeriod } from '../../utils/reservation.ts'
import RejectReasonModal from './RejectReasonModal.tsx'

/** 서버는 status 를 안 주면 PENDING 이고 "전체"가 없다. 그래서 탭은 4개이고 요청에는 항상 status 를 명시한다 */
const DEFAULT_TAB: ReservationStatus = 'PENDING'

const EMPTY_MESSAGE: Record<ReservationStatus, string> = {
  PENDING: '승인 대기 중인 예약이 없습니다.',
  APPROVED: '승인한 예약이 없습니다.',
  REJECTED: '거절한 예약이 없습니다.',
  CANCELLED: '취소된 예약이 없습니다.',
}

/** ?status= → 탭. 없거나 잘못된 값이면 승인 대기 */
function parseTab(params: URLSearchParams): ReservationStatus {
  const status = params.get('status')
  return isReservationStatus(status) ? status : DEFAULT_TAB
}

function listPath(tab: ReservationStatus): string {
  return tab === DEFAULT_TAB ? '/admin/reservations' : `/admin/reservations?status=${tab}`
}

const APPROVE_BUTTON = 'rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50'
const REJECT_BUTTON = 'rounded border border-red-300 bg-white px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50'

/**
 * ADMIN 전용. 내가 등록한 게임의 예약만 온다. 승인/거절은 게임 소유 관리자만 가능하고 서버가 최종 검사한다(아니면 403).
 * 승인 대기(PENDING)만 처리할 수 있고, 거절은 사유(선택)를 모달로 받는다.
 */
export default function AdminReservationsPage() {
  const [searchParams] = useSearchParams()
  const tab = parseTab(searchParams)
  const { data: reservations, isPending, isError, error } = useAdminReservations(tab)
  const decide = useDecideReservation()
  const [rejecting, setRejecting] = useState<AdminReservationResponse | null>(null)
  const today = todayInSeoul()

  function handleApprove(reservation: AdminReservationResponse) {
    const message = `"${reservation.requester.nickname}"님의 "${reservation.boardGameName}" ${formatPeriod(reservation.startDate, reservation.endDate)} 대여를 승인할까요?`
    if (decide.isPending || !window.confirm(message)) {
      return
    }
    decide.reset()
    decide.mutate({ id: reservation.id, approved: true })
  }

  function openReject(reservation: AdminReservationResponse) {
    if (decide.isPending) {
      return
    }
    decide.reset()
    setRejecting(reservation)
  }

  function closeReject() {
    decide.reset()
    setRejecting(null)
  }

  function handleReject(reservation: AdminReservationResponse, reason: string | null) {
    decide.mutate({ id: reservation.id, approved: false, reason }, { onSuccess: () => setRejecting(null) })
  }

  // 처리 중인 예약의 id (없으면 null)
  const pendingId = decide.isPending ? decide.variables.id : null

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-bold">예약 관리</h1>

      <StatusFilterTabs
        label="상태 필터"
        items={RESERVATION_STATUSES.map((status) => ({
          key: status,
          label: RESERVATION_STATUS_LABEL[status],
          to: listPath(status),
          current: status === tab,
        }))}
      />

      {decide.isError && rejecting === null && <ErrorMessage message={decide.error.message} />}

      {isPending && <Loading />}
      {isError && <ErrorMessage message={error.message} />}
      {reservations && reservations.length === 0 && <EmptyMessage message={EMPTY_MESSAGE[tab]} />}
      {reservations && reservations.length > 0 && (
        <ul className="space-y-3">
          {reservations.map((reservation) => (
            <ReservationCard
              key={reservation.id}
              reservation={reservation}
              today={today}
              actions={
                reservation.status === 'PENDING' && (
                  <>
                    <button
                      type="button"
                      onClick={() => handleApprove(reservation)}
                      disabled={decide.isPending}
                      className={APPROVE_BUTTON}
                    >
                      {pendingId === reservation.id ? '처리 중…' : '승인'}
                    </button>
                    <button
                      type="button"
                      onClick={() => openReject(reservation)}
                      disabled={decide.isPending}
                      className={REJECT_BUTTON}
                    >
                      거절
                    </button>
                  </>
                )
              }
            />
          ))}
        </ul>
      )}

      {rejecting !== null && (
        <RejectReasonModal
          gameName={rejecting.boardGameName}
          requesterNickname={rejecting.requester.nickname}
          isPending={decide.isPending}
          errorMessage={decide.isError ? decide.error.message : null}
          onSubmit={(reason) => handleReject(rejecting, reason)}
          onClose={closeReject}
        />
      )}
    </section>
  )
}
