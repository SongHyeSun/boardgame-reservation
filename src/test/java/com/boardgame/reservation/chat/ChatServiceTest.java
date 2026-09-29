package com.boardgame.reservation.chat;

import com.boardgame.reservation.chat.dto.ChatHistoryItem;
import com.boardgame.reservation.chat.dto.ChatRecommendationResponse;
import com.boardgame.reservation.chat.dto.ChatRequest;
import com.boardgame.reservation.chat.dto.ChatResponse;
import com.boardgame.reservation.chat.llm.ChatRole;
import com.boardgame.reservation.chat.llm.LlmClient;
import com.boardgame.reservation.chat.llm.LlmResult;
import com.boardgame.reservation.chat.service.ChatRateLimiter;
import com.boardgame.reservation.chat.service.ChatService;
import com.boardgame.reservation.chat.service.GameSearchTool;
import com.boardgame.reservation.chat.service.RecommendationValidator;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DB/스프링 없이 서비스 로직만 빠르게 검증하는 단위 테스트(BoardGameServiceTest 등과 같은 스타일). */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final String RATE_LIMIT_KEY = "chat:usage:1:20260928";

    @Mock
    LlmClient llmClient;
    @Mock
    GameSearchTool gameSearchTool;
    @Mock
    ChatRateLimiter rateLimiter;
    @Mock
    RecommendationValidator recommendationValidator;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(llmClient, gameSearchTool, rateLimiter, recommendationValidator);
    }

    private static ChatRequest request() {
        return new ChatRequest("3인용 추천해줘", List.of(new ChatHistoryItem(ChatRole.USER, "안녕")));
    }

    @Test
    @DisplayName("성공 시 validator 호출 결과·ChatResponse.remainingToday를 그대로 조립한다")
    void recommend_success() {
        ChatRateLimiter.Increment increment = new ChatRateLimiter.Increment(RATE_LIMIT_KEY, 19);
        given(rateLimiter.checkAndIncrement(MEMBER_ID)).willReturn(increment);
        LlmResult llmResult = new LlmResult("답변", List.of(new LlmResult.Recommendation(3L, "이유")), Set.of(3L), 1, 100L);
        given(llmClient.generate(any(), eq(gameSearchTool))).willReturn(llmResult);
        List<ChatRecommendationResponse> validated = List.of();
        given(recommendationValidator.validate(llmResult.recommendations(), llmResult.toolReturnedGameIds()))
                .willReturn(validated);

        ChatResponse response = chatService.recommend(MEMBER_ID, request());

        assertThat(response.answer()).isEqualTo("답변");
        assertThat(response.recommendations()).isSameAs(validated);
        assertThat(response.remainingToday()).isEqualTo(19);
        verify(rateLimiter, never()).decrement(anyString());
    }

    @Test
    @DisplayName("CHAT_UNAVAILABLE(키 없음·타임아웃 등을 흉내) → 전파 + 같은 key로 decrement")
    void recommend_chatUnavailable_decrementsAndPropagates() {
        given(rateLimiter.checkAndIncrement(MEMBER_ID)).willReturn(new ChatRateLimiter.Increment(RATE_LIMIT_KEY, 20));
        given(llmClient.generate(any(), eq(gameSearchTool))).willThrow(new BusinessException(ErrorCode.CHAT_UNAVAILABLE));

        assertThatThrownBy(() -> chatService.recommend(MEMBER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_UNAVAILABLE);
        verify(rateLimiter).decrement(RATE_LIMIT_KEY);
    }

    @Test
    @DisplayName("제공자 429(CHAT_BUSY) → 전파 + 같은 key로 decrement")
    void recommend_chatBusy_decrementsAndPropagates() {
        given(rateLimiter.checkAndIncrement(MEMBER_ID)).willReturn(new ChatRateLimiter.Increment(RATE_LIMIT_KEY, 20));
        given(llmClient.generate(any(), eq(gameSearchTool))).willThrow(new BusinessException(ErrorCode.CHAT_BUSY));

        assertThatThrownBy(() -> chatService.recommend(MEMBER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_BUSY);
        verify(rateLimiter).decrement(RATE_LIMIT_KEY);
    }

    @Test
    @DisplayName("예상 못 한 RuntimeException도 변환하지 않고 그대로 전파하되 decrement는 호출한다")
    void recommend_unexpectedException_propagatesAsIsAndDecrements() {
        given(rateLimiter.checkAndIncrement(MEMBER_ID)).willReturn(new ChatRateLimiter.Increment(RATE_LIMIT_KEY, 20));
        IllegalStateException unexpected = new IllegalStateException("boom");
        given(llmClient.generate(any(), eq(gameSearchTool))).willThrow(unexpected);

        assertThatThrownBy(() -> chatService.recommend(MEMBER_ID, request())).isSameAs(unexpected);
        verify(rateLimiter).decrement(RATE_LIMIT_KEY);
    }

    @Test
    @DisplayName("한도 초과(CHAT_LIMIT_EXCEEDED)는 llmClient를 호출하지 않고 decrement도 하지 않는다")
    void recommend_limitExceeded_neverCallsLlmOrDecrement() {
        given(rateLimiter.checkAndIncrement(MEMBER_ID)).willThrow(new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> chatService.recommend(MEMBER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_LIMIT_EXCEEDED);
        org.mockito.Mockito.verifyNoInteractions(llmClient, recommendationValidator);
        verify(rateLimiter, never()).decrement(anyString());
    }

    @Test
    @DisplayName("usage는 rateLimiter.usage를 그대로 위임한다")
    void usage_delegatesToRateLimiter() {
        chatService.usage(MEMBER_ID);

        verify(rateLimiter).usage(MEMBER_ID);
    }
}
