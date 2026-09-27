import { Link, useLocation, useSearchParams } from 'react-router'
import EmptyMessage from '../../components/EmptyMessage.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import Loading from '../../components/Loading.tsx'
import ReservationCard from '../../components/ReservationCard.tsx'
import StatusFilterTabs from '../../components/StatusFilterTabs.tsx'
import { useCancelReservation, useMyReservations } from '../../hooks/useReservations.ts'
import type { ReservationStatus } from '../../types/reservation.ts'
import { todayInSeoul } from '../../utils/date.ts'
import { isReservationStatus, RESERVATION_STATUS_LABEL, RESERVATION_STATUSES } from '../../utils/format.ts'
import { readNotice } from '../../utils/navigation.ts'
import { canCancel, formatPeriod } from '../../utils/reservation.ts'

/** 서버는 status 를 안 주면 전체를 돌려주므로 "전체"는 별도 값으로 구분한다 */
type StatusTab = ReservationStatus | 'ALL'

const TABS: readonly { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '전체' },
  ...RESERVATION_STATUSES.map((status) => ({ value: status, label: RESERVATION_STATUS_LABEL[status] })),
]

const EMPTY_MESSAGE: Record<StatusTab, string> = {
  ALL: '예약 내역이 없습니다.',
  PENDING: '승인 대기 중인 예약이 없습니다.',
  APPROVED: '승인된 예약이 없습니다.',
  REJECTED: '거절된 예약이 없습니다.',
  CANCELLED: '취소된 예약이 없습니다.',
}

/** ?status= → 탭. 없거나 잘못된 값이면 전체 */
function parseTab(params: URLSearchParams): StatusTab {
  const status = params.get('status')
  return isReservationStatus(status) ? status : 'ALL'
}

function listPath(tab: StatusTab): string {
  return tab === 'ALL' ? '/me/reservations' : `/me/reservations?status=${tab}`
}

export default function MyReservationsPage() {
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const tab = parseTab(searchParams)
  const { data: reservations, isPending, isError, error } = useMyReservations(tab === 'ALL' ? null : tab)
  const cancel = useCancelReservation()
  const today = todayInSeoul()

  const notice = readNotice(location.state)

  function handleCancel(id: number, gameName: string, period: string) {
    if (cancel.isPending || !window.confirm(`"${gameName}" ${period} 예약을 취소할까요? 취소하면 이 기간의 재고 점유가 풀립니다.`)) {
      return
    }
    cancel.reset()
    cancel.mutate(id)
  }

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-2xl font-bold">내 예약</h1>
        <Link
          to="/boardgames"
          className="rounded bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
        >
          게임 둘러보기
        </Link>
      </div>

      {notice && (
        <p role="status" className="rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {notice}
        </p>
      )}

      <StatusFilterTabs
        label="상태 필터"
        items={TABS.map((item) => ({
          key: item.value,
          label: item.label,
          to: listPath(item.value),
          current: item.value === tab,
        }))}
      />

      {cancel.isError && <ErrorMessage message={cancel.error.message} />}

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
                canCancel(reservation, today) && (
                  <button
                    type="button"
                    onClick={() =>
                      handleCancel(
                        reservation.id,
                        reservation.boardGameName,
                        formatPeriod(reservation.startDate, reservation.endDate),
                      )
                    }
                    disabled={cancel.isPending}
                    className="rounded border border-red-300 bg-white px-3 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
                  >
                    {cancel.isPending && cancel.variables === reservation.id ? '취소 중…' : '예약 취소'}
                  </button>
                )
              }
            />
          ))}
        </ul>
      )}
    </section>
  )
}
