package com.boardgame.reservation.member;

import com.boardgame.reservation.global.file.FileStorage;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.boardgame.reservation.support.MultipartTestUtils.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** multipart 회원가입: 프로필·아바타(이모지/이미지)·관리자 신청·검증 실패. 파일은 test yml 의 build/test-uploads 에 쓰고 테스트 후 지운다. */
@SpringBootTest
@RecordApplicationEvents
class SignupIntegrationTest {

    private static final String BASE = """
            {"email":"hyeseon@test.com","password":"password123","nickname":"혜선","name":"홍혜선"%s}
            """;

    @Autowired
    WebApplicationContext context;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    FileStorage fileStorage;
    @Autowired
    ApplicationEvents applicationEvents;

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
        memberRepository.deleteAll();
    }

    /** BASE 에 추가 필드(예: ,"bio":"...")를 붙인 data JSON */
    private static String data(String extraFields) {
        return BASE.formatted(extraFields);
    }

    private String rememberKey(String responseJson) {
        String url = JsonPath.read(responseJson, "$.data.avatar.imageUrl");
        String key = url.substring("/api/files/".length());
        storedKeys.add(key);
        return key;
    }

    // ───────────── 성공 ─────────────

    @Test
    @DisplayName("이미지 없이 가입: 201, 기본 이모지 아바타(🎲), 신청 없음(NONE), role USER")
    void signup_defaults() throws Exception {
        mockMvc.perform(signup(data("")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("홍혜선"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.avatar.emoji").value("🎲"))
                .andExpect(jsonPath("$.data.avatar.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.adminRequestStatus").value("NONE"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        assertThat(applicationEvents.stream(AdminRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("선택 프로필(생년월일·소속·직업·한줄소개)과 이모지 선택이 저장되고 응답에 나온다")
    void signup_withProfileAndEmoji() throws Exception {
        mockMvc.perform(signup(data("""
                        ,"birthDate":"1995-05-01","affiliation":"보드게임 동아리","job":"개발자","bio":"잘 부탁드려요","avatarEmoji":"🦊"
                        """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.birthDate").value("1995-05-01"))
                .andExpect(jsonPath("$.data.affiliation").value("보드게임 동아리"))
                .andExpect(jsonPath("$.data.job").value("개발자"))
                .andExpect(jsonPath("$.data.bio").value("잘 부탁드려요"))
                .andExpect(jsonPath("$.data.avatar.type").value("EMOJI"))
                .andExpect(jsonPath("$.data.avatar.emoji").value("🦊"));

        Member saved = memberRepository.findByEmail("hyeseon@test.com").orElseThrow();
        assertThat(saved.getAffiliation()).isEqualTo("보드게임 동아리");
        assertThat(saved.getAvatarEmoji()).isEqualTo("🦊");
    }

    @Test
    @DisplayName("이미지와 함께 가입: 아바타 타입 IMAGE, imageUrl 로 로그인 없이 이미지를 받을 수 있다")
    void signup_withImage() throws Exception {
        String json = mockMvc.perform(signup(data(""), ImageFixtures.png("image")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatar.type").value("IMAGE"))
                .andExpect(jsonPath("$.data.avatar.imageUrl").value(startsWith("/api/files/avatars/")))
                .andReturn().getResponse().getContentAsString();
        String key = rememberKey(json);

        assertThat(memberRepository.findByEmail("hyeseon@test.com").orElseThrow().getAvatarImageKey()).isEqualTo(key);
        mockMvc.perform(get("/api/files/" + key))
                .andExpect(status().isOk())
                .andExpect(content().bytes(ImageFixtures.pngBytes()));
    }

    @Test
    @DisplayName("requestAdmin=true: role 은 USER 그대로, 신청 상태 PENDING, AdminRequestedEvent(memberId) 발행")
    void signup_requestAdmin() throws Exception {
        String json = mockMvc.perform(signup(data(",\"requestAdmin\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.adminRequestStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long memberId = ((Number) JsonPath.read(json, "$.data.id")).longValue();

        Member saved = memberRepository.findById(memberId).orElseThrow();
        assertThat(saved.getAdminRequestedAt()).isNotNull();
        assertThat(applicationEvents.stream(AdminRequestedEvent.class).toList())
                .containsExactly(new AdminRequestedEvent(memberId));
    }

    // ───────────── 실패: 입력 검증 ─────────────

    @Test
    @DisplayName("필수값(이름) 누락 → 400, 회원은 생기지 않는다")
    void signup_missingName() throws Exception {
        mockMvc.perform(signup("""
                        {"email":"hyeseon@test.com","password":"password123","nickname":"혜선"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(startsWith("name: ")));

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("이름 1자 / 소속 51자 / 한줄소개 101자 / 미래 생년월일 → 400")
    void signup_fieldRules() throws Exception {
        mockMvc.perform(signup(data("").replace("\"name\":\"홍혜선\"", "\"name\":\"홍\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signup(data(",\"affiliation\":\"" + "가".repeat(51) + "\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signup(data(",\"bio\":\"" + "가".repeat(101) + "\"")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signup(data(",\"birthDate\":\"2999-01-01\"")))
                .andExpect(status().isBadRequest());

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("data 파트가 없으면 400 (500 이 아님)")
    void signup_missingDataPart() throws Exception {
        mockMvc.perform(multipart("/api/auth/signup").file(ImageFixtures.jpeg("image")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("옛 방식(application/json 본문)으로 요청하면 400 (500 이 아님)")
    void signup_oldJsonFormat() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("data 파트의 Content-Type 이 application/json 이 아니면 400")
    void signup_dataPartWithoutJsonContentType() throws Exception {
        MockMultipartFile plain = new MockMultipartFile("data", "data", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                data("").getBytes());

        mockMvc.perform(multipart("/api/auth/signup").file(plain))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("허용 목록 밖 이모지 → 400 INVALID_AVATAR, 회원·파일 생성 없음")
    void signup_invalidEmoji() throws Exception {
        mockMvc.perform(signup(data(",\"avatarEmoji\":\"😀\""), ImageFixtures.jpeg("image")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("아바타 설정이 올바르지 않습니다."));

        assertThat(memberRepository.count()).isZero();
    }

    // ───────────── 실패: 이미지 ─────────────

    @Test
    @DisplayName("형식이 다른 파일(.jpg 이름, 내용은 텍스트) → 400 INVALID_FILE, 회원 생성 없음")
    void signup_fakeImage() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("image", "evil.jpg", "image/jpeg", "not an image".getBytes());

        mockMvc.perform(signup(data(""), fake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 파일입니다."));

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("확장자·Content-Type·시그니처가 서로 다르면 400 (png 내용을 .jpg 로)")
    void signup_signatureMismatch() throws Exception {
        MockMultipartFile mismatch = new MockMultipartFile("image", "a.jpg", "image/jpeg", ImageFixtures.pngBytes());

        mockMvc.perform(signup(data(""), mismatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 파일입니다."));

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("gif 등 허용되지 않은 형식 → 400")
    void signup_disallowedType() throws Exception {
        MockMultipartFile gif = new MockMultipartFile("image", "a.gif", "image/gif",
                "GIF89a".getBytes());

        mockMvc.perform(signup(data(""), gif))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5MB 초과 → 400 FILE_TOO_LARGE, 회원 생성 없음")
    void signup_tooLarge() throws Exception {
        byte[] big = Arrays.copyOf(ImageFixtures.jpegBytes(), 5 * 1024 * 1024 + 1);
        MockMultipartFile tooLarge = new MockMultipartFile("image", "big.jpg", "image/jpeg", big);

        mockMvc.perform(signup(data(""), tooLarge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미지는 5MB 이하만 업로드할 수 있습니다."));

        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("중복 이메일은 이미지가 있어도 409 이고, 이미지를 저장하지 않는다")
    void signup_duplicateWithImage() throws Exception {
        mockMvc.perform(signup(data(""))).andExpect(status().isCreated());

        mockMvc.perform(signup(data(""), ImageFixtures.jpeg("image")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

        assertThat(memberRepository.count()).isEqualTo(1);
    }
}
