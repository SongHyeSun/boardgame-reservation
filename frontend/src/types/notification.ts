// notification/dto 기준 (NotificationResponse, UnreadCountResponse)

export type NotificationType =
  | 'PARTY_JOINED'
  | 'PARTY_FULL'
  | 'PARTY_LEFT'
  | 'PARTY_KICKED'
  | 'PARTY_CLOSED'
  | 'GAME_SUSPENDED'
  | 'RESERVATION_REQUESTED'
  | 'RESERVATION_APPROVED'
  | 'RESERVATION_REJECTED'
  | 'RESERVATION_CANCELLED'
  | 'ADMIN_REQUESTED'
  | 'ADMIN_APPROVED'
  | 'ADMIN_REJECTED'

/** message 는 이미 완성된 한국어 문장이라 화면에서 타입별 라벨을 따로 만들지 않는다. link 는 프론트 경로(nullable) */
export interface NotificationResponse {
  id: number
  type: NotificationType
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

export interface UnreadCountResponse {
  count: number
}

/** 서버가 Spring Data Page 를 그대로 직렬화한다(PagedModel 미적용). 화면은 content 만 쓴다 */
export interface NotificationPage {
  content: NotificationResponse[]
}
