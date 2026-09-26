package com.boardgame.reservation.member.domain;

import com.boardgame.reservation.global.common.BaseTimeEntity;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD: MEMBER (id, email[unique, 변경 불가], password[BCrypt], nickname, role, 프로필, 아바타, 관리자 신청 상태, created_at)
 *
 * name 은 기존 계정 때문에 DB 에서는 nullable, 가입·수정 DTO 에서 필수로 받는다.
 * email 은 Spring Session 의 principal name(세션 인덱스 키)이므로 변경 기능을 만들지 않는다.
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자, 외부에서 new Member() 금지
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** 반드시 PasswordEncoder로 암호화된 값만 들어온다 (BCrypt 60자) */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING) // ORDINAL(숫자)로 저장하면 enum 순서 바뀔 때 데이터가 깨짐
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 20)
    private String name;

    private LocalDate birthDate;

    @Column(length = 50)
    private String affiliation;

    @Column(length = 50)
    private String job;

    @Column(length = 100)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvatarType avatarType;

    @Column(nullable = false, length = 20)
    private String avatarEmoji;

    /** FileStorage key. avatarType 이 EMOJI 여도 이전에 올린 이미지는 남겨 둘 수 있다(다시 IMAGE 로 전환 가능) */
    @Column(length = 100)
    private String avatarImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRequestStatus adminRequestStatus;

    private LocalDateTime adminRequestedAt;

    /** 가입·수정 때 함께 다루는 프로필 값 */
    public record Profile(String name, LocalDate birthDate, String affiliation, String job, String bio) {
    }

    private Member(String email, String encodedPassword, String nickname, Role role) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = role;
        this.avatarType = AvatarType.EMOJI;
        this.avatarEmoji = AvatarEmojis.DEFAULT;
        this.adminRequestStatus = AdminRequestStatus.NONE;
    }

    /** 일반 회원 (프로필 없이 최소 정보만. 이름은 비어 있음) */
    public static Member createUser(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, Role.USER);
    }

    /** 관리자 계정 */
    public static Member createAdmin(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, Role.ADMIN);
    }

    /** 최고 관리자 계정 (AdminInitializer 에서만 사용) */
    public static Member createSuperAdmin(String email, String encodedPassword, String nickname) {
        Member member = new Member(email, encodedPassword, nickname, Role.SUPER_ADMIN);
        member.name = nickname;
        return member;
    }

    /**
     * 회원가입. 항상 USER 로 만들고, adminRequestedAt 이 있으면 관리자 신청(PENDING)까지 함께 기록한다.
     *
     * @param avatarEmoji null 이면 기본 이모지. 허용 목록 검증은 호출하는 서비스가 한다
     * @param avatarImageKey 있으면 아바타 타입은 IMAGE
     */
    public static Member register(String email, String encodedPassword, String nickname, Profile profile,
                                  String avatarEmoji, String avatarImageKey, LocalDateTime adminRequestedAt) {
        Member member = new Member(email, encodedPassword, nickname, Role.USER);
        member.applyProfile(profile);
        if (avatarEmoji != null) {
            member.avatarEmoji = avatarEmoji;
        }
        if (avatarImageKey != null) {
            member.useImageAvatar(avatarImageKey);
        }
        if (adminRequestedAt != null) {
            member.requestAdmin(adminRequestedAt);
        }
        return member;
    }

    // ───────────── 프로필 · 아바타 · 비밀번호 ─────────────

    public void updateProfile(String nickname, Profile profile) {
        this.nickname = nickname;
        applyProfile(profile);
    }

    private void applyProfile(Profile profile) {
        this.name = profile.name();
        this.birthDate = profile.birthDate();
        this.affiliation = profile.affiliation();
        this.job = profile.job();
        this.bio = profile.bio();
    }

    /** 아바타 타입은 그대로 두고 이모지 값만 바꾼다 (이미지 아바타 사용 중에도 대체 이모지를 바꿀 수 있게) */
    public void changeAvatarEmoji(String emoji) {
        this.avatarEmoji = emoji;
    }

    /** 이모지 아바타로 전환. 저장된 이미지 key 는 그대로 둔다 */
    public void useEmojiAvatar(String emoji) {
        this.avatarType = AvatarType.EMOJI;
        this.avatarEmoji = emoji;
    }

    /** 이미지 아바타로 전환. 이미지 key 가 없으면 INVALID_AVATAR */
    public void useImageAvatar(String imageKey) {
        if (imageKey == null) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR);
        }
        this.avatarType = AvatarType.IMAGE;
        this.avatarImageKey = imageKey;
    }

    /** 이미지를 제거하고 이모지 아바타로 전환 */
    public void removeAvatarImage() {
        this.avatarImageKey = null;
        this.avatarType = AvatarType.EMOJI;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // ───────────── 관리자 신청 ─────────────

    /** 관리자 신청(재신청 포함): USER 이고 상태가 NONE/REJECTED 일 때만 */
    public void requestAdmin(LocalDateTime now) {
        boolean requestable = role == Role.USER
                && (adminRequestStatus == AdminRequestStatus.NONE || adminRequestStatus == AdminRequestStatus.REJECTED);
        if (!requestable) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
        this.adminRequestStatus = AdminRequestStatus.PENDING;
        this.adminRequestedAt = now;
    }

    /** 승인: PENDING 일 때만. role 을 ADMIN 으로 올린다 */
    public void approveAdmin() {
        requirePendingRequest();
        this.role = Role.ADMIN;
        this.adminRequestStatus = AdminRequestStatus.APPROVED;
    }

    /** 거절: PENDING 일 때만. role 은 그대로 */
    public void rejectAdmin() {
        requirePendingRequest();
        this.adminRequestStatus = AdminRequestStatus.REJECTED;
    }

    private void requirePendingRequest() {
        if (adminRequestStatus != AdminRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REQUEST);
        }
    }

    /** 부트스트랩 계정 승격 (AdminInitializer 에서만 사용) */
    public void promoteToSuperAdmin() {
        this.role = Role.SUPER_ADMIN;
    }
}
