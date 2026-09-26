// member/dto 기준 (MemberResponse, AvatarResponse, SignupRequest, UpdateProfileRequest, ChangePasswordRequest, AdminRequestResponse)

export type Role = 'USER' | 'ADMIN' | 'SUPER_ADMIN'

export type AvatarType = 'EMOJI' | 'IMAGE'

export type AdminRequestStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'

/**
 * imageUrl 은 type 이 EMOJI 여도 저장된 이미지가 있으면 내려온다. 화면에 그릴 때는 type 으로 판단한다.
 * 이미지가 없으면 null. 값은 `/api/files/...` (로그인 불필요)
 */
export interface Avatar {
  type: AvatarType
  emoji: string
  imageUrl: string | null
}

/** login · signup · GET/PUT /members/me · POST /members/me/admin-request 응답 공통 */
export interface MemberResponse {
  id: number
  email: string
  nickname: string
  role: Role
  createdAt: string
  name: string | null // A단계 이전 가입자는 null
  birthDate: string | null // LocalDate `YYYY-MM-DD`
  affiliation: string | null
  job: string | null
  bio: string | null
  avatar: Avatar
  adminRequestStatus: AdminRequestStatus
}

/**
 * POST /auth/signup 의 data 파트 (image 는 별도 파트). 선택 필드는 없으면 null.
 * avatarEmoji 를 생략하면 서버 기본 이모지, image 가 있으면 서버가 아바타 타입을 IMAGE 로 처리한다.
 */
export interface SignupRequest {
  email: string // 필수, 이메일 형식, 최대 100자
  password: string // 필수, 8~64자
  nickname: string // 필수, 2~20자
  name: string // 필수, 2~20자
  birthDate?: string | null // 과거 날짜
  affiliation?: string | null // 최대 50자
  job?: string | null // 최대 50자
  bio?: string | null // 최대 100자
  avatarEmoji?: string | null // 허용 목록 안의 값 (utils/avatar.ts)
  requestAdmin?: boolean // true 면 USER 로 가입 + 관리자 신청(PENDING)
}

/**
 * PUT /members/me 의 data 파트 (image 는 별도 파트). 선택 필드는 보내지 않으면 지워진다(전체 교체).
 * - image 가 있으면 서버는 avatarType 과 무관하게 IMAGE 로 처리하고, removeImage 와 같이 오면 400
 * - avatarType=IMAGE 인데 새·기존 이미지가 모두 없으면 400, removeImage=true 와 같이 오면 400
 * - avatarEmoji 를 비우면 현재 이모지 유지
 */
export interface UpdateProfileRequest {
  nickname: string // 필수, 2~20자
  name: string // 필수, 2~20자
  birthDate?: string | null
  affiliation?: string | null
  job?: string | null
  bio?: string | null
  avatarType: AvatarType // 필수
  avatarEmoji?: string | null
  removeImage?: boolean
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string // 8~64자
}

export interface LoginRequest {
  email: string
  password: string
}

/** SUPER_ADMIN 이 보는 관리자 신청 목록 항목 (PENDING 만, 오래된 신청부터) */
export interface AdminRequestResponse {
  memberId: number
  email: string
  nickname: string
  name: string | null
  affiliation: string | null
  job: string | null
  requestedAt: string
}

/** 프로필 폼의 입력 상태 (프론트 전용). 값은 전부 문자열이고 제출 시 trim·null 변환한다. */
export interface ProfileFormValues {
  nickname: string
  name: string
  birthDate: string // `<input type="date">` 값 (`YYYY-MM-DD`), 미입력은 ''
  affiliation: string
  job: string
  bio: string
}

export interface SignupFormValues extends ProfileFormValues {
  email: string
  password: string
  requestAdmin: boolean
}

export interface PasswordFormValues {
  currentPassword: string
  newPassword: string
  newPasswordConfirm: string // 클라이언트 전용 (서버로 보내지 않음)
}
