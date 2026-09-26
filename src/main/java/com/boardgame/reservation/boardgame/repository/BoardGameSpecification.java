package com.boardgame.reservation.boardgame.repository;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 목록 조회 필터. players / difficulty / keyword / playMode 는 선택값이며 null(또는 빈 keyword)이면 해당 조건을 건너뛴다.
 * (JPQL 의 ":param IS NULL" 방식은 PostgreSQL 에서 null 타입 추론 오류가 날 수 있어 Criteria 로 조합)
 * 노출 범위는 ownerId 로 갈린다: 없으면 숨기지 않은(visible) 게임만, 있으면 그 관리자가 등록한 게임 전부(숨김 포함).
 */
public final class BoardGameSpecification {

    private static final char ESCAPE = '\\';

    private BoardGameSpecification() {
    }

    /**
     * @param players    이 인원이 플레이 가능한 게임만 (minPlayers <= players <= maxPlayers)
     * @param difficulty 난이도 일치
     * @param keyword    이름 부분 일치 (대소문자 무시)
     * @param playMode   그 진행 방식이 가능한 게임만 (ONLINE → onlineAvailable, OFFLINE → offlineAvailable)
     * @param ownerId    null 이면 visible 게임만, 있으면 그 회원이 등록한 게임(숨김 포함)
     */
    public static Specification<BoardGame> search(Integer players, Difficulty difficulty, String keyword,
                                                  PlayMode playMode, Long ownerId) {
        return (root, query, cb) -> {
            // owner(닉네임)를 응답에 쓰므로 N+1 을 막는다. 레거시 게임(createdBy null)도 나오도록 left join. count 쿼리에는 fetch 불가
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("createdBy", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (ownerId != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), ownerId));
            } else {
                predicates.add(cb.isTrue(root.get("visible")));
            }
            if (players != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPlayers"), players));
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPlayers"), players));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (playMode != null) {
                predicates.add(cb.isTrue(root.get(playMode == PlayMode.ONLINE ? "onlineAvailable" : "offlineAvailable")));
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
