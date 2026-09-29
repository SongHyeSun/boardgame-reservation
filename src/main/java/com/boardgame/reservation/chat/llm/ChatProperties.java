package com.boardgame.reservation.chat.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.chat.* 설정. gemini.api-key 가 비어 있으면(또는 provider 가 gemini 가 아니면) DisabledLlmClient 를 쓴다. */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(String provider, int dailyLimit, int maxHistory, int timeoutSeconds, Gemini gemini) {

    public record Gemini(String apiKey, String model) {
    }
}
