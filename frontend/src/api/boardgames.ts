import type { BoardGameFilter, BoardGameRequest, BoardGameResponse } from '../types/boardgame.ts'
import { request } from './client.ts'

export function getBoardGames(filter: BoardGameFilter = {}): Promise<BoardGameResponse[]> {
  return request<BoardGameResponse[]>({ method: 'GET', url: '/boardgames', params: filter })
}

export function getBoardGame(id: number): Promise<BoardGameResponse> {
  return request<BoardGameResponse>({ method: 'GET', url: `/boardgames/${id}` })
}

/** ADMIN 전용 */
export function createBoardGame(body: BoardGameRequest): Promise<BoardGameResponse> {
  return request<BoardGameResponse>({ method: 'POST', url: '/boardgames', data: body })
}

/** ADMIN 전용, 전체 교체 */
export function updateBoardGame(id: number, body: BoardGameRequest): Promise<BoardGameResponse> {
  return request<BoardGameResponse>({ method: 'PUT', url: `/boardgames/${id}`, data: body })
}

/** ADMIN 전용 */
export async function removeBoardGame(id: number): Promise<void> {
  await request<null>({ method: 'DELETE', url: `/boardgames/${id}` })
}
