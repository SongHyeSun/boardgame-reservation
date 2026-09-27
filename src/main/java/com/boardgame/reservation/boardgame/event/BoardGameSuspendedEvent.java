package com.boardgame.reservation.boardgame.event;

import java.util.List;

/**
 * 게임이 운영 중지(숨김)되어 모집 중이던 파티와 아직 끝나지 않은 활성 예약이 취소됐을 때 발행.
 * 엔티티가 아니라 id 만 담는다 (알림 대상: 취소된 파티 참여자·예약자). 리스너는 알림 단계(D)에서 추가.
 * 예약은 GAME_SUSPENDED 로 취소된 것만 담고, 이미 끝난 예약은 이력으로 남아 포함되지 않는다.
 */
public record BoardGameSuspendedEvent(Long gameId, List<Long> cancelledPartyIds, List<Long> cancelledReservationIds) {
}
