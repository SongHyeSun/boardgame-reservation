import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import {
  createBoardGame,
  getBoardGame,
  getBoardGames,
  removeBoardGame,
  updateBoardGame,
} from '../api/boardgames.ts'
import type { BoardGameFilter, BoardGameRequest } from '../types/boardgame.ts'

export function useBoardGames(filter: BoardGameFilter = {}) {
  return useQuery({
    queryKey: ['boardgames', filter],
    queryFn: () => getBoardGames(filter),
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
    mutationFn: (body: BoardGameRequest) => createBoardGame(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['boardgames'] }),
  })
}

export function useUpdateBoardGame(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: BoardGameRequest) => updateBoardGame(id, body),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['boardgames'] }),
        queryClient.invalidateQueries({ queryKey: ['boardgame', id] }),
      ]),
  })
}

/**
 * 삭제된 id 를 마운트된 상세가 다시 조회하면 404 가 나므로 ['boardgame', id] 는 invalidate 가 아니라 제거한다.
 * 이동을 먼저 한 뒤 캐시를 정리한다. (useLogout 과 같은 순서. 이동 안내는 목록이 state.notice 로 표시)
 * 409(파티가 있는 게임) 등 실패 시에는 이동하지 않고 상세 화면이 error 를 표시한다.
 */
export function useDeleteBoardGame(id: number, name: string) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: () => removeBoardGame(id),
    onSuccess: () => {
      navigate('/boardgames', { replace: true, state: { notice: `"${name}" 게임을 삭제했습니다.` } })
      queryClient.removeQueries({ queryKey: ['boardgame', id] })
      return queryClient.invalidateQueries({ queryKey: ['boardgames'] })
    },
  })
}
