import type { MemberResponse } from '../types/auth.ts'
import type { PartyDetailResponse } from '../types/party.ts'

/**
 * 파티 상세에 보여 줄 버튼 (docs/frontend-plan.md 4장).
 * NONE = 버튼 없음(마감/취소된 파티는 비로그인 포함 상태 배지만), FULL = 비활성 "정원 마감".
 * remaining 은 조회 시점 값이라 UX 용도일 뿐, 최종 판정은 서버가 한다.
 */
export type PartyAction = 'NONE' | 'LOGIN' | 'CLOSE' | 'LEAVE' | 'JOIN' | 'FULL'

export function getPartyAction(party: PartyDetailResponse, me: MemberResponse | null): PartyAction {
  if (party.status !== 'RECRUITING') {
    return 'NONE'
  }
  if (me === null) {
    return 'LOGIN'
  }
  if (party.hostId === me.id) {
    return 'CLOSE'
  }
  if (party.members.some((member) => member.memberId === me.id)) {
    return 'LEAVE'
  }
  return party.remaining > 0 ? 'JOIN' : 'FULL'
}
