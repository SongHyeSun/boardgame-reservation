package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.party.domain.Party;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** 목록 조회의 동적 필터는 PartySpecification 으로 조합한다. */
public interface PartyRepository extends JpaRepository<Party, Long>, JpaSpecificationExecutor<Party> {

    @EntityGraph(attributePaths = {"boardGame", "host"})
    Optional<Party> findWithDetailsById(Long id);

    boolean existsByBoardGameId(Long boardGameId);
}
