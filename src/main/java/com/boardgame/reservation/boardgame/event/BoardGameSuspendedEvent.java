package com.boardgame.reservation.boardgame.event;

import java.util.List;

/** 게임이 운영 중지(숨김)되어 모집 중이던 파티가 취소됐을 때 발행. 엔티티가 아니라 id 만 담는다. 리스너는 알림 단계(D)에서 추가. */
public record BoardGameSuspendedEvent(Long gameId, List<Long> cancelledPartyIds) {
}
