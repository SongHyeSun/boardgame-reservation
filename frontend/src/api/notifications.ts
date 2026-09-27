import type { NotificationPage, UnreadCountResponse } from '../types/notification.ts'
import { request } from './client.ts'

/** 최신순 20개(드롭다운용 고정 페이지). content 외 필드(totalElements 등)는 쓰지 않는다 */
export function getNotifications(): Promise<NotificationPage> {
  return request<NotificationPage>({ method: 'GET', url: '/notifications', params: { page: 0, size: 20 } })
}

export function getUnreadCount(): Promise<UnreadCountResponse> {
  return request<UnreadCountResponse>({ method: 'GET', url: '/notifications/unread-count' })
}

/** 본인 것만(아니면 404) */
export function markNotificationRead(id: number): Promise<void> {
  return request<void>({ method: 'PATCH', url: `/notifications/${id}/read` })
}

export function markAllNotificationsRead(): Promise<void> {
  return request<void>({ method: 'PATCH', url: '/notifications/read-all' })
}
