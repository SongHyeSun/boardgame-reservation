package com.boardgame.reservation.chat.llm;

/** LLM이 호출하는 도구의 실행기. 구현체(GameSearchTool)는 무상태여야 한다 — 싱글톤 빈이 요청별 상태를 가지면 안 됨. */
public interface ToolExecutor {

    /**
     * @param toolName     LLM이 호출한 도구 이름
     * @param argumentsJson LLM이 준 인자(JSON 문자열)
     * @return 도구 실행 결과(JSON 문자열)
     */
    String execute(String toolName, String argumentsJson);
}
