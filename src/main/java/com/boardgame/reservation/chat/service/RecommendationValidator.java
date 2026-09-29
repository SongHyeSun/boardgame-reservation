package com.boardgame.reservation.chat.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.chat.dto.ChatRecommendationResponse;
import com.boardgame.reservation.chat.llm.LlmResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 환각 방지 검증(⭐ README 포인트). LLM이 준 추천 gameId 중
 * (1) 이번 요청에서 searchBoardGames가 실제로 반환한 id이고, (2) DB에 존재하고 visible=true인 것만 응답에 포함한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationValidator {

    private static final int MAX_RECOMMENDATIONS = 5;

    private final BoardGameRepository boardGameRepository;

    public List<ChatRecommendationResponse> validate(List<LlmResult.Recommendation> llmRecommendations,
                                                       Set<Long> toolReturnedGameIds) {
        Map<Long, String> candidates = new LinkedHashMap<>();
        for (LlmResult.Recommendation recommendation : llmRecommendations) {
            if (toolReturnedGameIds.contains(recommendation.gameId())) {
                candidates.putIfAbsent(recommendation.gameId(), recommendation.reason());
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, BoardGame> visibleGames = boardGameRepository.findAllById(candidates.keySet()).stream()
                .filter(BoardGame::isVisible)
                .collect(Collectors.toMap(BoardGame::getId, game -> game));

        List<ChatRecommendationResponse> result = new ArrayList<>();
        for (Map.Entry<Long, String> candidate : candidates.entrySet()) {
            BoardGame game = visibleGames.get(candidate.getKey());
            if (game != null && result.size() < MAX_RECOMMENDATIONS) {
                result.add(ChatRecommendationResponse.from(game, candidate.getValue()));
            }
        }

        int filtered = llmRecommendations.size() - result.size();
        if (filtered > 0) {
            log.warn("filtered {} hallucinated/hidden chat recommendations", filtered);
        }
        return result;
    }
}
