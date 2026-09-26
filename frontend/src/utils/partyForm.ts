// 파티 개설 폼의 게임 종류별 규칙 (party/domain PartyPolicy, boardgame-plan.md 5장). 최종 판정은 서버.

import type { PlayMode } from '../types/boardgame.ts'
import type { PartyGameChoice } from '../types/party.ts'
import { availablePlayModes } from './format.ts'

/** 기타 게임 파티의 정원 범위(호스트 포함)와 게임 이름 길이. PartyPolicy / PartyCreateRequest 와 같은 값 */
export const CUSTOM_GAME_MIN_CAPACITY = 2
export const CUSTOM_GAME_MAX_CAPACITY = 20
export const CUSTOM_GAME_NAME_MAX = 50

export interface CapacityRange {
  min: number
  max: number
}

/** 정원(호스트 포함) 허용 범위. 보드게임 = 게임의 min~max, 기타 게임 = 2~20, 게임을 안 골랐으면 null */
export function capacityRange(game: PartyGameChoice): CapacityRange | null {
  switch (game.kind) {
    case 'BOARDGAME':
      return { min: game.boardGame.minPlayers, max: game.boardGame.maxPlayers }
    case 'CUSTOM':
      return { min: CUSTOM_GAME_MIN_CAPACITY, max: CUSTOM_GAME_MAX_CAPACITY }
    case 'NONE':
      return null
  }
}

/** 고를 수 있는 진행 방식. 보드게임은 게임이 지원하는 방식만, 기타 게임·미선택은 제한 없음(null) */
export function supportedPlayModes(game: PartyGameChoice): PlayMode[] | null {
  return game.kind === 'BOARDGAME' ? availablePlayModes(game.boardGame) : null
}

/**
 * 게임이 바뀐 뒤의 진행 방식: 한 방식만 지원하면 그 방식으로 자동 선택,
 * 지원하지 않는 방식이 골라져 있었다면 초기화, 그 외에는 현재 값을 유지한다.
 */
export function playModeAfterGameChange(current: PlayMode | '', game: PartyGameChoice): PlayMode | '' {
  const supported = supportedPlayModes(game)
  if (supported === null) {
    return current
  }
  if (supported.length === 1) {
    return supported[0]
  }
  return current !== '' && supported.includes(current) ? current : ''
}
