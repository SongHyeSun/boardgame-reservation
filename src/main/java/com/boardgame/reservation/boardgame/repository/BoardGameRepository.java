package com.boardgame.reservation.boardgame.repository;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.member.domain.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 목록 조회의 동적 필터는 BoardGameSpecification 으로 조합한다. */
public interface BoardGameRepository
        extends JpaRepository<BoardGame, Long>, JpaSpecificationExecutor<BoardGame> {

    /** 응답의 owner(닉네임)까지 한 번에 읽는다. 등록 관리자가 없는(레거시) 게임도 조회되도록 left join */
    @EntityGraph(attributePaths = "createdBy")
    Optional<BoardGame> findWithOwnerById(Long id);

    /**
     * 등록 관리자가 없는 기존 게임의 소유자를 채운다 (기동 시 SUPER_ADMIN 으로). @return 채운 게임 수
     * 호출 시점에 BoardGame 엔티티가 영속성 컨텍스트에 없어 clearAutomatically 가 필요 없고,
     * 같은 트랜잭션에서 방금 승격한 회원의 미반영 변경을 em.clear() 로 버릴 가능성도 피한다.
     */
    @Modifying
    @Query("update BoardGame g set g.createdBy = :owner where g.createdBy is null")
    int assignOwnerIfMissing(@Param("owner") Member owner);
}
