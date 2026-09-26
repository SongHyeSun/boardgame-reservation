package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 목록 필터(status, boardGameId, playMode 모두 선택). boardGame/host 는 fetch join 으로 N+1 을 막는다.
 * 기타 게임 파티는 boardGame 이 null 이라 boardGame 은 left join 이어야 목록에 나온다 (boardGameId 필터를 주면 자연히 제외).
 */
public final class PartySpecification {

    private PartySpecification() {
    }

    public static Specification<Party> search(PartyStatus status, Long boardGameId, PlayMode playMode) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("boardGame", JoinType.LEFT);
                root.fetch("host");
            }

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (boardGameId != null) {
                predicates.add(cb.equal(root.get("boardGame").get("id"), boardGameId));
            }
            if (playMode != null) {
                predicates.add(cb.equal(root.get("playMode"), playMode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
