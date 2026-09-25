import type {
  JoinResponse,
  PartyCreateRequest,
  PartyDetailResponse,
  PartyFilter,
  PartyResponse,
} from '../types/party.ts'
import { request } from './client.ts'

export function getParties(filter: PartyFilter = {}): Promise<PartyResponse[]> {
  return request<PartyResponse[]>({ method: 'GET', url: '/parties', params: filter })
}

export function getParty(id: number): Promise<PartyDetailResponse> {
  return request<PartyDetailResponse>({ method: 'GET', url: `/parties/${id}` })
}

export function createParty(body: PartyCreateRequest): Promise<PartyResponse> {
  return request<PartyResponse>({ method: 'POST', url: '/parties', data: body })
}

/** 선착순 참여. 정원 마감·중복 참여는 409 */
export function joinParty(id: number): Promise<JoinResponse> {
  return request<JoinResponse>({ method: 'POST', url: `/parties/${id}/join` })
}

export async function leaveParty(id: number): Promise<void> {
  await request<null>({ method: 'DELETE', url: `/parties/${id}/leave` })
}

/** 호스트 전용 */
export async function closeParty(id: number): Promise<void> {
  await request<null>({ method: 'PATCH', url: `/parties/${id}/close` })
}
