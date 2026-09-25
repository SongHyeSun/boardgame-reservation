// member/dto 기준

export type Role = 'USER' | 'ADMIN'

export interface MemberResponse {
  id: number
  email: string
  nickname: string
  role: Role
  createdAt: string
}

export interface SignupRequest {
  email: string // 필수, 이메일 형식, 최대 100자
  password: string // 필수, 8~64자
  nickname: string // 필수, 2~20자
}

export interface LoginRequest {
  email: string
  password: string
}
