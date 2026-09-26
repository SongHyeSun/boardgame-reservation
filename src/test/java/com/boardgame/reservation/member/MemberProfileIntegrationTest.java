package com.boardgame.reservation.member;

import com.boardgame.reservation.global.file.FileStorage;
import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.event.AdminRequestedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.support.ImageFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.boardgame.reservation.support.MultipartTestUtils.signup;
import static com.boardgame.reservation.support.MultipartTestUtils.updateMe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 정보 API(조회·수정·비밀번호·관리자 신청)를 Security 필터 체인 + H2 + 실제 LocalFileStorage 로 검증.
 * 세션은 서블릿 HttpSession(MockHttpSession) — Redis 없이 돌아간다.
 */
@SpringBootTest
@RecordApplicationEvents
class MemberProfileIntegrationTest {

    private static final String EMAIL = "hyeseon@test.com";
    private static final String PASSWORD = "password123";

    @Autowired
    WebApplicationContext context;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    FileStorage fileStorage;
    @Autowired
    ApplicationEvents applicationEvents;

    MockMvc mockMvc;
    MockHttpSession session;
    final List<String> storedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mockMvc.perform(signup("""
                        {"email":"%s","password":"%s","nickname":"혜선","name":"홍혜선",
                         "birthDate":"1995-05-01","affiliation":"동아리","job":"개발자","bio":"안녕하세요"}
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
        session = login(EMAIL, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        storedKeys.forEach(fileStorage::delete);
        memberRepository.deleteAll();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 수정 요청 data JSON. avatarType 과 removeImage 만 바꿔가며 쓴다 */
    private static String profile(String avatarType, String extraFields) {
        return """
                {"nickname":"새닉네임","name":"김철수","avatarType":"%s"%s}
                """.formatted(avatarType, extraFields);
    }

    /** 응답 JSON 의 imageUrl 에서 key 를 꺼내 정리 대상으로 기억한다 */
    private String rememberKey(String responseJson) {
        String url = JsonPath.read(responseJson, "$.data.avatar.imageUrl");
        String key = url.substring("/api/files/".length());
        storedKeys.add(key);
        return key;
    }

    private String performUpdate(String dataJson, MockMultipartFile... files) throws Exception {
        return mockMvc.perform(updateMe(dataJson, files).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ───────────── 조회 ─────────────

    @Test
    @DisplayName("GET /me: 가입한 프로필·아바타·신청 상태가 내려온다")
    void me_includesProfile() throws Exception {
        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value("홍혜선"))
                .andExpect(jsonPath("$.data.birthDate").value("1995-05-01"))
                .andExpect(jsonPath("$.data.affiliation").value("동아리"))
                .andExpect(jsonPath("$.data.job").value("개발자"))
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"))
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.avatar.emoji").value("🎲"))
                .andExpect(jsonPath("$.data.adminRequestStatus").value("NONE"));
    }

    // ───────────── 수정 ─────────────

    @Test
    @DisplayName("PUT /me: 수정 내용이 응답과 이후 GET /me 에 바로 반영된다(세션 캐시가 아니라 DB 기준)")
    void update_reflectedImmediately() throws Exception {
        mockMvc.perform(updateMe(profile("EMOJI", """
                        ,"birthDate":"2000-02-03","affiliation":"새 소속","avatarEmoji":"🦊"
                        """).trim()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.name").value("김철수"))
                .andExpect(jsonPath("$.data.birthDate").value("2000-02-03"))
                .andExpect(jsonPath("$.data.affiliation").value("새 소속"))
                // 보내지 않은 선택 값은 지워진다(전체 교체)
                .andExpect(jsonPath("$.data.job").doesNotExist())
                .andExpect(jsonPath("$.data.bio").doesNotExist())
                .andExpect(jsonPath("$.data.avatar.emoji").value("🦊"))
                // 이메일은 바꿀 수 없다
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    @DisplayName("PUT /me: 로그인하지 않으면 401")
    void update_unauthenticated() throws Exception {
        mockMvc.perform(updateMe(profile("EMOJI", "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /me: 필수값 누락·길이 초과는 400 이고 기존 정보는 그대로")
    void update_validation() throws Exception {
        String[] invalid = {
                """
                {"nickname":"새닉네임","avatarType":"EMOJI"}""",                       // name 없음
                """
                {"nickname":"새닉네임","name":"김철수"}""",                              // avatarType 없음
                """
                {"nickname":"a","name":"김철수","avatarType":"EMOJI"}""",              // 닉네임 1자
                """
                {"nickname":"새닉네임","name":"김철수","avatarType":"EMOJI","bio":"%s"}""".formatted("가".repeat(101)),
                """
                {"nickname":"새닉네임","name":"김철수","avatarType":"EMOJI","birthDate":"2999-01-01"}""",
                """
                {"nickname":"새닉네임","name":"김철수","avatarType":"ROBOT"}"""          // 알 수 없는 타입
        };
        for (String json : invalid) {
            mockMvc.perform(updateMe(json).session(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(jsonPath("$.data.nickname").value("혜선"))
                .andExpect(jsonPath("$.data.bio").value("안녕하세요"));
    }

    @Test
    @DisplayName("PUT /me: 허용 목록 밖 이모지는 400 INVALID_AVATAR")
    void update_invalidEmoji() throws Exception {
        mockMvc.perform(updateMe(profile("EMOJI", ",\"avatarEmoji\":\"😀\"")).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아바타 설정이 올바르지 않습니다."));
    }

    // ───────────── 아바타 전환 ─────────────

    @Test
    @DisplayName("아바타 흐름: 이모지 → 이미지 업로드 → 이모지로 전환(이미지 보존) → 업로드 없이 다시 이미지 → 이미지 제거")
    void avatarFlow() throws Exception {
        // 1) 이미지 업로드 → IMAGE, 로그인 없이 받을 수 있는 URL
        String uploaded = performUpdate(profile("IMAGE", ""), ImageFixtures.png("image"));
        String key = rememberKey(uploaded);
        assertThat(JsonPath.<String>read(uploaded, "$.data.avatar.type")).isEqualTo("IMAGE");
        mockMvc.perform(get("/api/files/" + key))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ImageFixtures.pngBytes()));

        // 2) 이모지로 전환 — 이미지는 지우지 않으므로 imageUrl 이 남아 있다
        mockMvc.perform(updateMe(profile("EMOJI", ",\"avatarEmoji\":\"🐱\"")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.avatar.emoji").value("🐱"))
                .andExpect(jsonPath("$.data.avatar.imageUrl").value("/api/files/" + key));
        mockMvc.perform(get("/api/files/" + key)).andExpect(status().isOk());

        // 3) 새 파일 없이 IMAGE 로 되돌리기 — 기존 이미지 사용
        mockMvc.perform(updateMe(profile("IMAGE", "")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar.type").value("IMAGE"))
                .andExpect(jsonPath("$.data.avatar.imageUrl").value("/api/files/" + key));

        // 4) 이미지 제거 → EMOJI + imageUrl 없음 + 파일 삭제
        mockMvc.perform(updateMe(profile("EMOJI", ",\"removeImage\":true")).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.avatar.imageUrl").doesNotExist());
        mockMvc.perform(get("/api/files/" + key)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미지를 교체하면 이전 파일은 커밋 후 삭제되고 새 파일이 서빙된다")
    void replaceImage_deletesOldFile() throws Exception {
        String first = performUpdate(profile("IMAGE", ""), ImageFixtures.png("image"));
        String oldKey = rememberKey(first);

        String second = performUpdate(profile("IMAGE", ""), ImageFixtures.jpeg("image"));
        String newKey = rememberKey(second);

        assertThat(newKey).isNotEqualTo(oldKey);
        mockMvc.perform(get("/api/files/" + oldKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/" + newKey))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ImageFixtures.jpegBytes()));
    }

    @Test
    @DisplayName("이미지 없이 avatarType=IMAGE 로 저장하면 400 INVALID_AVATAR, 아바타는 그대로")
    void imageTypeWithoutImage() throws Exception {
        mockMvc.perform(updateMe(profile("IMAGE", "")).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아바타 설정이 올바르지 않습니다."));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.nickname").value("혜선"));
    }

    @Test
    @DisplayName("새 이미지 + removeImage=true 를 같이 보내면 400, 기존 이미지는 유지")
    void imageAndRemoveTogether() throws Exception {
        String key = rememberKey(performUpdate(profile("IMAGE", ""), ImageFixtures.png("image")));

        mockMvc.perform(updateMe(profile("IMAGE", ",\"removeImage\":true"), ImageFixtures.jpeg("image")).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아바타 설정이 올바르지 않습니다."));

        mockMvc.perform(get("/api/files/" + key)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("잘못된 이미지(위조·5MB 초과)는 400 이고 기존 이미지와 프로필은 그대로")
    void invalidImage_keepsExisting() throws Exception {
        // 첫 업로드 성공: 닉네임이 "새닉네임" 으로 바뀌고 이미지가 생긴다
        String key = rememberKey(performUpdate(profile("IMAGE", ""), ImageFixtures.png("image")));
        // 이후 실패하는 요청은 다른 닉네임을 실어 보내, 실패 시 프로필이 롤백되어 바뀌지 않는지 확인한다
        String changedNickname = profile("IMAGE", "").replace("새닉네임", "실패닉네임");

        MockMultipartFile fake = new MockMultipartFile("image", "evil.png", "image/png", "not an image".getBytes());
        mockMvc.perform(updateMe(changedNickname, fake).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 파일입니다."));

        byte[] big = Arrays.copyOf(ImageFixtures.jpegBytes(), 5 * 1024 * 1024 + 1);
        mockMvc.perform(updateMe(changedNickname, new MockMultipartFile("image", "big.jpg", "image/jpeg", big))
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지는 5MB 이하만 업로드할 수 있습니다."));

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(jsonPath("$.data.avatar.imageUrl").value("/api/files/" + key))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
        mockMvc.perform(get("/api/files/" + key)).andExpect(status().isOk());
    }

    // ───────────── 비밀번호 ─────────────

    @Test
    @DisplayName("비밀번호 변경 성공: 새 비밀번호로 로그인되고 옛 비밀번호는 401")
    void changePassword_success() throws Exception {
        mockMvc.perform(patch("/api/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"new-password-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        login(EMAIL, "new-password-1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호 불일치 → 400 INVALID_PASSWORD, 기존 비밀번호 유지")
    void changePassword_wrongCurrent() throws Exception {
        mockMvc.perform(patch("/api/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"new-password-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 일치하지 않습니다."));

        login(EMAIL, PASSWORD);
    }

    @Test
    @DisplayName("비밀번호 변경: 새 비밀번호 규칙(8~64자)·필수값 위반은 400, 로그인 없이는 401")
    void changePassword_validationAndAuth() throws Exception {
        mockMvc.perform(patch("/api/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"new-password-1"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"new-password-1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ───────────── 관리자 신청 ─────────────

    @Test
    @DisplayName("관리자 신청: NONE → PENDING + 이벤트 발행, 이미 PENDING 이면 409")
    void requestAdmin() throws Exception {
        mockMvc.perform(post("/api/members/me/admin-request").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRequestStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.role").value("USER"));
        Long memberId = memberRepository.findByEmail(EMAIL).orElseThrow().getId();
        assertThat(applicationEvents.stream(AdminRequestedEvent.class).toList())
                .containsExactly(new AdminRequestedEvent(memberId));

        mockMvc.perform(post("/api/members/me/admin-request").session(session))
                .andExpect(status().isConflict());
        assertThat(applicationEvents.stream(AdminRequestedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("관리자 신청: 거절(REJECTED)된 뒤에는 재신청할 수 있다")
    void requestAdmin_afterRejected() throws Exception {
        Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
        member.requestAdmin(java.time.LocalDateTime.now());
        member.rejectAdmin();
        memberRepository.save(member);

        mockMvc.perform(post("/api/members/me/admin-request").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRequestStatus").value("PENDING"));
        assertThat(memberRepository.findByEmail(EMAIL).orElseThrow().getAdminRequestStatus())
                .isEqualTo(AdminRequestStatus.PENDING);
    }

    @Test
    @DisplayName("관리자 신청: 이미 ADMIN 이면 409, 로그인 없이는 401")
    void requestAdmin_adminAndUnauthenticated() throws Exception {
        memberRepository.save(Member.createAdmin("admin@test.com", passwordEncoder.encode(PASSWORD), "관리자"));
        MockHttpSession adminSession = login("admin@test.com", PASSWORD);

        mockMvc.perform(post("/api/members/me/admin-request").session(adminSession))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/members/me/admin-request"))
                .andExpect(status().isUnauthorized());
    }
}
