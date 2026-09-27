// 날짜(LocalDate) 유틸. API 경계는 `yyyy-MM-dd` 문자열, 달력 라이브러리에 넘기는 값만 로컬 자정 Date 다.
// toISOString() 은 UTC 로 바꿔 KST 자정이 전날로 밀리고, new Date('2026-10-01') 은 UTC 로 파싱되므로 둘 다 쓰지 않는다.
// ISO 날짜 문자열은 같은 형식끼리 사전순 비교가 날짜 비교와 같다.

const SEOUL_TIME_ZONE = 'Asia/Seoul'
const MS_PER_DAY = 86_400_000

const seoulDateFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: SEOUL_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function toIso(year: number, month: number, day: number): string {
  return `${String(year).padStart(4, '0')}-${pad2(month)}-${pad2(day)}`
}

/** `yyyy-MM-dd` → [연, 월(1~12), 일]. 형식이 다르면 null */
function parseParts(iso: string): [number, number, number] | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  return match === null ? null : [Number(match[1]), Number(match[2]), Number(match[3])]
}

/** 로컬 날짜 → `yyyy-MM-dd` (달력이 주는 로컬 자정 Date 용) */
export function toIsoDate(date: Date): string {
  return toIso(date.getFullYear(), date.getMonth() + 1, date.getDate())
}

/** `yyyy-MM-dd` → 로컬 자정 Date. 서버가 준 값만 넣으므로 형식 오류는 Invalid Date 로 둔다 */
export function parseIsoDate(iso: string): Date {
  const parts = parseParts(iso)
  return parts === null ? new Date(Number.NaN) : new Date(parts[0], parts[1] - 1, parts[2])
}

/** 서버(Asia/Seoul)와 같은 기준의 오늘. 브라우저 시간대와 무관하다 */
export function todayInSeoul(now: Date = new Date()): string {
  const parts = seoulDateFormat.formatToParts(now)
  const pick = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
  return `${pick('year')}-${pick('month')}-${pick('day')}`
}

/** 달력 날짜 기준으로 n일 뒤(음수면 앞). 시간대·서머타임과 무관하게 연·월·일만 계산한다 */
export function addDays(iso: string, days: number): string {
  const parts = parseParts(iso)
  if (parts === null) {
    return iso
  }
  const moved = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + days))
  return toIso(moved.getUTCFullYear(), moved.getUTCMonth() + 1, moved.getUTCDate())
}

/** 양끝을 포함한 일수 (당일 = 1). 서버 ReservationPolicy.durationDays 와 같다 */
export function durationDays(startIso: string, endIso: string): number {
  const start = parseParts(startIso)
  const end = parseParts(endIso)
  if (start === null || end === null) {
    return Number.NaN
  }
  const diff = Date.UTC(end[0], end[1] - 1, end[2]) - Date.UTC(start[0], start[1] - 1, start[2])
  return Math.round(diff / MS_PER_DAY) + 1
}

/** start ~ end 의 모든 날짜(양끝 포함). start > end 이면 빈 배열 */
export function eachDay(startIso: string, endIso: string): string[] {
  const count = durationDays(startIso, endIso)
  if (!(count >= 1)) {
    return []
  }
  return Array.from({ length: count }, (_, index) => addDays(startIso, index))
}

/** `2026-10-05` → `2026. 10. 5.` */
export function formatIsoDate(iso: string): string {
  const parts = parseParts(iso)
  return parts === null ? iso : `${parts[0]}. ${parts[1]}. ${parts[2]}.`
}
