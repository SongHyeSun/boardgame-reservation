package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.boardgame.reservation.support.MultipartTestUtils.createBoardGame;
import static com.boardgame.reservation.support.MultipartTestUtils.updateBoardGame;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보드게임 읽기 API(목록 필터·상세)와 쓰기 API의 권한(401/403)을 실제 Security 필터 체인까지 태워서 검증.
 * DB는 src/test/resources/application.yml 의 H2 사용, 회원·Redis 없이 돈다.
 * 등록·수정·숨기기의 실제 동작(소유자 검사, 이미지, 파티 취소)은 로그인 회원이 필요해서
 * BoardGameManageIntegrationTest / BoardGameVisibilityIntegrationTest 에서 검증한다.
 *
 * 시드 (id 오름차순, 모두 오프라인 전용·등록 관리자 없음):
 *   Catan     3~4명 NORMAL
 *   Pandemic  2~4명 NORMAL
 *   Splendor  2~4명 EASY
 *   Agricola  1~5명 HARD
 */
@SpringBootTest
class BoardGameApiIntegrationTest {

    private static final RequestPostProcessor USER = user("user").roles("USER");

    private static final String VALID_DATA = """
            {"name":"Carcassonne","minPlayers":2,"maxPlayers":5,"playTime":45,
             "difficulty":"EASY","description":"타일 배치 게임",
             "offlineAvailable":true,"onlineAvailable":false,"stock":2}
            """;

    private static final String VISIBILITY_HIDE = "{\"visible\":false}";

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

    private BoardGame saveGame(String name, boolean offline, boolean online, int stock) {
        return boardGameRepository.save(BoardGame.create(
                new BoardGame.Details(name, 2, 4, 30, Difficulty.EASY, "설명", offline, online, stock), null));
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
    @DisplayName("비로그인은 등록/수정/숨기기 시 401")
    void anonymous_cannotWrite() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(createBoardGame(VALID_DATA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(updateBoardGame(id, VALID_DATA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/boardgames/{id}/visibility", id)
                        .contentType(MediaType.APPLICATION_JSON).content(VISIBILITY_HIDE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 회원(USER)은 등록/수정/숨기기 시 403, 데이터는 그대로")
    void user_cannotWrite() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(createBoardGame(VALID_DATA).with(USER))
                .andExpect(status().isForbidden());
        mockMvc.perform(updateBoardGame(id, VALID_DATA).with(USER))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/boardgames/{id}/visibility", id).with(USER)
                        .contentType(MediaType.APPLICATION_JSON).content(VISIBILITY_HIDE))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data.length()").value(4));
        mockMvc.perform(get("/api/boardgames/{id}", id))
                .andExpect(jsonPath("$.data.name").value("Catan"))
                .andExpect(jsonPath("$.data.visible").value(true));
    }

    @Test
    @DisplayName("mine=true 는 로그인이 필요하다 (비로그인 401)")
    void list_mine_requiresLogin() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("mine", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    // ───────────── 상세 응답 ─────────────

    @Test
    @DisplayName("상세 응답에 진행 방식·재고·노출 여부·이미지·영상·등록 관리자 필드가 있다 (기존 게임은 오프라인 전용, owner 없음)")
    void detail_hasExtendedFields() throws Exception {
        Long id = boardGameRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/boardgames/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offlineAvailable").value(true))
                .andExpect(jsonPath("$.data.onlineAvailable").value(false))
                .andExpect(jsonPath("$.data.stock").value(1))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isEmpty())
                .andExpect(jsonPath("$.data.youtubeVideoId").isEmpty())
                .andExpect(jsonPath("$.data.owner").isEmpty());
    }

    @Test
    @DisplayName("숨긴 게임도 상세는 조회된다 (visible:false)")
    void detail_hiddenGame_stillReadable() throws Exception {
        BoardGame hidden = saveGame("Hidden", true, false, 1);
        hidden.hide();
        boardGameRepository.save(hidden);

        mockMvc.perform(get("/api/boardgames/{id}", hidden.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hidden"))
                .andExpect(jsonPath("$.data.visible").value(false));
    }

    // ───────────── 목록 필터 ─────────────

    @Test
    @DisplayName("필터 없으면 숨기지 않은 전체를 id 오름차순으로 반환")
    void list_noFilter() throws Exception {
        mockMvc.perform(get("/api/boardgames"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan", "Pandemic", "Splendor", "Agricola")));
    }

    @Test
    @DisplayName("숨긴 게임은 기본 목록에서 빠진다")
    void list_excludesHidden() throws Exception {
        BoardGame hidden = saveGame("Hidden", true, false, 1);
        hidden.hide();
        boardGameRepository.save(hidden);

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan", "Pandemic", "Splendor", "Agricola")));
        // 이름 검색에도 나오지 않는다 (게임 선택 모달이 이 목록을 재사용)
        mockMvc.perform(get("/api/boardgames").param("keyword", "hidden"))
                .andExpect(jsonPath("$.data").value(empty()));
    }

    @Test
    @DisplayName("playMode: 그 진행 방식이 가능한 게임만 (둘 다 가능한 게임은 양쪽에 나온다)")
    void list_playModeFilter() throws Exception {
        saveGame("OnlineOnly", false, true, 0);
        saveGame("Both", true, true, 2);

        mockMvc.perform(get("/api/boardgames").param("playMode", "ONLINE"))
                .andExpect(jsonPath("$.data[*].name").value(contains("OnlineOnly", "Both")));

        mockMvc.perform(get("/api/boardgames").param("playMode", "OFFLINE"))
                .andExpect(jsonPath("$.data[*].name")
                        .value(contains("Catan", "Pandemic", "Splendor", "Agricola", "Both")));

        // 다른 필터와 AND
        mockMvc.perform(get("/api/boardgames").param("playMode", "ONLINE").param("keyword", "both"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Both")));
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
    @DisplayName("잘못된 difficulty / players / playMode 타입은 500이 아니라 400")
    void list_invalidParamType() throws Exception {
        mockMvc.perform(get("/api/boardgames").param("difficulty", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/boardgames").param("players", "abc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/boardgames").param("playMode", "HYBRID"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/boardgames/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 id 상세는 404")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/boardgames/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
