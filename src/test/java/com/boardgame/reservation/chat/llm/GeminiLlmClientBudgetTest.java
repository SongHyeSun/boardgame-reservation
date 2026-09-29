package com.boardgame.reservation.chat.llm;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 전체 시간 예산(app.chat.total-budget-seconds): 호출은 "남은 시간 ≥ 호출 1회 최악 시간(2×timeout)"일 때만 시작한다.
 * 가짜 시계를 쓴다 — HTTP 호출마다 callDuration 만큼, 재시도 대기마다 실제 대기 시간만큼 시계를 앞으로 돌린다.
 * prod 값(timeout 20 → 호출 1회 최악 40초, 예산 100초)으로 어떤 경로든 총합이 예산을 넘지 않는지 확인한다.
 */
class GeminiLlmClientBudgetTest {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent";
    private static final String FUNCTION_CALL = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"functionCall":{"name":"searchBoardGames","args":{}}}
            ]}}]}
            """;
    private static final String FINAL_ANSWER = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"text":"{\\"answer\\":\\"ok\\",\\"recommendations\\":[]}"}
            ]}}]}
            """;

    private final AtomicLong clockNanos = new AtomicLong();
    private final AtomicInteger httpCalls = new AtomicInteger();
    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
    }

    /** timeout=20 → 호출 1회 최악 40초, 예산 100초 (prod 값). callDuration: HTTP 호출 1회가 가짜 시계로 걸리는 시간 */
    private GeminiLlmClient client(int timeoutSeconds, int budgetSeconds, Duration callDuration) {
        builder.requestInterceptor((request, body, execution) -> {
            httpCalls.incrementAndGet();
            var response = execution.execute(request, body);
            clockNanos.addAndGet(callDuration.toNanos());
            return response;
        });
        server = MockRestServiceServer.bindTo(builder).build();
        ChatProperties properties = new ChatProperties("gemini", 20, 10, timeoutSeconds, budgetSeconds,
                new ChatProperties.Gemini("test-key", "gemini-3.7-flash"));
        return new GeminiLlmClient(builder.build(), properties, new ObjectMapper(),
                clockNanos::get, millis -> clockNanos.addAndGet(Duration.ofMillis(millis).toNanos()));
    }

    private static ChatPrompt prompt() {
        return new ChatPrompt("system prompt", List.of(), "추천해줘");
    }

    @Test
    @DisplayName("시간이 충분하면 평소대로 동작한다")
    void enoughTime_worksNormally() {
        GeminiLlmClient client = client(20, 100, Duration.ofSeconds(5));
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(FUNCTION_CALL, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(FINAL_ANSWER, MediaType.APPLICATION_JSON));

        LlmResult result = client.generate(prompt(), (name, args) -> "[]");

        assertThat(result.answerText()).isEqualTo("ok");
        assertThat(httpCalls).hasValue(2);
        server.verify();
    }

    @Test
    @DisplayName("라운드가 진행돼 남은 시간이 호출 최악 시간(40초)보다 적으면 다음 호출을 시작하지 않고 CHAT_UNAVAILABLE")
    void notEnoughTimeForNextRound_doesNotCall() {
        GeminiLlmClient client = client(20, 100, Duration.ofSeconds(35)); // 라운드1 t=0→35, 라운드2 t=35→70, 라운드3은 남은 30 < 40
        server.expect(ExpectedCount.times(2), requestTo(ENDPOINT))
                .andRespond(withSuccess(FUNCTION_CALL, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(prompt(), (name, args) -> "[]"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_UNAVAILABLE));

        assertThat(httpCalls).hasValue(2);
        server.verify();
    }

    @Test
    @DisplayName("503 후 재시도 대기를 마치니 남은 시간이 부족하면 재시도하지 않고 CHAT_BUSY")
    void retryBlockedByBudget_throwsBusy() {
        GeminiLlmClient client = client(20, 100, Duration.ofSeconds(61)); // 실패 응답에 61초 + 대기 1초 → 남은 38 < 40
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.generate(prompt(), (name, args) -> "[]"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_BUSY));

        assertThat(httpCalls).hasValue(1);
        server.verify();
    }

    @Test
    @DisplayName("prod 값(20/100): 모든 호출이 최악 시간(40초)을 다 쓰는 functionCall 경로도 총합이 100초를 넘지 않는다")
    void prodValues_worstCaseFunctionCallPath_withinBudget() {
        GeminiLlmClient client = client(20, 100, Duration.ofSeconds(40));
        server.expect(ExpectedCount.manyTimes(), requestTo(ENDPOINT))
                .andRespond(withSuccess(FUNCTION_CALL, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(prompt(), (name, args) -> "[]"))
                .isInstanceOf(BusinessException.class);

        assertThat(Duration.ofNanos(clockNanos.get())).isLessThanOrEqualTo(Duration.ofSeconds(100));
        assertThat(httpCalls).hasValue(2); // t=0, t=40 에서 시작, t=80 에는 남은 20초라 시작 못 함
    }

    @Test
    @DisplayName("prod 값(20/100): 모든 호출이 503(최악 40초) + 재시도 경로여도 총합이 100초를 넘지 않는다")
    void prodValues_worstCaseRetryPath_withinBudget() {
        GeminiLlmClient client = client(20, 100, Duration.ofSeconds(40));
        server.expect(ExpectedCount.manyTimes(), requestTo(ENDPOINT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.generate(prompt(), (name, args) -> "[]"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_BUSY));

        // t=0 호출(→40) + 대기 1초(→41) + 재시도(남은 59 ≥ 40 → →81) 후 종료
        assertThat(Duration.ofNanos(clockNanos.get())).isLessThanOrEqualTo(Duration.ofSeconds(100));
        assertThat(httpCalls).hasValue(2);
    }

    @Test
    @DisplayName("총 예산이 호출 1회 최악 시간(2×timeout)보다 작은 설정은 기동 시점에 거부된다")
    void budgetSmallerThanWorstCaseCall_rejected() {
        assertThatThrownBy(() -> new ChatProperties("gemini", 20, 10, 30, 50,
                new ChatProperties.Gemini("k", "m")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("total-budget-seconds 를 안 주면(0) 100초가 기본값")
    void budgetDefaultsTo100() {
        ChatProperties properties = new ChatProperties("gemini", 20, 10, 30, 0, new ChatProperties.Gemini("k", "m"));

        assertThat(properties.totalBudgetSeconds()).isEqualTo(100);
    }
}
