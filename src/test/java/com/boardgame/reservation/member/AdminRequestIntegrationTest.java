package com.boardgame.reservation.member;

import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import com.boardgame.reservation.member.event.AdminRequestDecidedEvent;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Set;

import static com.boardgame.reservation.support.MultipartTestUtils.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 승인 API + 승인 후 기존 세션 무효화. 실제 Redis(indexed 세션 저장소) + Security 필터 체인 + H2.
 * 로그인은 SessionRepositoryFilter 를 태워 세션이 Redis 에 저장되게 한다(PartyApiIntegrationTest 의 세션 테스트와 같은 방식).
 */
@RecordApplicationEvents
class AdminRequestIntegrationTest extends RedisIntegrationTestSupport {

    private static final String PASSWORD = "password123";
    private static final String ADMIN_REQUESTS = "/api/admin/admin-requests";
    /** 검증에서 400 이 나는 보드게임 등록 본문(name 없음) — 인가는 통과했는지만 보려는 용도 */
    private static final String INVALID_BOARDGAME_BODY = """
            {"minPlayers":2,"maxPlayers":4,"playTime":30,"difficulty":"EASY"}
            """;

    @Autowired
    WebApplicationContext context;
    @Autowired
    SessionRepositoryFilter<?> sessionRepositoryFilter;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ApplicationEvents applicationEvents;

    MockMvc mockMvc;
    Cookie superAdminCookie;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(sessionRepositoryFilter)
                .apply(springSecurity())
                .build();
        memberRepository.save(Member.createSuperAdmin("root@test.com", passwordEncoder.encode(PASSWORD), "관리자"));
        superAdminCookie = login("root@test.com");
    }

    // ───────────── 헬퍼 ─────────────

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie[] cookies = result.getResponse().getCookies();
        assertThat(cookies).hasSize(1);
        return cookies[0];
    }

    /** 가입(requestAdmin 여부 선택) 후 memberId 반환 */
    private long signupMember(String email, boolean requestAdmin) throws Exception {
        mockMvc.perform(signup("""
                        {"email":"%s","password":"%s","nickname":"닉%s","name":"이름%s","affiliation":"동아리","job":"개발자","requestAdmin":%s}
                        """.formatted(email, PASSWORD, email.substring(0, 2), email.substring(0, 2), requestAdmin)))
                .andExpect(status().isCreated());
        return memberRepository.findByEmail(email).orElseThrow().getId();
    }

    private void approve(long memberId) throws Exception {
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", memberId).cookie(superAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private Member reload(long memberId) {
        return memberRepository.findById(memberId).orElseThrow();
    }

    // ───────────── 권한 ─────────────

    @Test
    @DisplayName("비로그인 401 / USER 403 / ADMIN 403 / SUPER_ADMIN 200 — 목록·승인·거절 모두 SUPER_ADMIN 전용")
    void authorization() throws Exception {
        long pendingId = signupMember("pending@test.com", true);
        Cookie userCookie = login("pending@test.com");
        memberRepository.save(Member.createAdmin("admin@test.com", passwordEncoder.encode(PASSWORD), "관리자2"));
        Cookie adminCookie = login("admin@test.com");

        mockMvc.perform(get(ADMIN_REQUESTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", pendingId)).andExpect(status().isUnauthorized());

        for (Cookie cookie : new Cookie[]{userCookie, adminCookie}) {
            mockMvc.perform(get(ADMIN_REQUESTS).cookie(cookie))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
            mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", pendingId).cookie(cookie))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/reject", pendingId).cookie(cookie))
                    .andExpect(status().isForbidden());
        }
        // 거부된 요청은 아무것도 바꾸지 않았다
        assertThat(reload(pendingId).getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(reload(pendingId).getRole()).isEqualTo(Role.USER);

        mockMvc.perform(get(ADMIN_REQUESTS).cookie(superAdminCookie)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("RoleHierarchy: SUPER_ADMIN 도 hasRole('ADMIN') API 를 통과한다 (USER 는 403)")
    void roleHierarchy() throws Exception {
        // 로그인 세션(실제 SUPER_ADMIN)
        mockMvc.perform(post("/api/boardgames").cookie(superAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isBadRequest());
        // 인가만 통과하면 본문 검증에서 400 — 403 이 아니라는 게 핵심
        mockMvc.perform(post("/api/boardgames").with(user("root").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/boardgames").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/boardgames").with(user("user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isForbidden());
        // 반대 방향(ADMIN 이 SUPER_ADMIN 전용 API)은 위 authorization 테스트에서 403 으로 검증
    }

    // ───────────── 목록 ─────────────

    @Test
    @DisplayName("목록에는 PENDING 만, 오래된 신청 순으로 필요한 필드만 내려온다")
    void list_pendingOnly() throws Exception {
        long firstId = signupMember("first@test.com", true);
        signupMember("none@test.com", false);
        long rejectedId = signupMember("rej@test.com", true);
        long secondId = signupMember("second@test.com", true);
        // 신청 시각을 명시적으로 벌려 정렬을 결정적으로 만든다
        setRequestedAt(firstId, LocalDateTime.of(2026, 9, 1, 10, 0));
        setRequestedAt(secondId, LocalDateTime.of(2026, 9, 2, 10, 0));
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/reject", rejectedId).cookie(superAdminCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_REQUESTS).cookie(superAdminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].memberId").value(firstId))
                .andExpect(jsonPath("$.data[0].email").value("first@test.com"))
                .andExpect(jsonPath("$.data[0].nickname").value("닉fi"))
                .andExpect(jsonPath("$.data[0].name").value("이름fi"))
                .andExpect(jsonPath("$.data[0].affiliation").value("동아리"))
                .andExpect(jsonPath("$.data[0].job").value("개발자"))
                .andExpect(jsonPath("$.data[0].requestedAt").exists())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[1].memberId").value(secondId));
    }

    private void setRequestedAt(long memberId, LocalDateTime at) {
        Member member = reload(memberId);
        // PENDING 을 유지한 채 시각만 바꾸기 위해 reject → 재신청 흐름을 도메인 메서드로 재현
        member.rejectAdmin();
        member.requestAdmin(at);
        memberRepository.save(member);
    }

    // ───────────── 승인 ─────────────

    @Test
    @DisplayName("승인: role ADMIN + APPROVED, AdminRequestDecidedEvent(memberId, true) 발행, 목록에서 사라진다")
    void approve_success() throws Exception {
        long memberId = signupMember("apply@test.com", true);

        approve(memberId);

        Member member = reload(memberId);
        assertThat(member.getRole()).isEqualTo(Role.ADMIN);
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.APPROVED);
        assertThat(applicationEvents.stream(AdminRequestDecidedEvent.class).toList())
                .containsExactly(new AdminRequestDecidedEvent(memberId, true));
        mockMvc.perform(get(ADMIN_REQUESTS).cookie(superAdminCookie))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("⭐ 승인 전에 만든 세션으로 /me → 401(세션 무효화), 재로그인하면 ADMIN 이고 ADMIN API 를 쓸 수 있다")
    void approve_invalidatesExistingSessions() throws Exception {
        long memberId = signupMember("apply@test.com", true);
        Cookie oldSession = login("apply@test.com");
        Cookie secondDevice = login("apply@test.com");
        Cookie bystander = signupAndLogin("other@test.com");

        mockMvc.perform(get("/api/members/me").cookie(oldSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));
        // 승인 전에는 ADMIN API 를 쓸 수 없다
        mockMvc.perform(post("/api/boardgames").cookie(oldSession)
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isForbidden());

        approve(memberId);

        // 기기 여러 대의 세션이 모두 끊긴다
        mockMvc.perform(get("/api/members/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/me").cookie(secondDevice)).andExpect(status().isUnauthorized());

        // 다른 회원·SUPER_ADMIN 의 세션은 그대로
        mockMvc.perform(get("/api/members/me").cookie(bystander)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN_REQUESTS).cookie(superAdminCookie)).andExpect(status().isOk());

        // 재로그인하면 새 권한
        Cookie fresh = login("apply@test.com");
        mockMvc.perform(get("/api/members/me").cookie(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.adminRequestStatus").value("APPROVED"));
        mockMvc.perform(post("/api/boardgames").cookie(fresh)
                        .contentType(MediaType.APPLICATION_JSON).content(INVALID_BOARDGAME_BODY))
                .andExpect(status().isBadRequest());
        // 승인된 ADMIN 은 SUPER_ADMIN 전용 API 는 여전히 403
        mockMvc.perform(get(ADMIN_REQUESTS).cookie(fresh)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PENDING 이 아니면(신청 없음·이미 승인) 승인 409, 없는 회원은 404")
    void approve_invalidTargets() throws Exception {
        long noneId = signupMember("none@test.com", false);
        long pendingId = signupMember("pend@test.com", true);
        approve(pendingId);

        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", noneId).cookie(superAdminCookie))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", pendingId).cookie(superAdminCookie))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/approve", 999_999L).cookie(superAdminCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
        assertThat(reload(noneId).getRole()).isEqualTo(Role.USER);
    }

    // ───────────── 거절 ─────────────

    @Test
    @DisplayName("거절: REJECTED, role·세션은 그대로, 이벤트(memberId, false) 발행, 이후 재신청 가능")
    void reject_success() throws Exception {
        long memberId = signupMember("apply@test.com", true);
        Cookie session = login("apply@test.com");

        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/reject", memberId).cookie(superAdminCookie))
                .andExpect(status().isOk());

        Member member = reload(memberId);
        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.REJECTED);
        assertThat(applicationEvents.stream(AdminRequestDecidedEvent.class).toList())
                .containsExactly(new AdminRequestDecidedEvent(memberId, false));
        // 세션은 끊기지 않는다 → 그대로 /me 와 재신청이 가능
        mockMvc.perform(get("/api/members/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRequestStatus").value("REJECTED"));
        mockMvc.perform(post("/api/members/me/admin-request").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRequestStatus").value("PENDING"));
    }

    @Test
    @DisplayName("거절도 PENDING 만 가능(409), 없는 회원은 404")
    void reject_invalidTargets() throws Exception {
        long noneId = signupMember("none@test.com", false);

        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/reject", noneId).cookie(superAdminCookie))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(ADMIN_REQUESTS + "/{id}/reject", 999_999L).cookie(superAdminCookie))
                .andExpect(status().isNotFound());
    }

    // ───────────── 세션 저장소 ─────────────

    @Test
    @DisplayName("승인 후 principal 인덱스(email 기준)에서도 그 회원의 세션이 사라진다")
    void approve_removesPrincipalIndexEntries() throws Exception {
        long memberId = signupMember("apply@test.com", true);
        login("apply@test.com");
        assertThat(sessionIndexMembers("apply@test.com")).hasSize(1);

        approve(memberId);

        assertThat(sessionIndexMembers("apply@test.com")).isEmpty();
    }

    private Cookie signupAndLogin(String email) throws Exception {
        signupMember(email, false);
        return login(email);
    }

    /**
     * principal 인덱스 셋에 남아 있는 값들(직렬화 헤더 포함 문자열). 인덱스 키가 없으면 빈 집합.
     * (삭제된 세션의 본문 해시는 indexed 저장소가 만료 이벤트용으로 잠시 남겨 두므로 키 존재가 아니라 인덱스·401 로 검증한다)
     */
    private Set<String> sessionIndexMembers(String email) {
        Set<String> indexKeys = redisTemplate.keys("spring:session:index:*:" + email);
        if (indexKeys == null || indexKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> members = redisTemplate.opsForSet().members(indexKeys.iterator().next());
        return members == null ? Set.of() : members;
    }
}
