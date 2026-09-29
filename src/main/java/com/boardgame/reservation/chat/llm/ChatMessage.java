package com.boardgame.reservation.chat.llm;

/** 제공자 중립 대화 메시지 (LlmClient 인터페이스에서만 쓰는 내부 모델, API DTO와는 별개). */
public record ChatMessage(ChatRole role, String content) {
}
