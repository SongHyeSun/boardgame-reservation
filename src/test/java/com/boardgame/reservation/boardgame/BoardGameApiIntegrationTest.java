package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드게임 CRUD + 목록 필터를 실제 Security 필터 체인까지 태워서 검증.
 * DB는 src/test/resources/application.yml 의 H2 사용.
 *
 * 시드 (id 오름차순):
 *   Catan     3~4명 NORMAL
 *   Pandemic  2~4명 NORMAL
 *   Splendor  2~4명 EASY
 *   Agricola  1~5명 HARD
 */
@SpringBootTest
class BoardGameApiIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    private static final RequestPostProcessor USER = user("user").roles("USER");

    private static final String VALID_BODY = """
            {"name":"Carcassonne","minPlayers":2,"maxPlayers":5,"playTime":45,
             "difficulty":"EASY","description":"타일 배치 게임"}
            """;

    @Autowired
    WebApplicationContext context;

    @Autowired
    BoardGameRepository boardGameRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        boardGameRepository.save(BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "자원 교환"));
        boardGameRepository.save(BoardGame.create("Pandemic", 2, 4, 45, Difficulty.NORMAL, "협력"));
        boardGameRepository.save(BoardGame.create("Splendor", 2, 4, 30, Difficulty.EASY, "보석"));
        boardGameRepository.save(BoardGame.create("Agricola", 1, 5, 120, Difficulty.HARD, "농장"));
    }

    @AfterEach
    void tearDown() {
        boardGameRepository.deleteAll();
    }

    // ───────────── 권한 ─────────────

    @Test
    @DisplayName("비로그인도 목록/상세 조회는 가능하다")
    void anonymous_canRead() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/boardgames/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Catan"));
    }

    @Test
    @DisplayName("비로그인은 등록/수정/삭제 시 401")
    void anonymous_cannotWrite() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/boardgames").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/boardgames/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/boardgames/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 회원(USER)은 등록/수정/삭제 시 403, 데이터는 그대로")
    void user_cannotWrite() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/boardgames").with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/boardgames/{id}", id).with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/boardgames/{id}", id).with(USER))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    // ───────────── ADMIN CRUD ─────────────

    @Test
    @DisplayName("ADMIN: 등록(201) → 상세 → 수정 → 삭제 → 삭제 후 404")
    void admin_crudFlow() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/boardgames").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Carcassonne"))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andReturn();
        long id = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id")).longValue();

        mockMvc.perform(get("/api/boardgames/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minPlayers").value(2))
                .andExpect(jsonPath("$.data.maxPlayers").value(5))
                .andExpect(jsonPath("$.data.playTime").value(45));

        mockMvc.perform(put("/api/boardgames/{id}", id).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carcassonne 2","minPlayers":1,"maxPlayers":6,"playTime":60,
                                 "difficulty":"NORMAL","description":"수정됨"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Carcassonne 2"))
                .andExpect(jsonPath("$.data.maxPlayers").value(6))
                .andExpect(jsonPath("$.data.difficulty").value("NORMAL"));

        mockMvc.perform(delete("/api/boardgames/{id}", id).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/boardgames/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("없는 id 상세/수정/삭제는 404")
    void notFound() throws Exception {
        mockMvc.perform(get("/api/boardgames/{id}", 999999L))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/boardgames/{id}", 999999L).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/boardgames/{id}", 999999L).with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("등록 시 필수값 누락 / 인원 범위 오류 / 잘못된 난이도는 400")
    void create_invalidInput() throws Exception {
        // 이름 누락
        mockMvc.perform(post("/api/boardgames").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"EASY"}
                                """))
                .andExpect(status().isBadRequest());

        // 최소 인원 0
        mockMvc.perform(post("/api/boardgames").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","minPlayers":0,"maxPlayers":4,"playTime":30,"difficulty":"EASY"}
                                """))
                .andExpect(status().isBadRequest());

        // min > max
        mockMvc.perform(post("/api/boardgames").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","minPlayers":5,"maxPlayers":2,"playTime":30,"difficulty":"EASY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("최소 인원은 최대 인원보다 클 수 없습니다."));

        // enum 오타
        mockMvc.perform(post("/api/boardgames").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"VERY_HARD"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    // ───────────── 목록 필터 ─────────────

    @Test
    @DisplayName("필터 없으면 전체를 id 오름차순으로 반환")
    void list_noFilter() throws Exception {
        mockMvc.perform(get("/api/boardgames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan", "Pandemic", "Splendor", "Agricola")));
    }

    @Test
    @DisplayName("players: min~max 범위에 포함되는 게임만 (경계값 포함)")
    void list_playersFilter() throws Exception {
        // 2명: Catan(3~4) 제외
        mockMvc.perform(get("/api/boardgames").param("players", "2"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Pandemic", "Splendor", "Agricola")));

        // 3명: 전부
        mockMvc.perform(get("/api/boardgames").param("players", "3"))
                .andExpect(jsonPath("$.data.length()").value(4));

        // 1명: min 경계 → Agricola 만
        mockMvc.perform(get("/api/boardgames").param("players", "1"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Agricola")));

        // 5명: max 경계 → Agricola 만
        mockMvc.perform(get("/api/boardgames").param("players", "5"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Agricola")));

        // 6명: 범위 밖 → 빈 목록 (200)
        mockMvc.perform(get("/api/boardgames").param("players", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    @DisplayName("difficulty: 난이도가 일치하는 게임만")
    void list_difficultyFilter() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("difficulty", "NORMAL"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan", "Pandemic")));

        mockMvc.perform(get("/api/boardgames").param("difficulty", "HARD"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Agricola")));
    }

    @Test
    @DisplayName("keyword: 이름 부분 일치, 대소문자 무시")
    void list_keywordFilter() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("keyword", "cat"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan")));

        mockMvc.perform(get("/api/boardgames").param("keyword", "PAN"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Pandemic")));

        mockMvc.perform(get("/api/boardgames").param("keyword", "zzz"))
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    @DisplayName("keyword: 빈 문자열/공백은 필터 없음으로 취급")
    void list_blankKeyword_ignored() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("keyword", "  "))
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    @DisplayName("keyword: % 와 _ 는 와일드카드가 아니라 문자 그대로 검색")
    void list_keywordWildcardsEscaped() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("keyword", "%"))
                .andExpect(jsonPath("$.data").value(empty()));
        mockMvc.perform(get("/api/boardgames").param("keyword", "_"))
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    @DisplayName("players + difficulty + keyword 조합은 AND")
    void list_combinedFilters() throws Exception {
        mockMvc.perform(get("/api/boardgames")
                        .param("players", "2").param("difficulty", "NORMAL").param("keyword", "pan"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Pandemic")));

        // 2명 + NORMAL + 'cat' → Catan 은 3명부터라 제외
        mockMvc.perform(get("/api/boardgames")
                        .param("players", "2").param("difficulty", "NORMAL").param("keyword", "cat"))
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    @DisplayName("잘못된 difficulty / players 타입은 500이 아니라 400")
    void list_invalidParamType() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("difficulty", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/boardgames").param("players", "abc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/boardgames/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }
}
