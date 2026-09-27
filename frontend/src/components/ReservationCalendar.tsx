import { createContext, useContext, useMemo } from 'react'
import { DayButton, DayPicker, type DayButtonProps } from 'react-day-picker'
import { ko } from 'react-day-picker/locale'
import 'react-day-picker/style.css'
import { parseIsoDate, toIsoDate } from '../utils/date.ts'
import { lastSelectableDate } from '../utils/reservationRange.ts'
import './ReservationCalendar.css'

/** 달력에서 고른 기간(`yyyy-MM-dd`). end 가 null 이면 여러 날 모드에서 시작일만 고른 상태 */
export interface CalendarSelection {
  start: string
  end: string | null
}

interface ReservationCalendarProps {
  /** SINGLE = 당일(하루 선택), RANGE = 여러 날(시작일·종료일 선택) */
  mode: 'SINGLE' | 'RANGE'
  /** 서울 기준 오늘(`yyyy-MM-dd`). 달력은 오늘 ~ 오늘+60일만 선택할 수 있다 */
  today: string
  /** 날짜 → 남은 수량. 아직 못 받았으면 null 이라 모든 날짜가 비활성이다 */
  availability: ReadonlyMap<string, number> | null
  /** 재고 2 이상인 게임만 날짜별 남은 수량을 보여 준다 */
  showRemaining: boolean
  selection: CalendarSelection | null
  /**
   * 사용자가 날짜를 눌러 선택이 바뀔 때. 기간 규칙(7일·마감일 포함)은 검사하지 않은 값이라 호출부가 검증한다.
   * clicked = 방금 누른 날짜 (검증에 실패하면 그 날짜부터 다시 고르게 하는 데 쓴다)
   */
  onChange: (selection: CalendarSelection | null, clicked: string) => void
}

const SOLD_OUT_LABEL = '마감'

/** 날짜(`yyyy-MM-dd`) → 셀 아래 라벨. DayButton 은 컴포넌트 밖에 정의해야 해서(렌더 안에서 만들면 매번 remount) context 로 전달한다 */
const DayLabelContext = createContext<(isoDate: string) => string | null>(() => null)

/** 기본 DayButton(키보드 포커스 처리 포함)을 그대로 쓰고 날짜 아래에 라벨만 붙인다 */
function ReservationDayButton({ children, ...props }: DayButtonProps) {
  const getLabel = useContext(DayLabelContext)
  const label = getLabel(toIsoDate(props.day.date))

  return (
    <DayButton {...props}>
      <span className="flex flex-col items-center leading-tight">
        <span>{children}</span>
        {label !== null && (
          <span className={label === SOLD_OUT_LABEL ? 'text-[10px] font-medium text-red-600' : 'text-[10px] opacity-70'}>
            {label}
          </span>
        )}
      </span>
    </DayButton>
  )
}

const COMPONENTS = { DayButton: ReservationDayButton }

/**
 * 예약 달력 (react-day-picker). 가용 수량이 0(마감)이거나 오늘 이전·60일 이후인 날짜는 선택할 수 없다.
 * 범위 모드는 라이브러리의 max/excludeDisabled 를 쓰지 않는다 — 넘으면 안내 없이 선택이 리셋되므로 호출부가 검증하고 문구를 보여 준다.
 */
export default function ReservationCalendar({
  mode,
  today,
  availability,
  showRemaining,
  selection,
  onChange,
}: ReservationCalendarProps) {
  const todayDate = useMemo(() => parseIsoDate(today), [today])
  const lastDate = useMemo(() => parseIsoDate(lastSelectableDate(today)), [today])

  // 받은 목록에 1 이상으로 있는 날짜만 선택 가능. (목록 밖 = 오늘 이전·60일 이후·아직 로딩 중)
  const isDisabled = (date: Date) => (availability?.get(toIsoDate(date)) ?? 0) < 1

  const getLabel = useMemo(
    () => (isoDate: string) => {
      const available = availability?.get(isoDate)
      if (available === undefined) {
        return null
      }
      if (available === 0) {
        return SOLD_OUT_LABEL
      }
      return showRemaining ? `${available}개` : null
    },
    [availability, showRemaining],
  )

  const common = {
    locale: ko,
    className: 'reservation-calendar mx-auto w-fit',
    today: todayDate,
    startMonth: todayDate,
    endMonth: lastDate,
    components: COMPONENTS,
    disabled: isDisabled,
  }

  return (
    // 좁으면 가로 스크롤. (justify-center 로 가운데 정렬하면 넘칠 때 왼쪽이 잘려 mx-auto 로 맞춘다)
    <div className="overflow-x-auto">
      <DayLabelContext value={getLabel}>
        {mode === 'SINGLE' ? (
          <DayPicker
            {...common}
            mode="single"
            selected={selection === null ? undefined : parseIsoDate(selection.start)}
            onSelect={(date, clicked) => {
              onChange(date === undefined ? null : { start: toIsoDate(date), end: toIsoDate(date) }, toIsoDate(clicked))
            }}
          />
        ) : (
          <DayPicker
            {...common}
            mode="range"
            // 완성된 범위에서 다시 누르면 새 범위를 시작하고, 시작일만 고른 뒤 같은 날을 또 누르면 선택이 풀린다
            resetOnSelect
            min={1}
            selected={
              selection === null
                ? undefined
                : {
                    from: parseIsoDate(selection.start),
                    to: selection.end === null ? undefined : parseIsoDate(selection.end),
                  }
            }
            onSelect={(range, clicked) => {
              const next =
                range?.from === undefined
                  ? null
                  : { start: toIsoDate(range.from), end: range.to === undefined ? null : toIsoDate(range.to) }
              onChange(next, toIsoDate(clicked))
            }}
          />
        )}
      </DayLabelContext>
    </div>
  )
}
