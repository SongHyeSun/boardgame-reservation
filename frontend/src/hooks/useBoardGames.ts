import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  changeBoardGameVisibility,
  createBoardGame,
  getBoardGame,
  getBoardGames,
  updateBoardGame,
} from '../api/boardgames.ts'
import type { BoardGameFilter, BoardGameRequest } from '../types/boardgame.ts'
import { refetchMeAfterError } from './useMe.ts'

interface BoardGameSubmission {
  data: BoardGameRequest
  /** multipart 의 image 파트. null 이면 파트 자체를 보내지 않는다 */
  image: File | null
}

interface BoardGamesOptions {
  /** 필터가 바뀌어 다시 조회하는 동안 이전 결과를 계속 보여 준다. (검색어 입력 중 로딩 깜빡임 방지) */
  keepPreviousData?: boolean
}

export function useBoardGames(filter: BoardGameFilter = {}, options: BoardGamesOptions = {}) {
  return useQuery({
    queryKey: ['boardgames', filter],
    queryFn: () => getBoardGames(filter),
    placeholderData: options.keepPreviousData ? keepPreviousData : undefined,
  })
}

export function useBoardGame(id: number) {
  return useQuery({
    queryKey: ['boardgame', id],
    queryFn: () => getBoardGame(id),
  })
}

export function useCreateBoardGame() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ data, image }: BoardGameSubmission) => createBoardGame(data, image),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['boardgames'] }),
    onError: (error) => refetchMeAfterError(queryClient, error),
  })
}

export function useUpdateBoardGame(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ data, image }: BoardGameSubmission) => updateBoardGame(id, data, image),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['boardgames'] }),
        queryClient.invalidateQueries({ queryKey: ['boardgame', id] }),
      ]),
    onError: (error) => refetchMeAfterError(queryClient, error),
  })
}

/**
 * 숨기기는 이 게임의 모집 중 파티를 서버가 전부 취소하므로 파티 목록·상세도 다시 조회한다.
 * 응답이 변경된 게임이라 상세 캐시에 바로 넣는다. (재조회 전에도 버튼·배지가 새 상태를 따른다)
 * promise 를 return 하므로 재조회가 끝날 때까지 mutation 은 pending 이다.
 */
export function useChangeBoardGameVisibility(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (visible: boolean) => changeBoardGameVisibility(id, visible),
    onSuccess: (boardGame) => {
      queryClient.setQueryData(['boardgame', id], boardGame)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['boardgames'] }),
        queryClient.invalidateQueries({ queryKey: ['parties'] }),
        queryClient.invalidateQueries({ queryKey: ['party'] }),
      ])
    },
    onError: (error) => refetchMeAfterError(queryClient, error),
  })
}
