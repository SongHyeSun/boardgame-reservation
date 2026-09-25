package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** 목록 필터(status, boardGameId 모두 선택). boardGame/host 는 fetch join 으로 N+1 을 막는다. */
public final class PartySpecification {

    private PartySpecification() {
    }

    public static Specification<Party> search(PartyStatus status, Long boardGameId) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("boardGame");
                root.fetch("host");
            }

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (boardGameId != null) {
                predicates.add(cb.equal(root.get("boardGame").get("id"), boardGameId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
