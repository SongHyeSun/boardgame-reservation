/** 백엔드 공통 응답 (global/response/ApiResponse.java) */
export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
}

/** 실패 응답·네트워크 오류를 하나로 합친 에러. status 0 = 서버에 닿지 못함 */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}
