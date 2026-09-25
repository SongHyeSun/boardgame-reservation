import axios, { type AxiosRequestConfig } from 'axios'
import { ApiError, type ApiResponse } from '../types/api.ts'

const CONNECTION_ERROR_MESSAGE = '서버에 연결할 수 없습니다'
const UNKNOWN_ERROR_MESSAGE = '요청 처리 중 오류가 발생했습니다'

const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

function isApiResponse(body: unknown): body is ApiResponse<unknown> {
  return typeof body === 'object' && body !== null && 'success' in body && 'message' in body
}

function toApiError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return new ApiError(0, error instanceof Error ? error.message : UNKNOWN_ERROR_MESSAGE)
  }
  // 응답 없음 = 네트워크 오류
  if (!error.response) {
    return new ApiError(0, CONNECTION_ERROR_MESSAGE)
  }
  const { status, data } = error.response
  if (isApiResponse(data) && data.message) {
    return new ApiError(status, data.message)
  }
  // 5xx인데 ApiResponse 형태가 아니면 Spring이 아니라 프록시/게이트웨이가 준 응답 (dev 서버에서 백엔드가 꺼진 경우)
  if (status >= 500) {
    return new ApiError(status, CONNECTION_ERROR_MESSAGE)
  }
  return new ApiError(status, UNKNOWN_ERROR_MESSAGE)
}

// 401은 여기서 강제 이동하지 않는다. (me 조회 401은 정상 흐름, 로그인 실패도 401)
// 보호 라우트/화면에서 처리한다.
http.interceptors.response.use(undefined, (error: unknown) => Promise.reject(toApiError(error)))

/** { success, data, message } 에서 data 만 꺼내 반환. 실패는 ApiError 로 throw */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}
