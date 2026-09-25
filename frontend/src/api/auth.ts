import { ApiError } from '../types/api.ts'
import type { LoginRequest, MemberResponse, SignupRequest } from '../types/auth.ts'
import { request } from './client.ts'

export function signup(body: SignupRequest): Promise<MemberResponse> {
  return request<MemberResponse>({ method: 'POST', url: '/auth/signup', data: body })
}

export function login(body: LoginRequest): Promise<MemberResponse> {
  return request<MemberResponse>({ method: 'POST', url: '/auth/login', data: body })
}

/** 세션이 이미 만료(401)됐다면 로그아웃된 것과 같으므로 실패로 보지 않는다. */
export async function logout(): Promise<void> {
  try {
    await request<null>({ method: 'POST', url: '/auth/logout' })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return
    }
    throw error
  }
}

/** 로그인 상태 판단용. 비로그인(401)은 에러가 아니라 null 로 돌려준다. */
export async function getMe(): Promise<MemberResponse | null> {
  try {
    return await request<MemberResponse>({ method: 'GET', url: '/members/me' })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}
