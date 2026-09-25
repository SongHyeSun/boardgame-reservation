package com.boardgame.reservation.boardgame.repository;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 목록 조회 필터. 세 조건 모두 선택값이며 null(또는 빈 keyword)이면 해당 조건을 건너뛴다.
 * (JPQL 의 ":param IS NULL" 방식은 PostgreSQL 에서 null 타입 추론 오류가 날 수 있어 Criteria 로 조합)
 */
public final class BoardGameSpecification {

    private static final char ESCAPE = '\\';

    private BoardGameSpecification() {
    }

    /**
     * @param players    이 인원이 플레이 가능한 게임만 (minPlayers <= players <= maxPlayers)
     * @param difficulty 난이도 일치
     * @param keyword    이름 부분 일치 (대소문자 무시)
     */
    public static Specification<BoardGame> search(Integer players, Difficulty difficulty, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (players != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPlayers"), players));
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPlayers"), players));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, ESCAPE));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 사용자가 입력한 % _ 가 와일드카드로 동작하지 않도록 이스케이프 */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
