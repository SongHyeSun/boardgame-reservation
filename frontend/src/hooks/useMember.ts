import { useMutation, useQueryClient } from '@tanstack/react-query'
import { changePassword, requestAdmin, updateMe } from '../api/members.ts'
import type { UpdateProfileRequest } from '../types/auth.ts'
import { meQueryKey, refetchMeAfterError } from './useMe.ts'

/**
 * 성공 응답이 /me 와 같은 형태라 me 캐시에 바로 넣는다.
 * 닉네임·아바타는 파티 응답(호스트·참여자)에도 들어 있으므로 파티 목록·상세도 다시 조회하게 한다.
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ data, image }: { data: UpdateProfileRequest; image: File | null }) => updateMe(data, image),
    onSuccess: (member) => {
      queryClient.setQueryData(meQueryKey, member)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['parties'] }),
        queryClient.invalidateQueries({ queryKey: ['party'] }),
      ])
    },
    onError: (error) => refetchMeAfterError(queryClient, error),
  })
}

/** 성공해도 세션은 유지된다(서버가 세션을 끊지 않음). 현재 비밀번호 불일치는 400 message 를 화면이 그대로 보여 준다. */
export function useChangePassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: changePassword,
    onError: (error) => refetchMeAfterError(queryClient, error),
  })
}

/** 409(이미 신청 중 등)는 화면의 me 가 서버와 어긋난 것이므로 me 를 다시 조회한다. */
export function useRequestAdmin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: requestAdmin,
    onSuccess: (member) => {
      queryClient.setQueryData(meQueryKey, member)
    },
    onError: (error) => refetchMeAfterError(queryClient, error, [409]),
  })
}
