import type { ChatRequest, ChatResponse, ChatUsageResponse } from '../types/chat.ts'
import { request } from './client.ts'

export function postChatRecommend(body: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>({ method: 'POST', url: '/chat/recommend', data: body })
}

export function getChatUsage(): Promise<ChatUsageResponse> {
  return request<ChatUsageResponse>({ method: 'GET', url: '/chat/usage' })
}
