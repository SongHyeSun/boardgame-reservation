import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getChatUsage, postChatRecommend } from '../api/chat.ts'
import type { ChatRequest, ChatResponse, ChatUsageResponse } from '../types/chat.ts'

export const chatUsageKey = ['chat-usage'] as const

export function useChatUsage() {
  return useQuery({
    queryKey: chatUsageKey,
    queryFn: getChatUsage,
  })
}

/**
 * 429 두 종류(CHAT_LIMIT_EXCEEDED/CHAT_BUSY)를 message 로 구분하는 화면 표시 로직은 여기 두지 않는다
 * (utils/chat.ts 의 isRetryable, 호출부인 ChatPage 참고). 이 훅은 usage 캐시 정합성만 챙긴다.
 */
export function useChatRecommend() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: ChatRequest) => postChatRecommend(body),
    onSuccess: (data: ChatResponse) => {
      queryClient.setQueryData<ChatUsageResponse>(chatUsageKey, (prev) =>
        prev ? { ...prev, remaining: data.remainingToday, used: prev.limit - data.remainingToday } : prev,
      )
      return queryClient.invalidateQueries({ queryKey: chatUsageKey })
    },
    onError: () => queryClient.invalidateQueries({ queryKey: chatUsageKey }),
  })
}
