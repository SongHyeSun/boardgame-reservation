package com.boardgame.reservation.chat.llm;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Google Gemini {@code generateContent} REST 연동(사용자가 확정한 표준 스펙 기준, docs/chatbot-plan.md 계획 2-1a).
 * 요청/응답은 POJO가 아니라 JsonNode 트리 모델로 다룬다 — 함수 호출 라운드마다 모델 응답의 content 객체를
 * 필드 추가·삭제 없이 그대로 다음 요청에 이어 붙여야 하기 때문(멀티라운드에서 되돌려줘야 하는 값 문제 해결).
 *
 * 이 클래스는 {@link BusinessException}(CHAT_UNAVAILABLE/CHAT_BUSY) 외에는 아무것도 던지지 않는다 —
 * RestClient 예외·JSON 파싱 예외·그 외 예상 못 한 예외까지 전부 여기서 잡아 변환한다.
 * 감쌀 때마다 원인을 알 수 있게 log.warn을 남기고(HTTP 상태코드+응답 본문 / 타임아웃·연결 실패 클래스+메시지 /
 * 파싱 실패 단계+원인+본문 앞부분 / 도구 라운드 번호), BusinessException의 cause로도 원본 예외를 유지한다.
 * API 키·요청 헤더·사용자 메시지 원문은 절대 로그에 남기지 않는다(응답 본문·모델이 생성한 텍스트만 남김).
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    private static final int MAX_TOOL_ROUNDS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final String TOOL_NAME = "searchBoardGames";
    private static final int LOG_BODY_MAX_LENGTH = 1000;

    private final RestClient restClient;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(RestClient restClient, ChatProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResult generate(ChatPrompt prompt, ToolExecutor toolExecutor) {
        try {
            return doGenerate(prompt, toolExecutor);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // 아래 3단계(HTTP 호출/요청 조립/도구 라운드/최종 답변) 어디에도 안 걸린 예상 못 한 예외에 대한 안전망
            log.warn("chat gemini: model={} unexpected failure class={} message={}",
                    properties.gemini().model(), e.getClass().getName(), e.getMessage());
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE, e);
        }
    }

    private LlmResult doGenerate(ChatPrompt prompt, ToolExecutor toolExecutor) {
        ArrayNode contents = buildInitialContents(prompt);
        ObjectNode toolDeclaration = buildToolDeclaration();
        Set<Long> toolReturnedGameIds = new HashSet<>();
        JsonNode lastResponse = null;

        int toolCallCount = 0;
        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            lastResponse = callGenerateContent(buildRequestBody(prompt.systemPrompt(), contents, toolDeclaration), round);
            JsonNode content = extractCandidateContent(lastResponse, round);
            JsonNode functionCall = findFunctionCall(content);
            if (functionCall == null) {
                return parseFinalAnswer(extractText(content), toolReturnedGameIds, toolCallCount,
                        extractTokenUsage(lastResponse), round);
            }

            toolCallCount++;
            String toolName = functionCall.path("name").asString("");
            String argumentsJson = writeArgumentsJson(functionCall.path("args"), round);
            JsonNode toolResult = readToolResult(toolExecutor.execute(toolName, argumentsJson), round);
            collectIds(toolResult, toolReturnedGameIds);

            contents.add(content); // 모델 응답을 그대로(수정 없이) 이어 붙임 — 9-5
            contents.add(buildFunctionResponseTurn(toolName, toolResult));
        }

        // 라운드 초과 — 마지막 응답에 텍스트가 있으면 그걸로 답변, 없으면 실패
        String text = extractText(extractCandidateContent(lastResponse, MAX_TOOL_ROUNDS));
        if (text == null || text.isBlank()) {
            log.warn("chat gemini: model={} exceeded {} tool rounds without a final text answer",
                    properties.gemini().model(), MAX_TOOL_ROUNDS);
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE);
        }
        return parseFinalAnswer(text, toolReturnedGameIds, toolCallCount, extractTokenUsage(lastResponse), MAX_TOOL_ROUNDS);
    }

    // ───────────── HTTP 호출 ─────────────

    private JsonNode callGenerateContent(ObjectNode requestBody, int round) {
        try {
            return doCall(requestBody);
        } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException.ServiceUnavailable firstFailure) {
            // 429(무료 티어 한도)와 503(UNAVAILABLE, high demand) 둘 다 일시적 과부하 신호라 같은 재시도 정책을 쓴다
            sleepBeforeRetry(round);
            try {
                return doCall(requestBody);
            } catch (RuntimeException retryFailure) {
                throw translateHttpFailure(retryFailure, round);
            }
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw translateHttpFailure(e, round);
        }
    }

    private JsonNode doCall(ObjectNode requestBody) {
        return restClient.post()
                .uri("/v1beta/models/{model}:generateContent", properties.gemini().model())
                .header("x-goog-api-key", properties.gemini().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * HTTP 에러(RestClientResponseException)는 상태코드+응답 본문(최대 1000자)을,
     * 타임아웃·연결 실패(ResourceAccessException)는 원인 예외 클래스명+메시지를 로그로 남기고 BusinessException으로 변환한다.
     * 429·503(재시도 후에도 실패)은 CHAT_BUSY, 그 외는 CHAT_UNAVAILABLE.
     */
    private BusinessException translateHttpFailure(RuntimeException e, int round) {
        if (e instanceof RestClientResponseException httpError) {
            log.warn("chat gemini: model={} round={} http status={} statusText={} body={}",
                    properties.gemini().model(), round, httpError.getStatusCode().value(), httpError.getStatusText(),
                    truncate(httpError.getResponseBodyAsString()));
            ErrorCode errorCode = isBusyStatus(httpError) ? ErrorCode.CHAT_BUSY : ErrorCode.CHAT_UNAVAILABLE;
            return new BusinessException(errorCode, e);
        }
        Throwable rootCause = e.getCause() != null ? e.getCause() : e;
        log.warn("chat gemini: model={} round={} connection failure class={} message={}",
                properties.gemini().model(), round, rootCause.getClass().getName(), rootCause.getMessage());
        return new BusinessException(ErrorCode.CHAT_UNAVAILABLE, e);
    }

    private static boolean isBusyStatus(RestClientResponseException e) {
        return e instanceof HttpClientErrorException.TooManyRequests || e instanceof HttpServerErrorException.ServiceUnavailable;
    }

    private void sleepBeforeRetry(int round) {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("chat gemini: model={} round={} interrupted while waiting to retry after busy response(429/503)",
                    properties.gemini().model(), round);
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE, e);
        }
    }

    // ───────────── 요청 조립 ─────────────

    private ArrayNode buildInitialContents(ChatPrompt prompt) {
        ArrayNode contents = objectMapper.createArrayNode();
        for (ChatMessage message : prompt.history()) {
            contents.add(turnOf(geminiRole(message.role()), message.content()));
        }
        contents.add(turnOf("user", prompt.userMessage()));
        return contents;
    }

    private static String geminiRole(ChatRole role) {
        return role == ChatRole.ASSISTANT ? "model" : "user";
    }

    private ObjectNode turnOf(String role, String text) {
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", role);
        turn.putArray("parts").addObject().put("text", text);
        return turn;
    }

    private ObjectNode buildRequestBody(String systemPrompt, ArrayNode contents, ObjectNode toolDeclaration) {
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("systemInstruction", systemInstruction);
        body.set("contents", contents);
        body.putArray("tools").add(toolDeclaration);
        return body;
    }

    private ObjectNode buildToolDeclaration() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("players", typeSchema("integer", "이 인원이 플레이 가능한 게임만"));
        properties.set("maxPlayTime", typeSchema("integer", "최대 플레이 시간(분)"));
        properties.set("difficulty", enumSchema(List.of("EASY", "NORMAL", "HARD"), "난이도"));
        properties.set("playMode", enumSchema(List.of("ONLINE", "OFFLINE"), "진행 방식"));
        properties.set("keyword", typeSchema("string", "게임 이름에 포함된 키워드"));

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        parameters.set("properties", properties);

        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", TOOL_NAME);
        function.put("description", "등록된(운영 중인) 보드게임을 조건으로 검색한다. 모든 파라미터는 선택이다.");
        function.set("parameters", parameters);

        ObjectNode tool = objectMapper.createObjectNode();
        tool.putArray("functionDeclarations").add(function);
        return tool;
    }

    private ObjectNode typeSchema(String type, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("description", description);
        return node;
    }

    private ObjectNode enumSchema(List<String> values, String description) {
        ObjectNode node = typeSchema("string", description);
        ArrayNode enumNode = node.putArray("enum");
        values.forEach(enumNode::add);
        return node;
    }

    private ObjectNode buildFunctionResponseTurn(String toolName, JsonNode toolResult) {
        ObjectNode responseWrapper = objectMapper.createObjectNode();
        responseWrapper.set("results", toolResult);

        ObjectNode functionResponse = objectMapper.createObjectNode();
        functionResponse.put("name", toolName);
        functionResponse.set("response", responseWrapper);

        ObjectNode part = objectMapper.createObjectNode();
        part.set("functionResponse", functionResponse);

        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", "user");
        turn.putArray("parts").add(part);
        return turn;
    }

    /** functionCall.args를 도구 호출 인자 문자열로 직렬화하는 단계("요청 조립") */
    private String writeArgumentsJson(JsonNode args, int round) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JacksonException e) {
            log.warn("chat gemini: model={} round={} stage=request-assembly parse failed cause={} body={}",
                    properties.gemini().model(), round, e.getMessage(), truncate(args.toString()));
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE, e);
        }
    }

    // ───────────── 응답 파싱 ─────────────

    private JsonNode extractCandidateContent(JsonNode response, int round) {
        JsonNode content = response.path("candidates").path(0).path("content");
        if (content.isMissingNode()) {
            log.warn("chat gemini: model={} round={} response missing candidates[0].content, body={}",
                    properties.gemini().model(), round, truncate(response.toString()));
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE);
        }
        return content;
    }

    private JsonNode findFunctionCall(JsonNode content) {
        for (JsonNode part : content.path("parts")) {
            if (part.has("functionCall")) {
                return part.path("functionCall");
            }
        }
        return null;
    }

    private String extractText(JsonNode content) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.has("text")) {
                text.append(part.path("text").asString(""));
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    private Long extractTokenUsage(JsonNode response) {
        JsonNode totalTokenCount = response.path("usageMetadata").path("totalTokenCount");
        return totalTokenCount.isMissingNode() || totalTokenCount.isNull() ? null : totalTokenCount.asLong();
    }

    /** 도구가 돌려준 JSON 문자열을 파싱하는 단계("도구 라운드") */
    private JsonNode readToolResult(String toolResultJson, int round) {
        try {
            return objectMapper.readTree(toolResultJson);
        } catch (JacksonException e) {
            log.warn("chat gemini: model={} round={} stage=tool-round parse failed cause={} body={}",
                    properties.gemini().model(), round, e.getMessage(), truncate(toolResultJson));
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE, e);
        }
    }

    private void collectIds(JsonNode toolResult, Set<Long> ids) {
        if (!toolResult.isArray()) {
            return;
        }
        for (JsonNode item : toolResult) {
            JsonNode id = item.path("id");
            if (id.isIntegralNumber()) {
                ids.add(id.asLong());
            }
        }
    }

    /**
     * 최종 답변 텍스트를 {answer, recommendations} JSON으로 파싱하는 단계("최종 답변").
     * 실패하면 원문을 answer로, recommendations는 빈 배열(에러로 만들지 않음 — BusinessException을 던지지 않음).
     */
    private LlmResult parseFinalAnswer(String text, Set<Long> toolReturnedGameIds, int toolCallCount, Long tokenUsage, int round) {
        if (text == null) {
            log.warn("chat gemini: model={} round={} final answer had no text part", properties.gemini().model(), round);
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE);
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(text);
        } catch (JacksonException e) {
            log.warn("chat gemini: model={} round={} stage=final-answer parse failed(not JSON, falling back to raw text) cause={} body={}",
                    properties.gemini().model(), round, e.getMessage(), truncate(text));
            return new LlmResult(text, List.of(), toolReturnedGameIds, toolCallCount, tokenUsage);
        }
        if (!parsed.has("answer")) {
            log.warn("chat gemini: model={} round={} stage=final-answer missing 'answer' field(falling back to raw text) body={}",
                    properties.gemini().model(), round, truncate(text));
            return new LlmResult(text, List.of(), toolReturnedGameIds, toolCallCount, tokenUsage);
        }

        List<LlmResult.Recommendation> recommendations = new ArrayList<>();
        for (JsonNode item : parsed.path("recommendations")) {
            recommendations.add(new LlmResult.Recommendation(item.path("gameId").asLong(), item.path("reason").asString("")));
        }
        return new LlmResult(parsed.path("answer").asString(text), recommendations, toolReturnedGameIds, toolCallCount, tokenUsage);
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > LOG_BODY_MAX_LENGTH ? text.substring(0, LOG_BODY_MAX_LENGTH) : text;
    }
}
