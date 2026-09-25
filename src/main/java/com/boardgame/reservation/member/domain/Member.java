package com.boardgame.reservation.member.domain;

import com.boardgame.reservation.global.common.BaseTimeEntity;
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

/**
 * ERD: MEMBER (id, email[unique], password[BCrypt], nickname, role, created_at)
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

    private Member(String email, String encodedPassword, String nickname, Role role) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = role;
    }

    /** 일반 회원 가입 */
    public static Member createUser(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, Role.USER);
    }

    /** 관리자 계정 (AdminInitializer에서만 사용) */
    public static Member createAdmin(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname, Role.ADMIN);
    }
}
