package com.boardgame.reservation.chat;

import com.boardgame.reservation.chat.llm.LlmClient;
import com.boardgame.reservation.chat.llm.LlmResult;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Set;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc + Security 필터 체인 + H2 + Redis 컨테이너 공유(RedisIntegrationTestSupport).
 * 실제 Gemini 설정과 무관하게 동작하도록 LlmClient를 FakeLlmClient로 갈아끼운다(@Primary).
 */
class ChatApiIntegrationTest extends RedisIntegrationTestSupport {

    @TestConfiguration
    static class FakeLlmClientConfig {
        @Bean
        @Primary
        LlmClient testLlmClient() {
            return new FakeLlmClient();
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    FakeLlmClient fakeLlmClient;

    MockMvc mockMvc;
    Member member;

    @BeforeEach
    void setUpChatApi() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        member = saveMember("chatter");
    }

    @Test
    @DisplayName("비로그인 → 401")
    void recommend_withoutLogin_401() throws Exception {
        mockMvc.perform(post("/api/chat/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"3인용 추천해줘\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("성공 응답 포맷: answer/recommendations/remainingToday")
    void recommend_success_returnsExpectedFormat() throws Exception {
        fakeLlmClient.setResult(new LlmResult("추천합니다", List.of(), Set.of(), 0, null));

        mockMvc.perform(post("/api/chat/recommend")
                        .with(loginAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"3인용 추천해줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("추천합니다"))
                .andExpect(jsonPath("$.data.recommendations").isArray())
                .andExpect(jsonPath("$.data.remainingToday").value(19));
    }

    @Test
    @DisplayName("빈 메시지 → 400")
    void recommend_blankMessage_400() throws Exception {
        mockMvc.perform(post("/api/chat/recommend")
                        .with(loginAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("501자 메시지 → 400")
    void recommend_tooLongMessage_400() throws Exception {
        String longMessage = "가".repeat(501);

        mockMvc.perform(post("/api/chat/recommend")
                        .with(loginAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + longMessage + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("history 11개 → 400")
    void recommend_tooManyHistoryItems_400() throws Exception {
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                history.append(",");
            }
            history.append("{\"role\":\"USER\",\"content\":\"메시지").append(i).append("\"}");
        }
        String body = "{\"message\":\"추천해줘\",\"history\":[" + history + "]}";

        mockMvc.perform(post("/api/chat/recommend")
                        .with(loginAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 role → 400")
    void recommend_invalidRole_400() throws Exception {
        String body = "{\"message\":\"추천해줘\",\"history\":[{\"role\":\"SYSTEM\",\"content\":\"안녕\"}]}";

        mockMvc.perform(post("/api/chat/recommend")
                        .with(loginAs(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("usage 비로그인 → 401")
    void usage_withoutLogin_401() throws Exception {
        mockMvc.perform(get("/api/chat/usage")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("usage 로그인 → limit/used/remaining")
    void usage_loggedIn_returnsUsage() throws Exception {
        mockMvc.perform(get("/api/chat/usage").with(loginAs(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(20))
                .andExpect(jsonPath("$.data.used").value(0))
                .andExpect(jsonPath("$.data.remaining").value(20));
    }
}
