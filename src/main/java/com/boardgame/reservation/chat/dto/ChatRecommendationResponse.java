package com.boardgame.reservation.chat.dto;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.global.file.FileKeys;

/** 게임 상세 필드는 LLM이 준 값이 아니라 DB(BoardGame)에서 채운다. reason만 LLM이 준 값. */
public record ChatRecommendationResponse(Long gameId, String name, String imageUrl, int minPlayers, int maxPlayers,
                                          int playTime, Difficulty difficulty, boolean offlineAvailable,
                                          boolean onlineAvailable, String reason) {

    public static ChatRecommendationResponse from(BoardGame game, String reason) {
        return new ChatRecommendationResponse(
                game.getId(), game.getName(), FileKeys.toUrl(game.getImageKey()),
                game.getMinPlayers(), game.getMaxPlayers(), game.getPlayTime(), game.getDifficulty(),
                game.isOfflineAvailable(), game.isOnlineAvailable(), reason);
    }
}
