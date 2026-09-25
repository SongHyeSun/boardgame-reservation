package com.boardgame.reservation.member.domain;

/**
 * 회원 권한. Spring Security의 hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 확인한다.
 */
public enum Role {
    USER,
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
