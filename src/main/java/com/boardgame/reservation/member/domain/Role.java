package com.boardgame.reservation.member.domain;

/**
 * 회원 권한. Spring Security의 hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 확인한다.
 * 계층(SecurityConfig.roleHierarchy): SUPER_ADMIN > ADMIN > USER
 * SUPER_ADMIN 은 부트스트랩(ADMIN_EMAIL) 계정 1개뿐이며 가입·승인으로는 만들 수 없다.
 */
public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
