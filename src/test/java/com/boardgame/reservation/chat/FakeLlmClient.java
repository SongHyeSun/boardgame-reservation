package com.boardgame.reservation.chat;

import com.boardgame.reservation.chat.llm.ChatPrompt;
import com.boardgame.reservation.chat.llm.LlmClient;
import com.boardgame.reservation.chat.llm.LlmResult;
import com.boardgame.reservation.chat.llm.ToolExecutor;

import java.util.function.Supplier;

/** 테스트 전용 LlmClient 구현체. 시나리오별로 result 또는 예외를 미리 설정해 두고 쓴다(실제 Gemini 호출 없음). */
public class FakeLlmClient implements LlmClient {

    private Supplier<LlmResult> behavior = () -> {
        throw new IllegalStateException("FakeLlmClient: 결과가 설정되지 않았습니다.");
    };

    public static FakeLlmClient returning(LlmResult result) {
        FakeLlmClient client = new FakeLlmClient();
        client.setResult(result);
        return client;
    }

    public void setResult(LlmResult result) {
        this.behavior = () -> result;
    }

    public void setException(RuntimeException exception) {
        this.behavior = () -> {
            throw exception;
        };
    }

    @Override
    public LlmResult generate(ChatPrompt prompt, ToolExecutor toolExecutor) {
        return behavior.get();
    }
}
