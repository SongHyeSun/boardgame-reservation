package com.boardgame.reservation.chat.llm;

import com.boardgame.reservation.chat.service.ChatRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;

/**
 * LlmClient 빈 선택: FileStorage/LocalFileStorage와 같은 패턴(인터페이스 + 단일 구현체, 나중에 교체).
 * provider가 gemini이고 키가 있어야 GeminiLlmClient, 그 외에는 항상 DisabledLlmClient.
 */
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatLlmConfig {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    @Bean
    public RestClient geminiRestClient(ChatProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(GEMINI_BASE_URL).requestFactory(factory).build();
    }

    @Bean
    public LlmClient llmClient(ChatProperties properties, RestClient geminiRestClient, ObjectMapper objectMapper) {
        boolean geminiReady = "gemini".equals(properties.provider())
                && properties.gemini().apiKey() != null
                && !properties.gemini().apiKey().isBlank();
        return geminiReady
                ? new GeminiLlmClient(geminiRestClient, properties, objectMapper)
                : new DisabledLlmClient();
    }

    @Bean
    public ChatRateLimiter chatRateLimiter(StringRedisTemplate redisTemplate, Clock clock, ChatProperties properties) {
        return new ChatRateLimiter(redisTemplate, clock, properties.dailyLimit());
    }
}
