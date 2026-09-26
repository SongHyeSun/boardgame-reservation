import type { BoardGameFilter, BoardGameRequest, BoardGameResponse } from '../types/boardgame.ts'
import { request, requestMultipart } from './client.ts'

export function getBoardGames(filter: BoardGameFilter = {}): Promise<BoardGameResponse[]> {
  return request<BoardGameResponse[]>({ method: 'GET', url: '/boardgames', params: filter })
}

export function getBoardGame(id: number): Promise<BoardGameResponse> {
  return request<BoardGameResponse>({ method: 'GET', url: `/boardgames/${id}` })
}

/** ADMIN 전용. multipart: data(JSON) + image(선택) */
export function createBoardGame(body: BoardGameRequest, image: File | null): Promise<BoardGameResponse> {
  return requestMultipart<BoardGameResponse>('POST', '/boardgames', body, image)
}

/** 소유 관리자 전용, 전체 교체. image 를 생략하면 이미지 유지, body.removeImage 가 true 면 제거 */
export function updateBoardGame(id: number, body: BoardGameRequest, image: File | null): Promise<BoardGameResponse> {
  return requestMultipart<BoardGameResponse>('PUT', `/boardgames/${id}`, body, image)
}

/** 소유 관리자 전용. false = 숨기기(운영 중지, 모집 중 파티 전부 취소), true = 다시 보이기. 응답은 변경된 게임 */
export function changeBoardGameVisibility(id: number, visible: boolean): Promise<BoardGameResponse> {
  return request<BoardGameResponse>({ method: 'PATCH', url: `/boardgames/${id}/visibility`, data: { visible } })
}
