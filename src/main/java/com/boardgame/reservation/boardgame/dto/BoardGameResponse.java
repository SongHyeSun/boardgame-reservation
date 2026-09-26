package com.boardgame.reservation.boardgame.dto;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.member.domain.Member;

import java.time.LocalDateTime;

/** 이미지는 파일 key 가 아니라 imageUrl(/api/files/...) 로 내려준다. owner 는 등록 관리자가 없는(레거시) 게임이면 null */
public record BoardGameResponse(
        Long id,
        String name,
        int minPlayers,
        int maxPlayers,
        int playTime,
        Difficulty difficulty,
        String description,
        String imageUrl,
        String youtubeVideoId,
        boolean offlineAvailable,
        boolean onlineAvailable,
        int stock,
        boolean visible,
        Owner owner,
        LocalDateTime createdAt
) {
    public record Owner(Long id, String nickname) {
        static Owner from(Member member) {
            return member == null ? null : new Owner(member.getId(), member.getNickname());
        }
    }

    /** owner 를 읽으므로 트랜잭션 안에서(또는 createdBy 를 fetch 한 뒤) 호출한다 */
    public static BoardGameResponse from(BoardGame boardGame) {
        return new BoardGameResponse(
                boardGame.getId(),
                boardGame.getName(),
                boardGame.getMinPlayers(),
                boardGame.getMaxPlayers(),
                boardGame.getPlayTime(),
                boardGame.getDifficulty(),
                boardGame.getDescription(),
                FileKeys.toUrl(boardGame.getImageKey()),
                boardGame.getYoutubeVideoId(),
                boardGame.isOfflineAvailable(),
                boardGame.isOnlineAvailable(),
                boardGame.getStock(),
                boardGame.isVisible(),
                Owner.from(boardGame.getCreatedBy()),
                boardGame.getCreatedAt()
        );
    }
}
