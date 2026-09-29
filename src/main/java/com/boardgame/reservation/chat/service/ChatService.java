package com.boardgame.reservation.chat.service;

import com.boardgame.reservation.chat.dto.ChatHistoryItem;
import com.boardgame.reservation.chat.dto.ChatRecommendationResponse;
import com.boardgame.reservation.chat.dto.ChatRequest;
import com.boardgame.reservation.chat.dto.ChatResponse;
import com.boardgame.reservation.chat.dto.ChatUsageResponse;
import com.boardgame.reservation.chat.llm.ChatMessage;
import com.boardgame.reservation.chat.llm.ChatPrompt;
import com.boardgame.reservation.chat.llm.LlmClient;
import com.boardgame.reservation.chat.llm.LlmResult;
import com.boardgame.reservation.chat.prompt.ChatSystemPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 챗봇 추천 오케스트레이션. 일부러 @Transactional을 붙이지 않는다 — LLM 응답을 기다리는 동안
 * DB 커넥션을 점유하지 않기 위해서다. DB 조회는 GameSearchTool/RecommendationValidator 안에서 각각 짧게 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmClient llmClient;
    private final GameSearchTool gameSearchTool;
    private final ChatRateLimiter rateLimiter;
    private final RecommendationValidator recommendationValidator;

    public ChatResponse recommend(Long memberId, ChatRequest request) {
        ChatRateLimiter.Increment increment = rateLimiter.checkAndIncrement(memberId);

        ChatPrompt prompt = new ChatPrompt(ChatSystemPrompt.TEXT, toChatMessages(request), request.message());
        long startNanos = System.nanoTime();
        LlmResult result;
        try {
            result = llmClient.generate(prompt, gameSearchTool);
        } catch (RuntimeException e) {
            // BusinessException뿐 아니라 예상 못 한 예외까지 전부 — 사용자 탓이 아닌 실패는 횟수에서 빼지 않는다.
            rateLimiter.decrement(increment.key());
            log.warn("chat recommend failed: memberId={}, error={}", memberId, e.toString());
            throw e;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // 메시지·답변 원문은 남기지 않는다 — memberId·소요시간·도구 호출 횟수·토큰 사용량만
        log.info("chat recommend: memberId={}, elapsedMs={}, toolCalls={}, tokenUsage={}",
                memberId, elapsedMs, result.toolCallCount(), result.tokenUsage());

        List<ChatRecommendationResponse> recommendations =
                recommendationValidator.validate(result.recommendations(), result.toolReturnedGameIds());
        return new ChatResponse(result.answerText(), recommendations, increment.remaining());
    }

    public ChatUsageResponse usage(Long memberId) {
        return rateLimiter.usage(memberId);
    }

    private static List<ChatMessage> toChatMessages(ChatRequest request) {
        return request.history().stream()
                .map(ChatService::toChatMessage)
                .toList();
    }

    private static ChatMessage toChatMessage(ChatHistoryItem item) {
        return new ChatMessage(item.role(), item.content());
    }
}
