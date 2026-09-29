package com.boardgame.reservation.chat.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.chat.* 설정. gemini.api-key 가 비어 있으면(또는 provider 가 gemini 가 아니면) DisabledLlmClient 를 쓴다.
 *
 * timeoutSeconds: HTTP 호출 1회의 connect·read 타임아웃(각각). 호출 1회 최악 시간 = 2 × timeoutSeconds.
 * totalBudgetSeconds: 한 번의 generate(라운드·재시도 포함) 전체 시간 상한. 미설정(0)이면 100초
 *   (Vercel 프록시 120초보다 먼저 끝나도록). 호출은 "남은 시간 ≥ 호출 1회 최악 시간"일 때만 시작한다.
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(String provider, int dailyLimit, int maxHistory, int timeoutSeconds,
                             int totalBudgetSeconds, Gemini gemini) {

    static final int DEFAULT_TOTAL_BUDGET_SECONDS = 100;

    public ChatProperties {
        if (totalBudgetSeconds <= 0) {
            totalBudgetSeconds = DEFAULT_TOTAL_BUDGET_SECONDS;
        }
        if (timeoutSeconds > 0 && totalBudgetSeconds < 2L * timeoutSeconds) {
            // 이 조합이면 첫 호출조차 시작할 수 없다 → 기동 시점에 바로 드러낸다
            throw new IllegalArgumentException("app.chat.total-budget-seconds(" + totalBudgetSeconds
                    + ") must be >= 2 * app.chat.timeout-seconds(" + timeoutSeconds + ")");
        }
    }

    /** 호출 1회의 최악 소요 시간(connect + read) */
    public long worstCaseCallSeconds() {
        return 2L * timeoutSeconds;
    }

    public record Gemini(String apiKey, String model) {
    }
}
