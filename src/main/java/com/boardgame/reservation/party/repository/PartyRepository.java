package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** 목록 조회의 동적 필터는 PartySpecification 으로 조합한다. */
public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

    @EntityGraph(attributePaths = {"boardGame", "host"})
    Optional<Party> findWithDetailsById(Long id);

    /** 게임 숨기기 때 취소할 파티를 찾는다 (id·status 만 쓰므로 join fetch 불필요) */
    List<Party> findByBoardGameIdAndStatus(Long boardGameId, PartyStatus status);
}
