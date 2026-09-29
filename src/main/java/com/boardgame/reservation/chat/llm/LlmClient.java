package com.boardgame.reservation.chat.llm;

/**
 * 제공자 중립 LLM 클라이언트. 함수 호출 반복(모델 호출 → 도구 실행 → 결과 전달 → 최종 답변)은
 * 구현체 내부에서 처리한다 — 제공자마다 되돌려줘야 하는 값이 달라서 인터페이스 밖으로 새지 않게 한다.
 *
 * @throws com.boardgame.reservation.global.exception.BusinessException
 *         CHAT_UNAVAILABLE / CHAT_BUSY 만 던진다. 내부 예외(HTTP, 파싱 등)는 구현체가 전부 감싼다.
 */
public interface LlmClient {

    LlmResult generate(ChatPrompt prompt, ToolExecutor toolExecutor);
}
