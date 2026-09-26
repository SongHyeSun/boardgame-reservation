package com.boardgame.reservation.support;

import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.party.dto.PartyCreateRequest;

/** PartyCreateRequest 는 필드가 많아 테스트에서는 이 팩토리로 만든다 (기본: 설명·일시 없음) */
public final class PartyRequests {

    private PartyRequests() {
    }

    /** 등록된 보드게임 + 오프라인 */
    public static PartyCreateRequest boardGame(Long boardGameId, String title, int capacity) {
        return boardGame(boardGameId, title, capacity, PlayMode.OFFLINE);
    }

    public static PartyCreateRequest boardGame(Long boardGameId, String title, int capacity, PlayMode playMode) {
        return new PartyCreateRequest(boardGameId, null, title, null, capacity, null, playMode, null, null, null);
    }

    /** 기타 게임(직접 입력) + 오프라인 */
    public static PartyCreateRequest customGame(String gameName, String title, int capacity) {
        return customGame(gameName, title, capacity, PlayMode.OFFLINE);
    }

    public static PartyCreateRequest customGame(String gameName, String title, int capacity, PlayMode playMode) {
        return new PartyCreateRequest(null, gameName, title, null, capacity, null, playMode, null, null, null);
    }

    /** 온라인 파티(플랫폼·접속 링크 포함). boardGameId 와 customGameName 중 하나만 채운다 */
    public static PartyCreateRequest online(Long boardGameId, String customGameName, String title, int capacity,
                                            String platform, String link) {
        return new PartyCreateRequest(boardGameId, customGameName, title, null, capacity, null,
                PlayMode.ONLINE, platform, link, null);
    }
}
