const DEFAULT_REDIRECT = '/parties'
const AUTH_PATHS = ['/login', '/signup']

/**
 * ?redirect= 값을 앱 내부 경로로 검증한다. 안전하지 않으면 /parties.
 * 외부 주소(`//host`, `/\host`), 공백·역슬래시 포함 값, 로그인/회원가입 자신(무한 이동)은 거른다.
 */
export function resolveRedirect(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || /[\s\\]/.test(raw)) {
    return DEFAULT_REDIRECT
  }
  const pathname = raw.split(/[?#]/, 1)[0].replace(/\/+$/, '').toLowerCase()
  if (AUTH_PATHS.includes(pathname)) {
    return DEFAULT_REDIRECT
  }
  return raw
}

/** 로그인 ↔ 회원가입 이동 시 유효한 redirect 를 이어 붙인다. */
export function pathWithRedirect(path: string, rawRedirect: string | null): string {
  if (rawRedirect && resolveRedirect(rawRedirect) === rawRedirect) {
    return `${path}?redirect=${encodeURIComponent(rawRedirect)}`
  }
  return path
}

/** navigate(..., { state: { notice } }) 로 넘어온 안내 문구 (AdminRoute 와 같은 관례) */
export function readNotice(state: unknown): string | null {
  if (typeof state === 'object' && state !== null && 'notice' in state && typeof state.notice === 'string') {
    return state.notice
  }
  return null
}
