package com.boardgame.reservation.chat.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.boardgame.repository.BoardGameSpecification;
import com.boardgame.reservation.chat.llm.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * LLM이 호출하는 searchBoardGames 도구. 무상태(요청별 상태를 필드로 두지 않음) — 싱글톤 빈이라
 * "이번 요청에서 반환한 id" 같은 상태를 여기 두면 동시 요청 간 경합이 생기므로 절대 필드에 두지 않는다
 * (그 상태는 GeminiLlmClient.generate() 안의 지역 변수가 관리한다).
 * LLM이 준 인자가 깨져 있어도(형식이 아예 JSON이 아니거나, enum 오타 등) 예외를 던지지 않고 조건을 무시하거나 빈 결과를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSearchTool implements ToolExecutor {

    private static final int MAX_RESULTS = 20;

    private final BoardGameRepository boardGameRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(String toolName, String argumentsJson) {
        JsonNode args = parseArguments(argumentsJson);
        Integer players = asInteger(args, "players");
        Integer maxPlayTime = asInteger(args, "maxPlayTime");
        Difficulty difficulty = asEnum(args, "difficulty", Difficulty.class);
        PlayMode playMode = asEnum(args, "playMode", PlayMode.class);
        String keyword = args.path("keyword").isString() ? args.path("keyword").asString() : null;

        List<BoardGame> games = boardGameRepository.findAll(
                        BoardGameSpecification.search(players, difficulty, keyword, playMode, null)
                                .and(BoardGameSpecification.maxPlayTime(maxPlayTime)),
                        Sort.by("id"))
                .stream()
                .limit(MAX_RESULTS)
                .toList();

        return objectMapper.writeValueAsString(games.stream().map(GameSearchResult::from).toList());
    }

    private JsonNode parseArguments(String argumentsJson) {
        try {
            return objectMapper.readTree(argumentsJson);
        } catch (JacksonException e) {
            log.debug("chat tool: malformed arguments ignored: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private Integer asInteger(JsonNode args, String field) {
        JsonNode node = args.path(field);
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    private <T extends Enum<T>> T asEnum(JsonNode args, String field, Class<T> type) {
        JsonNode node = args.path(field);
        if (!node.isString()) {
            return null;
        }
        try {
            return Enum.valueOf(type, node.asString());
        } catch (IllegalArgumentException e) {
            log.debug("chat tool: unknown {} value ignored: {}", field, node.asString());
            return null;
        }
    }
}
