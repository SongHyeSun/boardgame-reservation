// boardgame/dto 기준

export type Difficulty = 'EASY' | 'NORMAL' | 'HARD'

/** 진행 방식. 게임 목록 필터에 쓰고, B-2 에서 파티의 진행 방식으로도 재사용한다. */
export type PlayMode = 'ONLINE' | 'OFFLINE'

/** 등록 관리자. 레거시 게임은 owner 가 null */
export interface BoardGameOwner {
  id: number
  nickname: string
}

export interface BoardGameResponse {
  id: number
  name: string
  minPlayers: number
  maxPlayers: number
  playTime: number
  difficulty: Difficulty
  description: string | null
  imageUrl: string | null // `/api/files/...`
  youtubeVideoId: string | null // 11자 영상 ID (링크 원문은 서버가 저장하지 않는다)
  offlineAvailable: boolean
  onlineAvailable: boolean
  stock: number // 온라인 전용 게임은 0
  visible: boolean // false = 운영 중지
  owner: BoardGameOwner | null
  createdAt: string
}

/**
 * 등록(POST) / 수정(PUT) 공용 — multipart 의 data 파트 (image 는 별도 파트).
 * minPlayers <= maxPlayers, 진행 방식·재고 규칙, 유튜브 링크 형식은 서버가 최종 검증한다.
 */
export interface BoardGameRequest {
  name: string // 필수, 최대 100자
  minPlayers: number // 필수, 1 이상
  maxPlayers: number // 필수, 1 이상
  playTime: number // 필수, 1 이상 (분)
  difficulty: Difficulty
  description?: string | null // 최대 2000자
  offlineAvailable: boolean // 온라인·오프라인 중 최소 하나는 true
  onlineAvailable: boolean
  stock: number // 오프라인 가능이면 1 이상. 온라인 전용이면 0 을 보낸다
  /** 최대 200자. PUT 은 전체 교체라 생략/null 이면 기존 영상이 제거된다 */
  youtubeUrl?: string | null
  /** PUT 전용: true 면 현재 이미지 제거. 새 image 파트와 함께 보내면 400 */
  removeImage?: boolean
}

/** 등록/수정 폼의 입력 상태 (프론트 전용). 숫자 입력은 문자열로 들고 있다가 제출 시 변환한다. 이미지 파일·삭제 표시는 폼의 별도 state. */
export interface BoardGameFormValues {
  name: string
  minPlayers: string
  maxPlayers: string
  playTime: string
  difficulty: Difficulty
  description: string
  offlineAvailable: boolean
  onlineAvailable: boolean
  stock: string
  youtubeUrl: string
}

/** GET /api/boardgames 쿼리 파라미터 (전부 선택) */
export interface BoardGameFilter {
  players?: number
  difficulty?: Difficulty
  keyword?: string
  playMode?: PlayMode
  /** 내가 등록한 게임(숨김 포함). ADMIN 전용 — 비로그인 401, 일반 회원 403 */
  mine?: boolean
}
