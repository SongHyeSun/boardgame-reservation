package com.boardgame.reservation.member;

import com.boardgame.reservation.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.boardgame.reservation.support.MultipartTestUtils.signup;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입 → 로그인(세션) → 내 정보 → 로그아웃 전체 흐름을 실제 Security 필터 체인까지 태워서 검증.
 * DB는 src/test/resources/application.yml 의 H2 사용.
 */
@SpringBootTest
class AuthFlowIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberRepository memberRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteAll();
    }

    private static final String SIGNUP_BODY = """
            {"email":"hyeseon@test.com","password":"password123","nickname":"혜선","name":"홍혜선"}
            """;

    private MockHttpSession signupAndLogin() throws Exception {
        mockMvc.perform(signup(SIGNUP_BODY))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hyeseon@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("회원가입 성공 시 201과 회원 정보를 반환하고, 비밀번호는 응답에 없다")
    void signup_success() throws Exception {
        mockMvc.perform(signup(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("hyeseon@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("혜선"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("같은 이메일(대소문자 달라도)로 다시 가입하면 409")
    void signup_duplicateEmail() throws Exception {
        mockMvc.perform(signup(SIGNUP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("""
                        {"email":"HyeSeon@Test.com","password":"password123","nickname":"다른사람","name":"다른사람"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
    }

    @Test
    @DisplayName("입력값 검증 실패 시 400")
    void signup_invalidInput() throws Exception {
        mockMvc.perform(signup("""
                        {"email":"not-an-email","password":"short","nickname":"a","name":"홍혜선"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("로그인 → 세션으로 /me 조회 → 로그아웃 → 같은 세션으로 /me 는 401")
    void login_me_logout_flow() throws Exception {
        MockHttpSession session = signupAndLogin();

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("hyeseon@test.com"));

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401")
    void login_wrongPassword() throws Exception {
        mockMvc.perform(signup(SIGNUP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hyeseon@test.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 비밀번호 틀림과 같은 401 메시지")
    void login_unknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@test.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("로그인 없이 /me 호출 시 401 + 공통 응답 포맷")
    void me_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    @DisplayName("일반 회원(USER)이 보드게임 등록 API 호출 시 403 — 다음 브랜치의 ADMIN 규칙 선검증")
    void user_cannotAccessAdminApi() throws Exception {
        MockHttpSession session = signupAndLogin();

        mockMvc.perform(post("/api/boardgames").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }
}
