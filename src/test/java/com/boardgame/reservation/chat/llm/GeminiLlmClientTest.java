package com.boardgame.reservation.chat.llm;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/**
 * 실제 네트워크 없이 MockRestServiceServer로 요청/응답 JSON을 검증한다(docs/chatbot-plan.md 2-1a 형식 기준).
 * 실제 Gemini 응답 필드가 이 가정과 다르면 구현 세션에서 조정 필요(9-4/9-5 참고).
 */
class GeminiLlmClientTest {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent";

    private MockRestServiceServer server;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        ChatProperties properties = new ChatProperties(
                "gemini", 20, 10, 30, 100, new ChatProperties.Gemini("test-key", "gemini-3.7-flash"));
        client = new GeminiLlmClient(restClient, properties, new ObjectMapper());
    }

    private static ChatPrompt prompt(String userMessage) {
        return new ChatPrompt("system prompt", List.of(), userMessage);
    }

    @Test
    @DisplayName("도구 호출 없이 1턴 만에 최종 답변 JSON 파싱 성공")
    void noFunctionCall_parsesFinalAnswer() {
        String response = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"{\\"answer\\":\\"안녕하세요\\",\\"recommendations\\":[]}"}
                ]}}],"usageMetadata":{"totalTokenCount":42}}
                """;
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        LlmResult result = client.generate(prompt("안녕"), (name, args) -> "[]");

        assertThat(result.answerText()).isEqualTo("안녕하세요");
        assertThat(result.recommendations()).isEmpty();
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.tokenUsage()).isEqualTo(42L);
        server.verify();
    }

    @Test
    @DisplayName("functionCall 1라운드 후 최종 답변 — 도구 실행 인자·모델 content 재사용·id 수집 확인")
    void functionCallRound_thenFinalAnswer() {
        String round1 = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"functionCall":{"name":"searchBoardGames","args":{"players":3}}}
                ]}}]}
                """;
        String round2 = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"{\\"answer\\":\\"카탄을 추천합니다\\",\\"recommendations\\":[{\\"gameId\\":1,\\"reason\\":\\"인원이 맞아요\\"}]}"}
                ]}}],"usageMetadata":{"totalTokenCount":100}}
                """;
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(round1, MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("functionResponse")))
                .andExpect(content().string(Matchers.containsString("\"role\":\"model\"")))
                .andRespond(withSuccess(round2, MediaType.APPLICATION_JSON));

        List<String> calledTools = new ArrayList<>();
        List<String> calledArgs = new ArrayList<>();
        ToolExecutor toolExecutor = (name, argsJson) -> {
            calledTools.add(name);
            calledArgs.add(argsJson);
            return "[{\"id\":1,\"name\":\"카탄\"}]";
        };

        LlmResult result = client.generate(prompt("3인용 추천해줘"), toolExecutor);

        assertThat(calledTools).containsExactly("searchBoardGames");
        assertThat(calledArgs.get(0)).contains("\"players\":3");
        assertThat(result.answerText()).isEqualTo("카탄을 추천합니다");
        assertThat(result.recommendations()).containsExactly(new LlmResult.Recommendation(1L, "인원이 맞아요"));
        assertThat(result.toolReturnedGameIds()).containsExactly(1L);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.tokenUsage()).isEqualTo(100L);
        server.verify();
    }

    @Test
    @DisplayName("최종 답변이 JSON이 아니면 원문을 answer로, recommendations는 빈 배열(에러 아님)")
    void malformedFinalAnswer_fallsBackToRawText() {
        String response = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"이건 JSON이 아니에요"}]}}]}
                """;
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        LlmResult result = client.generate(prompt("안녕"), (name, args) -> "[]");

        assertThat(result.answerText()).isEqualTo("이건 JSON이 아니에요");
        assertThat(result.recommendations()).isEmpty();
    }

    @Test
    @DisplayName("3라운드 내내 functionCall만 오고 텍스트가 없으면 CHAT_UNAVAILABLE")
    void exceedsMaxRounds_withoutText_throwsUnavailable() {
        String functionCallOnly = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"functionCall":{"name":"searchBoardGames","args":{}}}
                ]}}]}
                """;
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(functionCallOnly, MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(() -> client.generate(prompt("추천해줘"), (name, args) -> "[]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_UNAVAILABLE);
        server.verify();
    }

    @Test
    @DisplayName("429 응답 → 1초 대기 후 1회 재시도, 재시도도 429면 CHAT_BUSY (요청이 정확히 2번 나감)")
    void tooManyRequests_retriesOnceThenBusy() {
        server.expect(requestTo(ENDPOINT)).andRespond(withTooManyRequests());
        server.expect(requestTo(ENDPOINT)).andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.generate(prompt("추천해줘"), (name, args) -> "[]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_BUSY);
        server.verify(); // 두 요청 모두 소비됐는지 = 실제로 1회 재시도했는지 확인
    }

    @Test
    @DisplayName("503(UNAVAILABLE, high demand) 응답 → 1초 대기 후 1회 재시도, 재시도도 503이면 CHAT_BUSY (요청이 정확히 2번 나감)")
    void serviceUnavailable_retriesOnceThenBusy() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.generate(prompt("추천해줘"), (name, args) -> "[]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_BUSY);
        server.verify(); // 두 요청 모두 소비됐는지 = 실제로 1회 재시도했는지 확인
    }

    @Test
    @DisplayName("5xx 응답(503 제외) → CHAT_UNAVAILABLE")
    void serverError_throwsUnavailable() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> client.generate(prompt("추천해줘"), (name, args) -> "[]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_UNAVAILABLE);
    }

    @Test
    @DisplayName("history의 ASSISTANT는 role \"model\"로 매핑된다")
    void historyAssistantRole_mapsToModel() {
        String response = """
                {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"answer\\":\\"네\\",\\"recommendations\\":[]}"}]}}]}
                """;
        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(Matchers.containsString("\"role\":\"model\"")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ChatPrompt prompt = new ChatPrompt("system prompt",
                List.of(new ChatMessage(ChatRole.USER, "안녕"), new ChatMessage(ChatRole.ASSISTANT, "네 안녕하세요")),
                "3인용 추천해줘");

        client.generate(prompt, (name, args) -> "[]");

        server.verify();
    }
}
