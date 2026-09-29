package com.boardgame.reservation.chat;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.chat.service.GameSearchResult;
import com.boardgame.reservation.chat.service.GameSearchTool;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** BoardGameSpecification 기반 필터·숨김 게임 제외·최대 20개를 실제 DB로 검증(RedisIntegrationTestSupport 공유 컨테이너 재사용). */
class GameSearchToolTest extends RedisIntegrationTestSupport {

    @Autowired
    GameSearchTool gameSearchTool;
    @Autowired
    ObjectMapper objectMapper;

    private List<GameSearchResult> search(String argumentsJson) {
        String json = gameSearchTool.execute("searchBoardGames", argumentsJson);
        return List.of(objectMapper.readValue(json, GameSearchResult[].class));
    }

    private BoardGame saveGame(Member owner, String name, int minPlayers, int maxPlayers, int playTime,
                                Difficulty difficulty, boolean online, boolean offline) {
        return boardGameRepository.save(BoardGame.create(
                new BoardGame.Details(name, minPlayers, maxPlayers, playTime, difficulty, "설명",
                        offline, online, offline ? 1 : 0),
                owner));
    }

    @Test
    @DisplayName("players 필터: 그 인원이 플레이 가능한 게임만")
    void filtersByPlayers() {
        Member owner = saveAdmin("owner");
        BoardGame catan = saveGame(owner, "카탄", 3, 4, 60, Difficulty.NORMAL, false, true);
        saveGame(owner, "아그리콜라", 5, 6, 90, Difficulty.HARD, false, true);

        List<GameSearchResult> results = search("{\"players\":3}");

        assertThat(results).extracting(GameSearchResult::id).containsExactly(catan.getId());
    }

    @Test
    @DisplayName("maxPlayTime 필터: 그 시간 이하인 게임만")
    void filtersByMaxPlayTime() {
        Member owner = saveAdmin("owner");
        BoardGame quick = saveGame(owner, "스플렌더", 2, 4, 30, Difficulty.EASY, false, true);
        saveGame(owner, "트와일라잇임페리움", 3, 6, 240, Difficulty.HARD, false, true);

        List<GameSearchResult> results = search("{\"maxPlayTime\":60}");

        assertThat(results).extracting(GameSearchResult::id).containsExactly(quick.getId());
    }

    @Test
    @DisplayName("difficulty·playMode 필터")
    void filtersByDifficultyAndPlayMode() {
        Member owner = saveAdmin("owner");
        BoardGame easyOnline = saveGame(owner, "우노", 2, 6, 20, Difficulty.EASY, true, false);
        saveGame(owner, "루트", 2, 4, 90, Difficulty.HARD, false, true);

        List<GameSearchResult> results = search("{\"difficulty\":\"EASY\",\"playMode\":\"ONLINE\"}");

        assertThat(results).extracting(GameSearchResult::id).containsExactly(easyOnline.getId());
    }

    @Test
    @DisplayName("숨긴(운영 중지) 게임은 제외된다")
    void excludesHiddenGames() {
        Member owner = saveAdmin("owner");
        BoardGame visible = saveGame(owner, "카탄", 3, 4, 60, Difficulty.NORMAL, false, true);
        BoardGame hidden = saveGame(owner, "숨김게임", 3, 4, 60, Difficulty.NORMAL, false, true);
        hidden.hide();
        boardGameRepository.save(hidden);

        List<GameSearchResult> results = search("{}");

        assertThat(results).extracting(GameSearchResult::id).containsExactly(visible.getId());
    }

    @Test
    @DisplayName("21개 이상 등록돼도 최대 20개만 반환한다")
    void limitsToTwenty() {
        Member owner = saveAdmin("owner");
        for (int i = 0; i < 25; i++) {
            saveGame(owner, "게임" + i, 2, 4, 60, Difficulty.NORMAL, false, true);
        }

        List<GameSearchResult> results = search("{}");

        assertThat(results).hasSize(20);
    }

    @Test
    @DisplayName("잘못된 difficulty 값은 예외 없이 무시된다")
    void ignoresInvalidEnumValue() {
        Member owner = saveAdmin("owner");
        BoardGame game = saveGame(owner, "카탄", 3, 4, 60, Difficulty.NORMAL, false, true);

        List<GameSearchResult> results = search("{\"difficulty\":\"NOT_A_DIFFICULTY\"}");

        assertThat(results).extracting(GameSearchResult::id).containsExactly(game.getId());
    }
}
