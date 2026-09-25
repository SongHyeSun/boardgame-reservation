package com.boardgame.reservation.boardgame.repository;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 목록 조회의 동적 필터는 BoardGameSpecification 으로 조합한다. */
public interface BoardGameRepository
        extends JpaRepository<BoardGame, Long>, JpaSpecificationExecutor<BoardGame> {
}
