import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { getNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '../api/notifications.ts'
import { ApiError } from '../types/api.ts'

/** unreadCountKey 가 notificationsKey 의 하위 키라, invalidateQueries(notificationsKey) 한 번으로 둘 다 무효화된다 */
export const notificationsKey = ['notifications'] as const
export const unreadCountKey = ['notifications', 'unread'] as const

/** 드롭다운이 열렸을 때만 조회한다 */
export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: notificationsKey,
    queryFn: getNotifications,
    select: (page) => page.content,
    enabled,
  })
}

/** 배지 숫자용. 로그인 상태면 항상 마운트되는 헤더에서 호출한다 */
export function useUnreadCount() {
  return useQuery({
    queryKey: unreadCountKey,
    queryFn: getUnreadCount,
  })
}

/**
 * 이미 처리됐거나(404: 남의 알림·삭제된 알림) 실패해도 목록이 서버와 어긋난 것이므로
 * onError 에서도 같은 무효화를 한다.
 */
function refetchAfterError(queryClient: QueryClient, error: Error) {
  if (!(error instanceof ApiError)) {
    return undefined
  }
  return queryClient.invalidateQueries({ queryKey: notificationsKey })
}

export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationsKey }),
    onError: (error) => refetchAfterError(queryClient, error),
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationsKey }),
    onError: (error) => refetchAfterError(queryClient, error),
  })
}
