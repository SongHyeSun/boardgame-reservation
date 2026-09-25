package com.boardgame.reservation.boardgame.dto;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;

import java.time.LocalDateTime;

public record BoardGameResponse(
        Long id,
        String name,
        int minPlayers,
        int maxPlayers,
        int playTime,
        Difficulty difficulty,
        String description,
        LocalDateTime createdAt
) {
    public static BoardGameResponse from(BoardGame boardGame) {
        return new BoardGameResponse(
                boardGame.getId(),
                boardGame.getName(),
                boardGame.getMinPlayers(),
                boardGame.getMaxPlayers(),
                boardGame.getPlayTime(),
                boardGame.getDifficulty(),
                boardGame.getDescription(),
                boardGame.getCreatedAt()
        );
    }
}
