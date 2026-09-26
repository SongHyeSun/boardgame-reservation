// 유튜브 링크 → 영상 ID. 백엔드 boardgame/domain/YoutubeUrlParser.java 와 같은 규칙 (입력 즉시 미리보기용, 최종 판정은 서버).
//
// 허용 형식 (https:// 또는 http://)
//   youtube.com | www.youtube.com | m.youtube.com   /watch?v=ID (다른 쿼리가 섞여도 됨), /shorts/ID, /embed/ID
//   youtu.be/ID  (?si=..., ?t=... 무시)
// scheme 없이 위 4개 host 로 시작하는 입력은 앞에 https:// 를 붙여 해석한다. 그 밖의 scheme 없는 입력은 거부.

export type YoutubeParseResult =
  | { kind: 'empty' }
  | { kind: 'valid'; videoId: string }
  | { kind: 'invalid' }

export const YOUTUBE_URL_MAX = 200
export const YOUTUBE_URL_INVALID_MESSAGE = '올바른 유튜브 링크가 아닙니다.'

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/
const SCHEMELESS = /^((www\.|m\.)?youtube\.com|youtu\.be)\//i
const YOUTUBE_HOSTS: readonly string[] = ['youtube.com', 'www.youtube.com', 'm.youtube.com']
const SHORT_HOST = 'youtu.be'

const INVALID: YoutubeParseResult = { kind: 'invalid' }

function extractVideoId(url: URL): string | null {
  const host = url.hostname.toLowerCase()
  // 빈 세그먼트는 버린다 (트레일링 슬래시 허용)
  const segments = url.pathname.split('/').filter((segment) => segment !== '')

  if (host === SHORT_HOST) {
    return segments.length === 1 ? segments[0] : null
  }
  if (!YOUTUBE_HOSTS.includes(host)) {
    return null
  }
  if (segments.length === 1 && segments[0] === 'watch') {
    return url.searchParams.get('v')
  }
  if (segments.length === 2 && (segments[0] === 'shorts' || segments[0] === 'embed')) {
    return segments[1]
  }
  return null
}

export function parseYoutubeUrl(input: string): YoutubeParseResult {
  let candidate = input.trim()
  if (candidate === '') {
    return { kind: 'empty' }
  }
  if (!candidate.includes('://') && SCHEMELESS.test(candidate)) {
    candidate = `https://${candidate}`
  }

  let url: URL
  try {
    url = new URL(candidate)
  } catch {
    return INVALID
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    return INVALID
  }

  const videoId = extractVideoId(url)
  return videoId !== null && VIDEO_ID.test(videoId) ? { kind: 'valid', videoId } : INVALID
}

/** 수정 폼의 유튜브 입력 초기값. 서버는 영상 ID 만 저장하므로 대표 형식으로 복원한다. */
export function youtubeUrlFromVideoId(videoId: string | null): string {
  return videoId === null ? '' : `https://youtu.be/${videoId}`
}

/** 재생은 쿠키 없는 도메인의 embed 로 한다. videoId 는 위 정규식을 통과한 값(서버 저장값)만 넣는다. */
export function youtubeEmbedUrl(videoId: string): string {
  return `https://www.youtube-nocookie.com/embed/${videoId}`
}
