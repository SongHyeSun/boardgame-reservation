package com.boardgame.reservation.member;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.AvatarEmojis;
import com.boardgame.reservation.member.domain.AvatarType;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Member 도메인 규칙(기본값, 아바타 전환, 관리자 신청 상태 전이) — Spring 없이 검증 */
class MemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);
    private static final Member.Profile PROFILE =
            new Member.Profile("홍길동", LocalDate.of(1995, 5, 1), "보드게임 동아리", "개발자", "잘 부탁드려요");

    private static Member newUser() {
        return Member.createUser("user@test.com", "encoded", "닉네임");
    }

    private static void assertInvalid(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ───────────── 기본값 · 팩토리 ─────────────

    @Test
    @DisplayName("createUser: USER, 기본 이모지 아바타, 신청 상태 NONE")
    void createUser_defaults() {
        Member member = newUser();

        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarEmoji()).isEqualTo("🎲");
        assertThat(member.getAvatarImageKey()).isNull();
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.NONE);
        assertThat(member.getAdminRequestedAt()).isNull();
        assertThat(member.getName()).isNull();
    }

    @Test
    @DisplayName("createAdmin / createSuperAdmin 은 각각 ADMIN / SUPER_ADMIN")
    void createAdminAndSuperAdmin() {
        assertThat(Member.createAdmin("a@test.com", "encoded", "관리자").getRole()).isEqualTo(Role.ADMIN);

        Member superAdmin = Member.createSuperAdmin("s@test.com", "encoded", "관리자");
        assertThat(superAdmin.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(superAdmin.getName()).isEqualTo("관리자");
        assertThat(superAdmin.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.NONE);
    }

    @Test
    @DisplayName("register: 프로필이 채워지고 항상 USER, 기본 이모지 아바타, 신청 없음")
    void register_basic() {
        Member member = Member.register("user@test.com", "encoded", "닉네임", PROFILE, null, null, null);

        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 1));
        assertThat(member.getAffiliation()).isEqualTo("보드게임 동아리");
        assertThat(member.getJob()).isEqualTo("개발자");
        assertThat(member.getBio()).isEqualTo("잘 부탁드려요");
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarEmoji()).isEqualTo("🎲");
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.NONE);
    }

    @Test
    @DisplayName("register: 이모지·이미지 key·관리자 신청 시각이 있으면 반영(IMAGE, PENDING)하되 role 은 USER")
    void register_withAvatarAndAdminRequest() {
        String key = "avatars/00000000-0000-0000-0000-000000000000.png";

        Member member = Member.register("user@test.com", "encoded", "닉네임", PROFILE, "🦊", key, NOW);

        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);
        assertThat(member.getAvatarEmoji()).isEqualTo("🦊");
        assertThat(member.getAvatarImageKey()).isEqualTo(key);
        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(member.getAdminRequestedAt()).isEqualTo(NOW);
    }

    // ───────────── 프로필 · 아바타 ─────────────

    @Test
    @DisplayName("updateProfile: 닉네임과 프로필 값이 모두 바뀌고 선택 값은 null 로 지울 수 있다")
    void updateProfile() {
        Member member = Member.register("user@test.com", "encoded", "닉네임", PROFILE, null, null, null);

        member.updateProfile("새닉네임", new Member.Profile("김철수", null, null, null, null));

        assertThat(member.getNickname()).isEqualTo("새닉네임");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getBirthDate()).isNull();
        assertThat(member.getAffiliation()).isNull();
        assertThat(member.getJob()).isNull();
        assertThat(member.getBio()).isNull();
    }

    @Test
    @DisplayName("아바타 전환: 이미지 ↔ 이모지, 이모지로 바꿔도 이미지 key 는 남고, removeAvatarImage 는 key 를 지우고 EMOJI")
    void avatarTransitions() {
        Member member = newUser();
        String key = "avatars/00000000-0000-0000-0000-000000000000.jpg";

        member.useImageAvatar(key);
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);

        member.useEmojiAvatar("🐉");
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarEmoji()).isEqualTo("🐉");
        assertThat(member.getAvatarImageKey()).isEqualTo(key);

        member.useImageAvatar(member.getAvatarImageKey());
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.IMAGE);

        member.removeAvatarImage();
        assertThat(member.getAvatarType()).isEqualTo(AvatarType.EMOJI);
        assertThat(member.getAvatarImageKey()).isNull();
    }

    @Test
    @DisplayName("이미지 key 없이 IMAGE 로 전환하면 INVALID_AVATAR")
    void useImageAvatar_withoutKey() {
        assertInvalid(() -> newUser().useImageAvatar(null), ErrorCode.INVALID_AVATAR);
    }

    // ───────────── 관리자 신청 상태 전이 ─────────────

    @Test
    @DisplayName("NONE → 신청 → PENDING(신청 시각 기록)")
    void requestAdmin_fromNone() {
        Member member = newUser();

        member.requestAdmin(NOW);

        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(member.getAdminRequestedAt()).isEqualTo(NOW);
        assertThat(member.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("PENDING 이면 다시 신청할 수 없다(INVALID_ADMIN_REQUEST)")
    void requestAdmin_whenPending() {
        Member member = newUser();
        member.requestAdmin(NOW);

        assertInvalid(() -> member.requestAdmin(NOW.plusDays(1)), ErrorCode.INVALID_ADMIN_REQUEST);
        assertThat(member.getAdminRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("승인 → ADMIN + APPROVED, 이후 재신청·재승인·거절은 불가")
    void approve() {
        Member member = newUser();
        member.requestAdmin(NOW);

        member.approveAdmin();

        assertThat(member.getRole()).isEqualTo(Role.ADMIN);
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.APPROVED);
        assertInvalid(member::approveAdmin, ErrorCode.INVALID_ADMIN_REQUEST);
        assertInvalid(member::rejectAdmin, ErrorCode.INVALID_ADMIN_REQUEST);
        assertInvalid(() -> member.requestAdmin(NOW), ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("거절 → REJECTED(role 유지), 거절된 뒤에는 재신청 가능하고 시각이 갱신된다")
    void reject_thenReRequest() {
        Member member = newUser();
        member.requestAdmin(NOW);

        member.rejectAdmin();

        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.REJECTED);

        member.requestAdmin(NOW.plusDays(2));
        assertThat(member.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.PENDING);
        assertThat(member.getAdminRequestedAt()).isEqualTo(NOW.plusDays(2));
    }

    @Test
    @DisplayName("PENDING 이 아니면 승인·거절 불가")
    void decide_requiresPending() {
        Member member = newUser();

        assertInvalid(member::approveAdmin, ErrorCode.INVALID_ADMIN_REQUEST);
        assertInvalid(member::rejectAdmin, ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("ADMIN·SUPER_ADMIN 은 관리자 신청 불가")
    void requestAdmin_notForAdmins() {
        assertInvalid(() -> Member.createAdmin("a@test.com", "encoded", "관리자").requestAdmin(NOW),
                ErrorCode.INVALID_ADMIN_REQUEST);
        assertInvalid(() -> Member.createSuperAdmin("s@test.com", "encoded", "관리자").requestAdmin(NOW),
                ErrorCode.INVALID_ADMIN_REQUEST);
    }

    @Test
    @DisplayName("promoteToSuperAdmin: role 만 SUPER_ADMIN 으로 바뀐다")
    void promoteToSuperAdmin() {
        Member admin = Member.createAdmin("a@test.com", "encoded", "관리자");

        admin.promoteToSuperAdmin();

        assertThat(admin.getRole()).isEqualTo(Role.SUPER_ADMIN);
    }

    @Test
    @DisplayName("changePassword: 암호화된 값으로 교체")
    void changePassword() {
        Member member = newUser();

        member.changePassword("new-encoded");

        assertThat(member.getPassword()).isEqualTo("new-encoded");
    }

    // ───────────── 이모지 목록 ─────────────

    @Test
    @DisplayName("허용 이모지는 16개, 중복 없음, 기본값 포함, 소스 인코딩이 깨지지 않았다(코드포인트 검증)")
    void avatarEmojis() {
        assertThat(AvatarEmojis.ALLOWED).hasSize(16).doesNotHaveDuplicates().contains(AvatarEmojis.DEFAULT);

        // 🎲 U+1F3B2, ♟️ = U+265F + U+FE0F : 컴파일 인코딩이 깨지면 여기서 걸린다
        assertThat(AvatarEmojis.DEFAULT.codePoints().toArray()).containsExactly(0x1F3B2);
        assertThat(AvatarEmojis.ALLOWED.get(2).codePoints().toArray()).containsExactly(0x265F, 0xFE0F);
    }

    @Test
    @DisplayName("isAllowed: 목록 안의 값만 true, null·빈 값·목록 밖 이모지·일반 문자는 false")
    void avatarEmojis_isAllowed() {
        assertThat(AvatarEmojis.isAllowed("🦊")).isTrue();
        assertThat(AvatarEmojis.isAllowed("♟️")).isTrue();
        assertThat(AvatarEmojis.isAllowed("♟")).isFalse(); // FE0F 없는 변형은 다른 문자열
        assertThat(AvatarEmojis.isAllowed("😀")).isFalse();
        assertThat(AvatarEmojis.isAllowed("a")).isFalse();
        assertThat(AvatarEmojis.isAllowed("")).isFalse();
        assertThat(AvatarEmojis.isAllowed(null)).isFalse();
    }
}
