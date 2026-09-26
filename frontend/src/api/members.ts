import type { ChangePasswordRequest, MemberResponse, UpdateProfileRequest } from '../types/auth.ts'
import { request, requestMultipart } from './client.ts'

/** multipart: data(JSON) + image(선택). 응답은 수정된 내 정보 */
export function updateMe(body: UpdateProfileRequest, image: File | null): Promise<MemberResponse> {
  return requestMultipart<MemberResponse>('PUT', '/members/me', body, image)
}

/** 현재 비밀번호 불일치는 400. 성공해도 로그인 세션은 유지된다. */
export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  await request<null>({ method: 'PATCH', url: '/members/me/password', data: body })
}

/** 관리자 신청(재신청 포함). USER 이고 상태가 NONE/REJECTED 일 때만, 아니면 409 */
export function requestAdmin(): Promise<MemberResponse> {
  return request<MemberResponse>({ method: 'POST', url: '/members/me/admin-request' })
}
