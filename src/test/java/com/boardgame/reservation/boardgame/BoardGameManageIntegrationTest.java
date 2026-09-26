package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.support.ImageFixtures;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static com.boardgame.reservation.support.MultipartTestUtils.createBoardGame;
import static com.boardgame.reservation.support.MultipartTestUtils.updateBoardGame;
import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게임 등록·수정·소유권·이미지·목록(mine)을 실제 회원 로그인(MemberPrincipal) + Security 필터 체인으로 검증.
 * 숨기기는 BoardGameVisibilityIntegrationTest.
 */
class BoardGameManageIntegrationTest extends RedisIntegrationTestSupport {

    private static final String YOUTUBE_ID = "dQw4w9WgXcQ";

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;
    Member admin;
    Member otherAdmin;
    Member superAdmin;
    Member normalUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        admin = saveAdmin("admin");
        otherAdmin = saveAdmin("other");
        superAdmin = memberRepository.save(Member.createSuperAdmin("root@test.com", "pw", "root"));
        normalUser = saveMember("user");
    }

    // ───────────── fixture ─────────────

    private static String json(String name, int min, int max, boolean offline, boolean online, int stock,
                               String youtubeUrl, boolean removeImage) {
        return """
                {"name":"%s","minPlayers":%d,"maxPlayers":%d,"playTime":45,"difficulty":"EASY","description":"설명",
                 "offlineAvailable":%b,"onlineAvailable":%b,"stock":%d,"youtubeUrl":%s,"removeImage":%b}
                """.formatted(name, min, max, offline, online, stock,
                youtubeUrl == null ? "null" : "\"" + youtubeUrl + "\"", removeImage);
    }

    private static String validJson() {
        return json("Carcassonne", 2, 5, true, true, 3, "https://youtu.be/" + YOUTUBE_ID, false);
    }

    private BoardGame saveOwnedGame(String name, Member owner) {
        return boardGameRepository.save(BoardGame.create(
                new BoardGame.Details(name, 2, 4, 30, Difficulty.NORMAL, "설명", true, false, 1), owner));
    }

    private static long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private static String imageUrlOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.imageUrl");
    }

    /** 이미지가 붙은 게임을 API 로 등록하고 결과를 돌려준다 */
    private MvcResult createWithImage(Member owner) throws Exception {
        return mockMvc.perform(createBoardGame(validJson(), ImageFixtures.jpeg("image")).with(loginAs(owner)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    // ───────────── 등록 ─────────────

    @Test
    @DisplayName("ADMIN 등록: 201, 소유 관리자·진행 방식·재고·유튜브 ID·imageUrl 이 응답에 담기고 이미지가 서빙된다")
    void create_success_withImageAndYoutube() throws Exception {
        MvcResult created = createWithImage(admin);

        mockMvc.perform(get("/api/boardgames/{id}", idOf(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Carcassonne"))
                .andExpect(jsonPath("$.data.offlineAvailable").value(true))
                .andExpect(jsonPath("$.data.onlineAvailable").value(true))
                .andExpect(jsonPath("$.data.stock").value(3))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.youtubeVideoId").value(YOUTUBE_ID))
                .andExpect(jsonPath("$.data.owner.id").value(admin.getId()))
                .andExpect(jsonPath("$.data.owner.nickname").value("admin"))
                .andExpect(jsonPath("$.data.imageUrl", startsWith("/api/files/boardgames/")));

        // 응답 URL 로 실제 이미지가 내려온다 (파일 key 가 아니라 URL 을 내려주는지도 확인)
        mockMvc.perform(get(imageUrlOf(created)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    @DisplayName("이미지·유튜브 없이도 등록된다 (imageUrl·youtubeVideoId 는 null)")
    void create_withoutMedia() throws Exception {
        mockMvc.perform(createBoardGame(json("Plain", 2, 4, true, false, 1, null, false)).with(loginAs(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").isEmpty())
                .andExpect(jsonPath("$.data.youtubeVideoId").isEmpty());
    }

    @Test
    @DisplayName("SUPER_ADMIN 도 ADMIN API 로 등록할 수 있고 소유자는 본인이다")
    void create_asSuperAdmin() throws Exception {
        mockMvc.perform(createBoardGame(validJson()).with(loginAs(superAdmin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.owner.id").value(superAdmin.getId()));
    }

    @Test
    @DisplayName("온라인 전용이면 요청 재고와 관계없이 재고 0 으로 저장된다")
    void create_onlineOnly_stockZero() throws Exception {
        mockMvc.perform(createBoardGame(json("Online", 2, 4, false, true, 7, null, false)).with(loginAs(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.offlineAvailable").value(false))
                .andExpect(jsonPath("$.data.onlineAvailable").value(true))
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    @Test
    @DisplayName("등록 시 검증 실패는 전부 400 이고 게임이 생기지 않는다")
    void create_invalidInputs() throws Exception {
        String[][] cases = {
                // {요청 data, 기대 메시지 일부}
                {"""
                {"minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"EASY",
                 "offlineAvailable":true,"onlineAvailable":false,"stock":1}
                """, "name"},
                {json("X", 0, 4, true, false, 1, null, false), "minPlayers"},
                {json("X", 5, 2, true, false, 1, null, false), "최소 인원은 최대 인원보다 클 수 없습니다."},
                {"""
                {"name":"X","minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"VERY_HARD",
                 "offlineAvailable":true,"onlineAvailable":false,"stock":1}
                """, "입력값이 올바르지 않습니다."},
                {json("X", 2, 4, false, false, 0, null, false), "온라인·오프라인 중 하나 이상 선택해야 합니다."},
                {json("X", 2, 4, true, false, 0, null, false), "오프라인 가능 게임은 재고가 1 이상이어야 합니다."},
                {json("X", 2, 4, true, false, 1, "https://vimeo.com/123", false), "올바른 유튜브 링크가 아닙니다."},
                // 진행 방식 필드 생략 (Boolean 이라 null → 검증 실패)
                {"""
                {"name":"X","minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"EASY",
                 "onlineAvailable":false,"stock":1}
                """, "offlineAvailable"},
        };

        for (String[] c : cases) {
            mockMvc.perform(createBoardGame(c[0]).with(loginAs(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString(c[1])));
        }
        assertThat(boardGameRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미지가 잘못됐으면 400 이고 게임은 생기지 않는다")
    void create_invalidImage() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("image", "photo.jpg", "image/jpeg", "not an image".getBytes());

        mockMvc.perform(createBoardGame(validJson(), fake).with(loginAs(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 파일입니다."));

        assertThat(boardGameRepository.count()).isZero();
    }

    @Test
    @DisplayName("옛 JSON 요청 형식은 더 이상 받지 않는다 (multipart 전용 → 400)")
    void create_jsonRequest_rejected() throws Exception {
        mockMvc.perform(post("/api/boardgames").with(loginAs(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isBadRequest());

        assertThat(boardGameRepository.count()).isZero();
    }

    // ───────────── 수정 · 소유권 ─────────────

    @Test
    @DisplayName("소유 관리자는 수정할 수 있다 (전체 교체 — 유튜브 링크를 생략하면 영상이 제거된다)")
    void update_owner_success() throws Exception {
        MvcResult created = mockMvc.perform(createBoardGame(validJson()).with(loginAs(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.youtubeVideoId").value(YOUTUBE_ID))
                .andReturn();
        long id = idOf(created);

        mockMvc.perform(updateBoardGame(id, json("Carcassonne 2", 1, 6, false, true, 0, null, false))
                        .with(loginAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Carcassonne 2"))
                .andExpect(jsonPath("$.data.maxPlayers").value(6))
                .andExpect(jsonPath("$.data.offlineAvailable").value(false))
                .andExpect(jsonPath("$.data.onlineAvailable").value(true))
                .andExpect(jsonPath("$.data.stock").value(0))
                .andExpect(jsonPath("$.data.youtubeVideoId").isEmpty())
                .andExpect(jsonPath("$.data.owner.id").value(admin.getId()));
    }

    @Test
    @DisplayName("⭐ 다른 ADMIN 은 수정 403, SUPER_ADMIN 도 남의 게임은 403 — 데이터는 그대로")
    void update_notOwner_forbidden() throws Exception {
        BoardGame game = saveOwnedGame("Catan", admin);

        for (Member intruder : new Member[]{otherAdmin, superAdmin}) {
            mockMvc.perform(updateBoardGame(game.getId(), json("Hacked", 2, 4, true, false, 1, null, false))
                            .with(loginAs(intruder)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("본인이 등록한 게임만 관리할 수 있습니다."));
        }

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.name").value("Catan"));
    }

    @Test
    @DisplayName("등록 관리자가 없는(레거시) 게임은 SUPER_ADMIN 을 포함해 아무도 수정할 수 없다")
    void update_legacyGame_forbidden() throws Exception {
        BoardGame legacy = boardGameRepository.save(BoardGame.create("Old", 2, 4, 30, Difficulty.EASY, "옛날"));

        for (Member member : new Member[]{admin, superAdmin}) {
            mockMvc.perform(updateBoardGame(legacy.getId(), json("Hacked", 2, 4, true, false, 1, null, false))
                            .with(loginAs(member)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("없는 게임 수정은 404")
    void update_notFound() throws Exception {
        mockMvc.perform(updateBoardGame(999999L, validJson()).with(loginAs(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정 시 검증 실패는 400 이고 기존 값은 그대로다")
    void update_invalidInput_keepsData() throws Exception {
        BoardGame game = saveOwnedGame("Catan", admin);

        mockMvc.perform(updateBoardGame(game.getId(), json("X", 5, 2, true, false, 1, null, false))
                        .with(loginAs(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(updateBoardGame(game.getId(), json("X", 2, 4, false, false, 0, null, false))
                        .with(loginAs(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("온라인·오프라인 중 하나 이상 선택해야 합니다."));

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.name").value("Catan"));
    }

    // ───────────── 이미지 교체·제거 ─────────────

    @Test
    @DisplayName("새 이미지로 교체하면 응답 URL 이 바뀌고 옛 파일은 커밋 후 삭제된다")
    void update_replaceImage_deletesOldFile() throws Exception {
        MvcResult created = createWithImage(admin);
        long id = idOf(created);
        String oldUrl = imageUrlOf(created);

        MvcResult updated = mockMvc.perform(updateBoardGame(id, validJson(), ImageFixtures.png("image"))
                        .with(loginAs(admin)))
                .andExpect(status().isOk())
                .andReturn();
        String newUrl = imageUrlOf(updated);

        assertThat(newUrl).isNotEqualTo(oldUrl).endsWith(".png");
        mockMvc.perform(get(newUrl)).andExpect(status().isOk());
        mockMvc.perform(get(oldUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("removeImage=true 면 이미지가 제거되고 옛 파일이 삭제된다")
    void update_removeImage() throws Exception {
        MvcResult created = createWithImage(admin);
        long id = idOf(created);
        String oldUrl = imageUrlOf(created);

        mockMvc.perform(updateBoardGame(id, json("Carcassonne", 2, 5, true, true, 3, null, true))
                        .with(loginAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").isEmpty());

        mockMvc.perform(get(oldUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미지 파트가 없고 removeImage 도 아니면 이미지는 그대로다")
    void update_keepsImage() throws Exception {
        MvcResult created = createWithImage(admin);
        String url = imageUrlOf(created);

        mockMvc.perform(updateBoardGame(idOf(created), json("Renamed", 2, 5, true, true, 3, null, false))
                        .with(loginAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(url));

        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 이미지와 removeImage=true 를 함께 보내면 400, 기존 이미지는 그대로다")
    void update_imageAndRemove_rejected() throws Exception {
        MvcResult created = createWithImage(admin);
        String url = imageUrlOf(created);

        mockMvc.perform(updateBoardGame(idOf(created), json("Carcassonne", 2, 5, true, true, 3, null, true),
                        ImageFixtures.png("image")).with(loginAs(admin)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    // ───────────── 목록 mine ─────────────

    @Test
    @DisplayName("mine=true: 내가 등록한 게임만(숨김 포함), USER 는 403, 기본 목록은 숨기지 않은 전체")
    void list_mine() throws Exception {
        saveOwnedGame("Mine-visible", admin);
        BoardGame hidden = saveOwnedGame("Mine-hidden", admin);
        hidden.hide();
        boardGameRepository.save(hidden);
        saveOwnedGame("Others", otherAdmin);
        boardGameRepository.save(BoardGame.create("Legacy", 2, 4, 30, Difficulty.EASY, "옛날"));

        mockMvc.perform(get("/api/boardgames").param("mine", "true").with(loginAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name").value(contains("Mine-visible", "Mine-hidden")))
                .andExpect(jsonPath("$.data[1].visible").value(false));

        mockMvc.perform(get("/api/boardgames").param("mine", "true").with(loginAs(otherAdmin)))
                .andExpect(jsonPath("$.data[*].name").value(contains("Others")));

        // SUPER_ADMIN 도 자기 게임만 (남의 게임·레거시는 안 나온다)
        mockMvc.perform(get("/api/boardgames").param("mine", "true").with(loginAs(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/boardgames").param("mine", "true").with(loginAs(normalUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));

        // 기본 목록: 비로그인·관리자 모두 숨김 게임 제외
        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Mine-visible", "Others", "Legacy")));
        mockMvc.perform(get("/api/boardgames").with(loginAs(admin)))
                .andExpect(jsonPath("$.data[*].name").value(contains("Mine-visible", "Others", "Legacy")));
    }

    @Test
    @DisplayName("mine=true 에 다른 필터를 함께 쓰면 AND")
    void list_mine_withFilters() throws Exception {
        boardGameRepository.save(BoardGame.create(new BoardGame.Details(
                "Online-mine", 2, 4, 30, Difficulty.EASY, "설명", false, true, 0), admin));
        saveOwnedGame("Offline-mine", admin);

        mockMvc.perform(get("/api/boardgames").param("mine", "true").param("playMode", "ONLINE")
                        .with(loginAs(admin)))
                .andExpect(jsonPath("$.data[*].name").value(contains("Online-mine")));
    }
}
