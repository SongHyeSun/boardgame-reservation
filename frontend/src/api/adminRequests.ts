import type { AdminRequestResponse } from '../types/auth.ts'
import { request } from './client.ts'

// 전부 SUPER_ADMIN 전용 (ADMIN 은 403)

/** 승인 대기(PENDING) 목록, 오래된 신청부터 */
export function getAdminRequests(): Promise<AdminRequestResponse[]> {
  return request<AdminRequestResponse[]>({ method: 'GET', url: '/admin/admin-requests' })
}

/** role → ADMIN. 그 회원의 기존 로그인 세션은 모두 무효화된다. PENDING 이 아니면 409 */
export async function approveAdminRequest(memberId: number): Promise<void> {
  await request<null>({ method: 'PATCH', url: `/admin/admin-requests/${memberId}/approve` })
}

/** 거절(REJECTED). 세션은 유지되고 그 회원은 재신청할 수 있다. PENDING 이 아니면 409 */
export async function rejectAdminRequest(memberId: number): Promise<void> {
  await request<null>({ method: 'PATCH', url: `/admin/admin-requests/${memberId}/reject` })
}
