package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 목록 조회의 동적 필터는 PartySpecification 으로 조합한다. */
public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

    /** 기타 게임 파티는 boardGame 이 null 이므로 반드시 left join (inner 면 파티가 조회되지 않는다) */
    @Query("select p from Party p left join fetch p.boardGame join fetch p.host where p.id = :id")
    Optional<Party> findWithDetailsById(@Param("id") Long id);

    /** 게임 숨기기 때 취소할 파티를 찾는다 (id·status 만 쓰므로 join fetch 불필요) */
    List<Party> findByBoardGameIdAndStatus(Long boardGameId, PartyStatus status);

    /** 게임의 진행 방식을 끄기 전에, 그 방식으로 모집 중인 파티가 있는지 확인 */
    boolean existsByBoardGameIdAndPlayModeAndStatus(Long boardGameId, PlayMode playMode, PartyStatus status);
}
