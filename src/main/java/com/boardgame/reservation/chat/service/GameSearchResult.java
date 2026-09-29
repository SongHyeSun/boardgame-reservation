package com.boardgame.reservation.chat.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;

/** GameSearchTool이 LLM에게 돌려주는 도구 결과 항목. description은 앞 200자만. */
public record GameSearchResult(Long id, String name, int minPlayers, int maxPlayers, int playTime,
                                Difficulty difficulty, boolean offlineAvailable, boolean onlineAvailable,
                                String description) {

    private static final int DESCRIPTION_MAX_LENGTH = 200;

    public static GameSearchResult from(BoardGame game) {
        return new GameSearchResult(game.getId(), game.getName(), game.getMinPlayers(), game.getMaxPlayers(),
                game.getPlayTime(), game.getDifficulty(), game.isOfflineAvailable(), game.isOnlineAvailable(),
                truncate(game.getDescription()));
    }

    private static String truncate(String description) {
        if (description == null) {
            return "";
        }
        return description.length() > DESCRIPTION_MAX_LENGTH
                ? description.substring(0, DESCRIPTION_MAX_LENGTH)
                : description;
    }
}
