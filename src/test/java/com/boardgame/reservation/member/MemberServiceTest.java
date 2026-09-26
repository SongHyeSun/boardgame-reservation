package com.boardgame.reservation.member;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.global.file.FileStorage;
import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.AvatarType;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.ChangePasswordRequest;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.dto.SignupRequest;
import com.boardgame.reservation.member.dto.UpdateProfileRequest;
import com.boardgame.reservation.member.event.AdminRequestedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.member.service.MemberService;
import com.boardgame.reservation.support.ImageFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** DB/스프링 없이 서비스 로직만 빠르게 검증하는 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    /** 2026-09-27 12:00 (Asia/Seoul) 로 고정 */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T03:00:00Z"), SEOUL);

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    FileStorage fileStorage;
    @Mock
    ApplicationEventPublisher eventPublisher;

    MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, fileStorage, eventPublisher, CLOCK);
    }

    private static SignupRequest request(String email, String emoji, boolean requestAdmin) {
        return new SignupRequest(email, "password123", "혜선", "홍혜선", null, null, null, null, emoji, requestAdmin);
    }

    private static SignupRequest basicRequest() {
        return request("hyeseon@test.com", null, false);
    }

    /** save 는 넘겨받은 엔티티를 id 를 붙여 그대로 돌려준다(IDENTITY 채번 흉내) */
    private void stubSaveWithId(long id) {
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> {
            Member member = inv.getArgument(0);
            ReflectionTestUtils.setField(member, "id", id);
            return member;
        });
    }

    private Member savedMember() {
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        return captor.getValue();
    }

    // ───────────── 기존 동작 ─────────────

    @Test
    @DisplayName("가입 시 이메일은 소문자로 정규화되고, 비밀번호는 암호화되어 저장된다")
    void signup_encodesPassword_andNormalizesEmail() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("{bcrypt}encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        memberService.signup(request(" HyeSeon@Test.com ", null, false), null);

        Member saved = savedMember();
        assertThat(saved.getEmail()).isEqualTo("hyeseon@test.com");
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(saved.getRole().name()).isEqualTo("USER");
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 DUPLICATE_EMAIL 예외, 저장하지 않고 파일도 올리지 않는다")
    void signup_duplicateEmail() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup(basicRequest(), ImageFixtures.jpeg("image")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("동시 가입으로 exists 체크를 통과해도 UNIQUE 위반은 DUPLICATE_EMAIL로 변환된다")
    void signup_uniqueViolation_convertedToDuplicate() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("{bcrypt}encoded");
        given(memberRepository.save(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> memberService.signup(basicRequest(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }

    // ───────────── 프로필 · 아바타 ─────────────

    @Test
    @DisplayName("프로필 값은 trim 되고 비어 있는 선택 값은 null, 이모지는 없으면 기본값")
    void signup_profileNormalization() {
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));
        SignupRequest request = new SignupRequest("hyeseon@test.com", "password123", " 혜선 ", " 홍혜선 ",
                LocalDate.of(1995, 5, 1), "  동아리  ", "   ", "", "  ", false);

        memberService.signup(request, null);

        Member saved = savedMember();
        assertThat(saved.getNickname()).isEqualTo("혜선");
        assertThat(saved.getName()).isEqualTo("홍혜선");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 1));
        assertThat(saved.getAffiliation()).isEqualTo("동아리");
        assertThat(saved.getJob()).isNull();
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(saved.getAvatarEmoji()).isEqualTo("🎲");
    }

    @Test
    @DisplayName("허용 목록의 이모지는 그대로 저장된다")
    void signup_allowedEmoji() {
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        memberService.signup(request("hyeseon@test.com", "🦊", false), null);
        assertThat(savedMember().getAvatarEmoji()).isEqualTo("🦊");
    }

    @Test
    @DisplayName("허용 목록 밖 이모지는 INVALID_AVATAR, 이미지가 함께 와도 파일을 저장하지 않는다")
    void signup_invalidEmoji() {
        assertThatThrownBy(() -> memberService.signup(request("hyeseon@test.com", "😀", false), ImageFixtures.jpeg("image")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AVATAR);

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("이미지가 있으면 avatars 에 저장하고 아바타 타입은 IMAGE")
    void signup_withImage() {
        MockMultipartFile image = ImageFixtures.png("image");
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(fileStorage.store(image, "avatars")).willReturn("avatars/00000000-0000-0000-0000-000000000000.png");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        MemberResponse response = memberService.signup(basicRequest(), image);

        Member saved = savedMember();
        assertThat(saved.getAvatarType()).isEqualTo(AvatarType.IMAGE);
        assertThat(saved.getAvatarImageKey()).isEqualTo("avatars/00000000-0000-0000-0000-000000000000.png");
        assertThat(response.avatar().imageUrl())
                .isEqualTo("/api/files/avatars/00000000-0000-0000-0000-000000000000.png");
        verify(fileStorage, never()).delete(any());
    }

    @Test
    @DisplayName("이미지 검증 실패(INVALID_FILE)는 그대로 전달되고 회원은 저장되지 않는다")
    void signup_invalidImage() {
        MockMultipartFile image = ImageFixtures.jpeg("image");
        given(fileStorage.store(image, "avatars")).willThrow(new BusinessException(ErrorCode.INVALID_FILE));

        assertThatThrownBy(() -> memberService.signup(basicRequest(), image))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("파일을 올린 뒤 DB 저장이 실패하면 방금 올린 파일을 삭제한다")
    void signup_saveFails_deletesNewFile() {
        MockMultipartFile image = ImageFixtures.jpeg("image");
        String key = "avatars/00000000-0000-0000-0000-000000000000.jpg";
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(fileStorage.store(image, "avatars")).willReturn(key);
        given(memberRepository.save(any(Member.class))).willThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> memberService.signup(basicRequest(), image))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

        verify(fileStorage).delete(key);
    }

    // ───────────── 관리자 신청 ─────────────

    @Test
    @DisplayName("requestAdmin=true 면 USER + PENDING + 신청 시각(Asia/Seoul 기준)이 기록되고 AdminRequestedEvent(memberId) 발행")
    void signup_requestAdmin() {
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        stubSaveWithId(42L);

        MemberResponse response = memberService.signup(request("hyeseon@test.com", null, true), null);

        Member saved = savedMember();
        assertThat(saved.getRole().name()).isEqualTo("USER");
        assertThat(saved.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(saved.getAdminRequestedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 12, 0));
        assertThat(response.adminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        verify(eventPublisher).publishEvent(new AdminRequestedEvent(42L));
    }

    @Test
    @DisplayName("requestAdmin=false 면 NONE 이고 이벤트를 발행하지 않는다")
    void signup_noAdminRequest() {
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        memberService.signup(basicRequest(), null);

        Member saved = savedMember();
        assertThat(saved.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.NONE);
        assertThat(saved.getAdminRequestedAt()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    // ───────────── 내 정보 수정 ─────────────

    private static final String OLD_KEY = "avatars/11111111-1111-1111-1111-111111111111.png";
    private static final String NEW_KEY = "avatars/22222222-2222-2222-2222-222222222222.jpg";

    /** id=1 인 기존 회원(이름·소속·직업·한줄소개 채워짐)을 findById 에 등록. imageKey 가 있으면 IMAGE 아바타 */
    private Member existingMember(String imageKey) {
        Member member = Member.register("user@test.com", "encoded", "닉네임",
                new Member.Profile("홍길동", LocalDate.of(1990, 1, 1), "동아리", "개발자", "안녕"),
                null, imageKey, null);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        return member;
    }

    private static UpdateProfileRequest update(AvatarType type, String emoji, Boolean removeImage) {
        return new UpdateProfileRequest(" 새닉네임 ", "김철수", null, null, null, null, type, emoji, removeImage);
    }

    private void assertInvalidAvatar(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AVATAR);
    }

    @Test
    @DisplayName("수정: 닉네임·이름은 trim 되어 바뀌고, 비운 선택 값(생년월일·소속·직업·한줄소개)은 지워진다")
    void updateProfile_replacesFields() {
        Member member = existingMember(null);

        MemberResponse response = memberService.updateProfile(1L, update(AvatarType.EMOJI, null, null), null);

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getBirthDate()).isNull();
        assertThat(member.getAffiliation()).isNull();
        assertThat(member.getJob()).isNull();
        assertThat(member.getBio()).isNull();
        assertThat(response.nickname()).isEqualTo("새닉네임");
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: 이모지를 바꾸면 EMOJI 로 저장, 이모지를 비우면 현재 이모지 유지")
    void updateProfile_emoji() {
        Member member = existingMember(null);

        memberService.updateProfile(1L, update(AvatarType.EMOJI, "🐉", null), null);
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarEmoji()).isEqualTo("🐉");

        memberService.updateProfile(1L, update(AvatarType.EMOJI, null, null), null);
        assertThat(member.getAvatarEmoji()).isEqualTo("🐉");
    }

    @Test
    @DisplayName("수정: 허용 목록 밖 이모지는 INVALID_AVATAR, 회원 정보는 바뀌지 않는다")
    void updateProfile_invalidEmoji() {
        Member member = existingMember(null);

        assertInvalidAvatar(() -> memberService.updateProfile(1L, update(AvatarType.EMOJI, "😀", null), null));

        assertThat(member.getNickname()).isEqualTo("닉네임");
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: 새 이미지를 올리면 avatarType 값과 무관하게 IMAGE, 이전 파일은 삭제(커밋 후)")
    void updateProfile_newImageReplacesOld() {
        Member member = existingMember(OLD_KEY);
        MockMultipartFile image = ImageFixtures.jpeg("image");
        given(fileStorage.store(image, "avatars")).willReturn(NEW_KEY);

        memberService.updateProfile(1L, update(AvatarType.EMOJI, null, null), image);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);
        assertThat(member.getAvatarImageKey()).isEqualTo(NEW_KEY);
        verify(fileStorage).delete(OLD_KEY);
        verify(fileStorage, never()).delete(NEW_KEY);
    }

    @Test
    @DisplayName("수정: 기존 이미지가 없을 때 새 이미지를 올리면 저장만 하고 삭제할 파일은 없다")
    void updateProfile_firstImage() {
        Member member = existingMember(null);
        MockMultipartFile image = ImageFixtures.jpeg("image");
        given(fileStorage.store(image, "avatars")).willReturn(NEW_KEY);

        memberService.updateProfile(1L, update(AvatarType.IMAGE, null, null), image);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);
        assertThat(member.getAvatarImageKey()).isEqualTo(NEW_KEY);
        verify(fileStorage, never()).delete(any());
    }

    @Test
    @DisplayName("수정: avatarType=IMAGE 인데 새 이미지도 기존 이미지도 없으면 INVALID_AVATAR (파일 저장 안 함)")
    void updateProfile_imageTypeWithoutAnyImage() {
        existingMember(null);

        assertInvalidAvatar(() -> memberService.updateProfile(1L, update(AvatarType.IMAGE, null, null), null));

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: avatarType=IMAGE + 기존 이미지 → 그대로 IMAGE, 파일 저장·삭제 없음")
    void updateProfile_imageTypeWithExistingImage() {
        Member member = existingMember(OLD_KEY);

        memberService.updateProfile(1L, update(AvatarType.IMAGE, null, null), null);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);
        assertThat(member.getAvatarImageKey()).isEqualTo(OLD_KEY);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: 이모지로 전환(removeImage=false)해도 기존 이미지 key 는 남겨 다시 IMAGE 로 되돌릴 수 있다")
    void updateProfile_emojiKeepsExistingImage() {
        Member member = existingMember(OLD_KEY);

        memberService.updateProfile(1L, update(AvatarType.EMOJI, "🦊", false), null);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarEmoji()).isEqualTo("🦊");
        assertThat(member.getAvatarImageKey()).isEqualTo(OLD_KEY);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: removeImage=true → 이미지 삭제(커밋 후) + EMOJI 전환")
    void updateProfile_removeImage() {
        Member member = existingMember(OLD_KEY);

        memberService.updateProfile(1L, update(AvatarType.EMOJI, null, true), null);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarImageKey()).isNull();
        verify(fileStorage).delete(OLD_KEY);
    }

    @Test
    @DisplayName("수정: 지울 이미지가 없을 때 removeImage=true 는 예외 없이 EMOJI, 삭제 호출 없음")
    void updateProfile_removeImage_noImage() {
        Member member = existingMember(null);

        memberService.updateProfile(1L, update(AvatarType.EMOJI, null, true), null);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: 새 이미지 + removeImage=true 동시 요청은 INVALID_AVATAR (저장·삭제 없음)")
    void updateProfile_imageAndRemove() {
        existingMember(OLD_KEY);

        assertInvalidAvatar(() -> memberService.updateProfile(
                1L, update(AvatarType.IMAGE, null, true), ImageFixtures.jpeg("image")));

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: removeImage=true 인데 avatarType=IMAGE 면 남는 이미지가 없으므로 INVALID_AVATAR, 기존 파일은 지우지 않는다")
    void updateProfile_removeImageButImageType() {
        Member member = existingMember(OLD_KEY);

        assertInvalidAvatar(() -> memberService.updateProfile(1L, update(AvatarType.IMAGE, null, true), null));

        assertThat(member.getAvatarImageKey()).isEqualTo(OLD_KEY);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("수정: 새 이미지 검증 실패(INVALID_FILE)는 그대로 전달되고 기존 이미지는 지우지 않는다")
    void updateProfile_invalidNewImage() {
        existingMember(OLD_KEY);
        MockMultipartFile image = ImageFixtures.jpeg("image");
        given(fileStorage.store(image, "avatars")).willThrow(new BusinessException(ErrorCode.INVALID_FILE));

        assertThatThrownBy(() -> memberService.updateProfile(1L, update(AvatarType.IMAGE, null, null), image))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE);

        verify(fileStorage, never()).delete(any());
    }

    @Test
    @DisplayName("수정: 없는 회원이면 MEMBER_NOT_FOUND")
    void updateProfile_memberNotFound() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateProfile(1L, update(AvatarType.EMOJI, null, null), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    // ───────────── 비밀번호 변경 ─────────────

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호가 맞으면 새 비밀번호를 암호화해 저장")
    void changePassword_success() {
        Member member = existingMember(null);
        given(passwordEncoder.matches("current-pw", "encoded")).willReturn(true);
        given(passwordEncoder.encode("new-password")).willReturn("new-encoded");

        memberService.changePassword(1L, new ChangePasswordRequest("current-pw", "new-password"));

        assertThat(member.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("비밀번호 변경: 현재 비밀번호 불일치 → INVALID_PASSWORD, 비밀번호는 그대로")
    void changePassword_mismatch() {
        Member member = existingMember(null);
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> memberService.changePassword(1L, new ChangePasswordRequest("wrong", "new-password")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);

        assertThat(member.getPassword()).isEqualTo("encoded");
        verify(passwordEncoder, never()).encode(any());
    }

    // ───────────── 관리자 신청 ─────────────

    @Test
    @DisplayName("관리자 신청: NONE → PENDING(현재 시각 기록) + AdminRequestedEvent")
    void requestAdmin_success() {
        Member member = existingMember(null);

        MemberResponse response = memberService.requestAdmin(1L);

        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(member.getAdminRequestedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 12, 0));
        assertThat(response.adminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        verify(eventPublisher).publishEvent(new AdminRequestedEvent(1L));
    }

    @Test
    @DisplayName("관리자 신청: 이미 PENDING 이면 INVALID_ADMIN_REQUEST, 이벤트를 발행하지 않는다")
    void requestAdmin_alreadyPending() {
        Member member = existingMember(null);
        member.requestAdmin(LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThatThrownBy(() -> memberService.requestAdmin(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ADMIN_REQUEST);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("관리자 신청: 거절된 뒤에는 재신청 가능")
    void requestAdmin_afterRejected() {
        Member member = existingMember(null);
        member.requestAdmin(LocalDateTime.of(2026, 9, 1, 0, 0));
        member.rejectAdmin();

        memberService.requestAdmin(1L);

        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        verify(eventPublisher).publishEvent(new AdminRequestedEvent(1L));
    }
}
