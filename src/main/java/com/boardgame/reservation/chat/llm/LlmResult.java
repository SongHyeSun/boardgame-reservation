package com.boardgame.reservation.chat.llm;

import java.util.List;
import java.util.Set;

/**
 * LlmClient.generate 의 결과. recommendations 는 LLM이 준 값 그대로(검증 전)이며,
 * 실제 응답에 포함될 게임 상세는 RecommendationValidator 가 DB 값으로 다시 채운다.
 * toolCallCount/tokenUsage(제공 안 되면 null)는 로깅(memberId·소요시간·도구 호출 횟수·토큰 사용량)에만 쓴다.
 */
public record LlmResult(String answerText, List<Recommendation> recommendations,
                         Set<Long> toolReturnedGameIds, int toolCallCount, Long tokenUsage) {

    public record Recommendation(Long gameId, String reason) {
    }
}
