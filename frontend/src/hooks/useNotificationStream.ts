import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { getMe } from '../api/auth.ts'
import type { NotificationResponse } from '../types/notification.ts'
import { meQueryKey, useMe } from './useMe.ts'
import { notificationsKey } from './useNotifications.ts'
import { useToast } from './useToast.ts'

/**
 * notification.link(프론트 경로) → 그 화면이 쓰는 쿼리 키. 접두사 매치라 하위 키(상태별 등)까지 함께 무효화된다.
 * 알림·안읽은 수는 호출부에서 항상 무효화하므로 여기서는 link 로 짐작되는 추가 쿼리만 다룬다.
 */
function invalidateForLink(queryClient: QueryClient, link: string | null) {
  if (link === null) {
    return
  }
  const partyMatch = /^\/parties\/(\d+)/.exec(link)
  if (partyMatch) {
    const id = Number(partyMatch[1])
    queryClient.invalidateQueries({ queryKey: ['party', id] })
    queryClient.invalidateQueries({ queryKey: ['parties'] })
    return
  }
  if (link.startsWith('/me/reservations')) {
    queryClient.invalidateQueries({ queryKey: ['reservations', 'me'] })
    return
  }
  if (link.startsWith('/admin/reservations')) {
    queryClient.invalidateQueries({ queryKey: ['admin-reservations'] })
    return
  }
  if (link.startsWith('/admin/admin-requests')) {
    queryClient.invalidateQueries({ queryKey: ['admin-requests'] })
    return
  }
  if (link === '/me') {
    queryClient.invalidateQueries({ queryKey: meQueryKey })
  }
}

/**
 * 로그인 중이면 SSE 로 실시간 알림을 받는다. Layout 최상단(ToastProvider 안)에서 1회만 호출한다.
 * 의존성은 me 전체가 아니라 memberId 로 둔다 — 프로필 수정처럼 me 가 다른 참조로 refetch 되기만 해도
 * 매번 재연결하는 것을 막고, 로그인/로그아웃으로 실제 사용자가 바뀔 때만 연결을 다시 맺는다.
 */
export function useNotificationStream() {
  const { data: me } = useMe()
  const queryClient = useQueryClient()
  const { addToast } = useToast()
  const memberId = me?.id ?? null

  useEffect(() => {
    if (memberId === null) {
      return undefined
    }

    const eventSource = new EventSource('/api/notifications/stream')

    // 서버가 프록시 제한(120초)보다 먼저 연결을 닫고 브라우저가 자동 재연결하는 사이에 놓친 알림은 토스트로는 못 받으므로,
    // 연결이 (재)성립할 때마다 알림 목록·안읽은 수를 다시 조회해 보정한다 (최초 연결 시 1회 추가 조회는 무해)
    eventSource.onopen = () => {
      queryClient.invalidateQueries({ queryKey: notificationsKey })
    }

    eventSource.addEventListener('notification', (event) => {
      const notification: NotificationResponse = JSON.parse((event as MessageEvent<string>).data)
      addToast(notification)
      queryClient.invalidateQueries({ queryKey: notificationsKey })
      invalidateForLink(queryClient, notification.link)
    })

    // 브라우저가 자동 재연결하지만, 세션이 실제로 끊긴 경우엔 me 를 다시 조회해 무한 재연결을 막는다
    eventSource.onerror = () => {
      queryClient.fetchQuery({ queryKey: meQueryKey, queryFn: getMe }).then((freshMe) => {
        if (freshMe === null) {
          eventSource.close()
        }
      })
    }

    return () => eventSource.close()
  }, [memberId, queryClient, addToast])
}
