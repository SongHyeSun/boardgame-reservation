package com.boardgame.reservation.chat.llm;

import java.util.List;

/** LlmClient.generate 에 넘기는 입력. history 는 이번 사용자 메시지(userMessage) 이전까지의 대화만 담는다. */
public record ChatPrompt(String systemPrompt, List<ChatMessage> history, String userMessage) {
}
