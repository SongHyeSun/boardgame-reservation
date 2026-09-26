package com.boardgame.reservation.support;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * multipart 요청 조립 헬퍼. 서버는 data 파트를 JSON 으로 읽으므로 Content-Type 을 application/json 으로 지정해야 한다
 * (지정하지 않으면 415 → INVALID_INPUT).
 */
public final class MultipartTestUtils {

    private MultipartTestUtils() {
    }

    public static MockMultipartFile jsonPart(String partName, String json) {
        return new MockMultipartFile(partName, "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    /** POST /api/auth/signup : data(JSON) + 선택 파일 파트 */
    public static MockMultipartHttpServletRequestBuilder signup(String dataJson, MockMultipartFile... files) {
        return withFiles(multipart("/api/auth/signup"), dataJson, files);
    }

    /** PUT /api/members/me : data(JSON) + 선택 파일 파트 */
    public static MockMultipartHttpServletRequestBuilder updateMe(String dataJson, MockMultipartFile... files) {
        return withFiles(multipart(HttpMethod.PUT, "/api/members/me"), dataJson, files);
    }

    private static MockMultipartHttpServletRequestBuilder withFiles(
            MockMultipartHttpServletRequestBuilder builder, String dataJson, MockMultipartFile... files) {
        builder.file(jsonPart("data", dataJson));
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        return builder;
    }
}
