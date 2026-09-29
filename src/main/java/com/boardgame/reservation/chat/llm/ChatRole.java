package com.boardgame.reservation.chat.llm;

/** 대화 턴의 발화자. Gemini 요청에서는 USER→"user", ASSISTANT→"model"로 매핑한다. */
public enum ChatRole {
    USER,
    ASSISTANT
}
