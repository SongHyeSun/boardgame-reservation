package com.boardgame.reservation.chat.llm;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;

/** GEMINI_API_KEY 가 없거나 provider 가 gemini 가 아닐 때 쓰는 빈. 항상 CHAT_UNAVAILABLE. */
public class DisabledLlmClient implements LlmClient {

    @Override
    public LlmResult generate(ChatPrompt prompt, ToolExecutor toolExecutor) {
        throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE);
    }
}
