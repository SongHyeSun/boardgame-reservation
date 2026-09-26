package com.boardgame.reservation.global.file;

import com.boardgame.reservation.support.ImageFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파일 서빙 API. Security 필터 체인 포함, 저장 위치는 test application.yml 의 app.upload.dir(build/test-uploads).
 * 로그인 없이(permitAll) 접근된다는 점도 함께 검증한다.
 */
@SpringBootTest
class FileControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FileStorage fileStorage;

    MockMvc mockMvc;
    final List<String> storedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        storedKeys.forEach(fileStorage::delete);
    }

    private String store(MockMultipartFile file) {
        String key = fileStorage.store(file, FileKeys.AVATARS);
        storedKeys.add(key);
        return key;
    }

    @Test
    @DisplayName("저장된 이미지는 로그인 없이 받을 수 있고, Content-Type 과 장기 캐시 헤더가 붙는다")
    void serve_success() throws Exception {
        String key = store(ImageFixtures.png("image"));

        mockMvc.perform(get("/api/files/" + key))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(content().bytes(ImageFixtures.pngBytes()));
    }

    @Test
    @DisplayName("jpg/webp 도 확장자에 맞는 Content-Type 으로 내려간다")
    void serve_contentTypes() throws Exception {
        String jpg = store(ImageFixtures.jpeg("image"));
        String webp = store(ImageFixtures.webp("image"));

        mockMvc.perform(get("/api/files/" + jpg))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
        mockMvc.perform(get("/api/files/" + webp))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"));
    }

    @Test
    @DisplayName("형식은 맞지만 없는 파일은 404 + 공통 응답 포맷")
    void serve_missing() throws Exception {
        mockMvc.perform(get("/api/files/avatars/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("파일을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("key 형식이 아닌 이름·허용되지 않은 dir·확장자는 404")
    void serve_invalidKey() throws Exception {
        mockMvc.perform(get("/api/files/avatars/evil.txt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/avatars/..png")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/secrets/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/avatars/00000000-0000-0000-0000-000000000000.gif"))
                .andExpect(status().isNotFound());
    }
}
