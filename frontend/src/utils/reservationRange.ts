// 예약 기간 1차 검증. 규칙은 서버 ReservationPolicy(시작 오늘~+60일, 기간 최대 7일)와 같고, 최종 판정은 서버.
// react-day-picker 의 range max 는 "박 수"이고 넘으면 안내 없이 선택이 리셋되므로 라이브러리 제약은 쓰지 않고 여기서 안내 문구를 만든다.

import { addDays, durationDays, eachDay } from './date.ts'

/** 달력에서 고를 수 있는 마지막 날: 오늘 + 60일 (시작·종료 모두. availability 조회 한도 62일 안에 들어온다) */
export const RESERVATION_WINDOW_DAYS = 60
/** 대여 기간 최대 (당일 = 1일) */
export const RESERVATION_MAX_DURATION_DAYS = 7

export const RANGE_TOO_LONG_MESSAGE = `최대 ${RESERVATION_MAX_DURATION_DAYS}일까지 선택할 수 있습니다.`
export const RANGE_SOLD_OUT_MESSAGE = '선택한 기간에 예약 마감된 날짜가 있어 선택할 수 없습니다.'
export const RANGE_OUT_OF_WINDOW_MESSAGE = `오늘부터 ${RESERVATION_WINDOW_DAYS}일 이내의 날짜만 선택할 수 있습니다.`

/** 달력에 보여 줄 마지막 날(`yyyy-MM-dd`) */
export function lastSelectableDate(today: string): string {
  return addDays(today, RESERVATION_WINDOW_DAYS)
}

/**
 * 선택한 기간(양끝 포함)이 신청 가능한지 본다. 문제가 없으면 null, 있으면 화면에 보여 줄 안내 문구.
 * @param soldOut 가용 수량이 0인 날짜(`yyyy-MM-dd`) 집합
 */
export function validateRange(start: string, end: string, soldOut: ReadonlySet<string>, today: string): string | null {
  if (end < start) {
    return '종료일이 시작일보다 빠를 수 없습니다.'
  }
  if (start < today || end > lastSelectableDate(today)) {
    return RANGE_OUT_OF_WINDOW_MESSAGE
  }
  // 기간을 먼저 막아 아래 반복이 최대 7번을 넘지 않게 한다
  if (durationDays(start, end) > RESERVATION_MAX_DURATION_DAYS) {
    return RANGE_TOO_LONG_MESSAGE
  }
  return eachDay(start, end).some((day) => soldOut.has(day)) ? RANGE_SOLD_OUT_MESSAGE : null
}
