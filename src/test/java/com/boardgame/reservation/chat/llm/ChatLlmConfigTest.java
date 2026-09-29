package com.boardgame.reservation.chat.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmClient 빈 선택 로직 단위 테스트. 다른 테스트(ChatApiIntegrationTest 등)는 전부 FakeLlmClient로 이 로직을
 * 우회하므로, 이 테스트가 없으면 "provider+키 유무로 빈을 고른다"는 분기가 전혀 검증되지 않는다.
 */
class ChatLlmConfigTest {

    private final ChatLlmConfig config = new ChatLlmConfig();
    private final RestClient dummyRestClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("키가 비어 있으면 provider가 gemini여도 DisabledLlmClient")
    void blankApiKey_returnsDisabledLlmClient() {
        ChatProperties properties = properties("gemini", "");

        LlmClient client = config.llmClient(properties, dummyRestClient, objectMapper);

        assertThat(client).isInstanceOf(DisabledLlmClient.class);
    }

    @Test
    @DisplayName("키가 있어도 provider가 gemini가 아니면 DisabledLlmClient")
    void providerMismatch_returnsDisabledLlmClient() {
        ChatProperties properties = properties("claude", "test-key");

        LlmClient client = config.llmClient(properties, dummyRestClient, objectMapper);

        assertThat(client).isInstanceOf(DisabledLlmClient.class);
    }

    @Test
    @DisplayName("provider가 gemini이고 키가 있으면 GeminiLlmClient")
    void geminiWithKey_returnsGeminiLlmClient() {
        ChatProperties properties = properties("gemini", "test-key");

        LlmClient client = config.llmClient(properties, dummyRestClient, objectMapper);

        assertThat(client).isInstanceOf(GeminiLlmClient.class);
    }

    private static ChatProperties properties(String provider, String apiKey) {
        return new ChatProperties(provider, 20, 10, 30, 100, new ChatProperties.Gemini(apiKey, "gemini-3.7-flash"));
    }
}
