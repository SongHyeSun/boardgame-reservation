import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import ActionBar from '../../components/ActionBar.tsx'
import BackLink from '../../components/BackLink.tsx'
import Button from '../../components/Button.tsx'
import ErrorMessage from '../../components/ErrorMessage.tsx'
import GameImage from '../../components/GameImage.tsx'
import GameStatusBadge from '../../components/GameStatusBadge.tsx'
import Loading from '../../components/Loading.tsx'
import Modal from '../../components/Modal.tsx'
import ReservationCalendar, { type CalendarSelection } from '../../components/ReservationCalendar.tsx'
import { useBoardGame } from '../../hooks/useBoardGames.ts'
import { useAvailability, useCreateReservation } from '../../hooks/useReservations.ts'
import type { BoardGameResponse } from '../../types/boardgame.ts'
import { formatIsoDate, todayInSeoul } from '../../utils/date.ts'
import { formatPeriod } from '../../utils/reservation.ts'
import {
  lastSelectableDate,
  RESERVATION_MAX_DURATION_DAYS,
  RESERVATION_WINDOW_DAYS,
  validateRange,
} from '../../utils/reservationRange.ts'
import { parsePositiveInteger } from '../../utils/validation.ts'

const PRIMARY_BUTTON = 'rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50'
const SECONDARY_BUTTON =
  'rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50 disabled:opacity-50'

/** 서버 RESERVATION_NOT_SUPPORTED 와 같은 문구 */
const ONLINE_ONLY_MESSAGE = '온라인 전용 게임은 대여할 수 없습니다.'
const SUSPENDED_MESSAGE = '운영 중지된 게임은 대여할 수 없습니다.'
const SUBMITTED_NOTICE = '대여 신청이 접수되었습니다. 관리자 승인을 기다려 주세요.'

type Mode = 'SINGLE' | 'RANGE'

const MODES: readonly { value: Mode; label: string }[] = [
  { value: 'SINGLE', label: '당일' },
  { value: 'RANGE', label: '여러 날' },
]

interface BoardGameProps {
  boardGame: BoardGameResponse
}

function selectionSummary(selection: CalendarSelection | null): string {
  if (selection === null) {
    return '날짜를 선택해 주세요.'
  }
  if (selection.end === null) {
    return `시작일 ${formatIsoDate(selection.start)} — 종료일을 선택해 주세요.`
  }
  return `선택한 기간: ${formatPeriod(selection.start, selection.end)}`
}

interface ConfirmModalProps {
  gameName: string
  start: string
  end: string
  isPending: boolean
  onConfirm: () => void
  onClose: () => void
}

/** 신청 확인. form 바깥에 렌더하고 안의 버튼은 type="button" (Modal 규칙) */
function ConfirmModal({ gameName, start, end, isPending, onConfirm, onClose }: ConfirmModalProps) {
  return (
    <Modal title="대여 신청 확인" onClose={onClose}>
      <div className="space-y-3 p-4 text-sm">
        <p className="text-base font-semibold">{gameName}</p>
        <p>{formatPeriod(start, end)}</p>
        <p className="text-gray-600">
          신청하면 관리자 승인 전에도 이 기간의 재고가 점유됩니다. 시작일 전날까지는 내 예약에서 취소할 수 있습니다.
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} disabled={isPending} className={SECONDARY_BUTTON}>
            돌아가기
          </button>
          <button type="button" data-autofocus onClick={onConfirm} disabled={isPending} className={PRIMARY_BUTTON}>
            {isPending ? '신청 중…' : '신청하기'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/**
 * 달력 + 기간 선택 + 신청. 오늘 ~ 60일의 가용 수량을 한 번에 받아 마감일(0)을 비활성으로 만든다.
 * 선택 기간의 유효성은 저장하지 않고 매 렌더 계산한다 — 재조회로 마감일이 바뀌어도 신청 버튼이 그대로 따라온다.
 * 신청이 409 등으로 실패하면 서버 메시지를 보여 주고(훅이 달력을 다시 조회한다) 선택은 유지한다.
 */
function ReservationForm({ boardGame }: BoardGameProps) {
  const navigate = useNavigate()
  const today = todayInSeoul()
  const { data, isPending, isError, error } = useAvailability(boardGame.id, today, lastSelectableDate(today))
  const create = useCreateReservation()

  const [mode, setMode] = useState<Mode>('SINGLE')
  const [selection, setSelection] = useState<CalendarSelection | null>(null)
  // 규칙을 어겨 받지 않은 선택에 대한 안내 (다음 선택에서 지운다)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  const availability = useMemo(() => (data ? new Map(data.map((item) => [item.date, item.available])) : null), [data])
  const soldOut = useMemo(
    () => new Set(data ? data.filter((item) => item.available === 0).map((item) => item.date) : []),
    [data],
  )

  const period = selection !== null && selection.end !== null ? { start: selection.start, end: selection.end } : null
  const periodError = period === null ? null : validateRange(period.start, period.end, soldOut, today)
  const canSubmit = period !== null && periodError === null && availability !== null && !create.isPending

  function handleModeChange(next: Mode) {
    if (next === mode) {
      return
    }
    setMode(next)
    setSelection(null)
    setNotice(null)
    create.reset()
  }

  function handleChange(next: CalendarSelection | null, clicked: string) {
    create.reset()
    if (next !== null && next.end !== null) {
      const message = validateRange(next.start, next.end, soldOut, today)
      if (message !== null) {
        // 규칙을 어긴 범위는 받지 않고, 방금 누른 날짜부터 다시 고르게 한다
        setNotice(message)
        setSelection(mode === 'RANGE' ? { start: clicked, end: null } : null)
        return
      }
    }
    setNotice(null)
    setSelection(next)
  }

  function handleConfirm() {
    if (period === null || create.isPending) {
      return
    }
    create.mutate(
      { boardGameId: boardGame.id, startDate: period.start, endDate: period.end },
      {
        onSuccess: () => navigate('/me/reservations', { state: { notice: SUBMITTED_NOTICE } }),
        onSettled: () => setConfirming(false),
      },
    )
  }

  const message = periodError ?? notice

  // lg: 왼쪽 달력 + 오른쪽 sticky 사이드 카드(선택한 기간 + 신청). 그 미만은 하단 고정 바
  return (
    <div className="lg:grid lg:grid-cols-[1fr_360px] lg:gap-8">
      <div className="space-y-4">
      <div role="group" aria-label="대여 방식" className="flex gap-2">
        {MODES.map((item) => (
          <button
            key={item.value}
            type="button"
            aria-pressed={item.value === mode}
            onClick={() => handleModeChange(item.value)}
            className={`rounded border px-3 py-1.5 text-sm ${
              item.value === mode
                ? 'border-indigo-600 bg-indigo-600 font-medium text-white'
                : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="rounded border border-gray-200 bg-white p-4">
        {isError && <ErrorMessage message={error.message} />}
        {isPending && <Loading />}
        {!isError && !isPending && (
          <ReservationCalendar
            mode={mode}
            today={today}
            availability={availability}
            showRemaining={boardGame.stock >= 2}
            selection={selection}
            onChange={handleChange}
          />
        )}
        <p className="mt-3 text-xs text-gray-500">
          오늘부터 {RESERVATION_WINDOW_DAYS}일 이내, 최대 {RESERVATION_MAX_DURATION_DAYS}일까지 선택할 수 있습니다. 예약
          마감된 날짜는 선택할 수 없고, 마감 날짜가 낀 기간은 신청할 수 없습니다.
          {boardGame.stock >= 2 && ' 날짜 아래 숫자는 남은 수량입니다.'}
        </p>
      </div>

      {(message !== null || create.isError) && (
        <div className="space-y-2">
          {message !== null && (
            <p role="alert" className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              {message}
            </p>
          )}
          {create.isError && <ErrorMessage message={create.error.message} />}
        </div>
      )}
      </div>

      <div>
        <ActionBar summary={<p className="text-small font-semibold lg:text-body">{selectionSummary(selection)}</p>}>
          <Button size="lg" onClick={() => setConfirming(true)} disabled={!canSubmit}>
            대여 신청
          </Button>
        </ActionBar>
      </div>

      {confirming && period !== null && (
        <ConfirmModal
          gameName={boardGame.name}
          start={period.start}
          end={period.end}
          isPending={create.isPending}
          onConfirm={handleConfirm}
          onClose={() => {
            if (!create.isPending) {
              setConfirming(false)
            }
          }}
        />
      )}
    </div>
  )
}

function ReserveView({ boardGame }: BoardGameProps) {
  const reservable = boardGame.visible && boardGame.offlineAvailable

  return (
    <section className="space-y-4">
      <div className="flex items-center gap-4 rounded border border-gray-200 bg-white p-4">
        <GameImage imageUrl={boardGame.imageUrl} name={boardGame.name} className="w-28 shrink-0" />
        <div className="min-w-0 space-y-1">
          <h1 className="text-xl font-bold">{boardGame.name} 대여 예약</h1>
          <div className="flex flex-wrap items-center gap-1.5 text-sm text-gray-600">
            <GameStatusBadge visible={boardGame.visible} />
            {boardGame.offlineAvailable && <span>재고 {boardGame.stock}개</span>}
          </div>
        </div>
      </div>

      {!boardGame.visible && <ErrorMessage message={SUSPENDED_MESSAGE} />}
      {boardGame.visible && !boardGame.offlineAvailable && <ErrorMessage message={ONLINE_ONLY_MESSAGE} />}
      {/* 온라인 전용·운영 중지 게임은 서버 availability 가 409 라 달력 자체를 그리지 않는다 */}
      {reservable && <ReservationForm boardGame={boardGame} />}
    </section>
  )
}

function ReserveGame({ id }: { id: number }) {
  const { data: boardGame, isPending, isError, error } = useBoardGame(id)

  if (isPending) {
    return <Loading />
  }
  if (isError) {
    return <ErrorMessage message={error.message} />
  }
  return <ReserveView boardGame={boardGame} />
}

export default function ReservePage() {
  const { id: rawId } = useParams()
  const id = parsePositiveInteger(rawId ?? '')

  if (id === null) {
    return (
      <div className="space-y-4">
        <ErrorMessage message="잘못된 게임 번호입니다." />
        <BackLink to="/boardgames">게임 목록</BackLink>
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <BackLink to={`/boardgames/${id}`}>게임 상세</BackLink>
      <ReserveGame id={id} />
    </div>
  )
}
