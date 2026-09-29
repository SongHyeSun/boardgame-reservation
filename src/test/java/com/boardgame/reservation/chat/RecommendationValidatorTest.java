package com.boardgame.reservation.chat;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.chat.dto.ChatRecommendationResponse;
import com.boardgame.reservation.chat.llm.LlmResult;
import com.boardgame.reservation.chat.service.RecommendationValidator;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 환각 방지 검증(⭐). RedisIntegrationTestSupport 공유 컨테이너 재사용(DB 접근이 필요해서 순수 단위 테스트로는 못 함). */
class RecommendationValidatorTest extends RedisIntegrationTestSupport {

    @Autowired
    RecommendationValidator validator;

    private BoardGame saveVisibleGame(String name) {
        return boardGameRepository.save(BoardGame.create(name, 2, 4, 60, Difficulty.NORMAL, "설명"));
    }

    @Test
    @DisplayName("도구가 반환하지 않은 id는 제외된다")
    void excludesIdsNotReturnedByTool() {
        BoardGame game = saveVisibleGame("카탄");
        List<LlmResult.Recommendation> recommendations = List.of(
                new LlmResult.Recommendation(game.getId(), "이유"),
                new LlmResult.Recommendation(9999L, "도구가 안 돌려준 id"));

        List<ChatRecommendationResponse> result = validator.validate(recommendations, Set.of(game.getId()));

        assertThat(result).extracting(ChatRecommendationResponse::gameId).containsExactly(game.getId());
    }

    @Test
    @DisplayName("숨긴(운영 중지) 게임은 제외된다")
    void excludesHiddenGames() {
        BoardGame game = saveVisibleGame("카탄");
        game.hide();
        boardGameRepository.save(game);

        List<ChatRecommendationResponse> result = validator.validate(
                List.of(new LlmResult.Recommendation(game.getId(), "이유")), Set.of(game.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("중복 gameId는 제거되고 먼저 나온 reason을 유지한다")
    void deduplicatesByGameId_keepingFirstReason() {
        BoardGame game = saveVisibleGame("카탄");
        List<LlmResult.Recommendation> recommendations = List.of(
                new LlmResult.Recommendation(game.getId(), "첫 번째 이유"),
                new LlmResult.Recommendation(game.getId(), "두 번째 이유"));

        List<ChatRecommendationResponse> result = validator.validate(recommendations, Set.of(game.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).isEqualTo("첫 번째 이유");
    }

    @Test
    @DisplayName("최대 5개까지만 반환한다")
    void limitsToFive() {
        List<LlmResult.Recommendation> recommendations = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            BoardGame game = saveVisibleGame("게임" + i);
            recommendations.add(new LlmResult.Recommendation(game.getId(), "이유" + i));
            ids.add(game.getId());
        }

        List<ChatRecommendationResponse> result = validator.validate(recommendations, ids);

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("빈 입력(상위 파싱 실패 상황을 흉내) → 빈 결과")
    void emptyInput_returnsEmpty() {
        List<ChatRecommendationResponse> result = validator.validate(List.of(), Set.of());

        assertThat(result).isEmpty();
    }
}
