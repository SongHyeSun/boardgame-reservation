package com.boardgame.reservation.global.security;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션에 저장되는 로그인 사용자 정보.
 * 컨트롤러에서 @AuthenticationPrincipal MemberPrincipal principal 로 꺼내 쓴다.
 *
 * - 엔티티(Member)를 통째로 세션에 넣지 않는다 → 지연로딩/직렬화 문제 방지
 * - UserDetails 는 Serializable → 나중에 Spring Session + Redis로 세션을 옮겨도 그대로 저장 가능
 * - CredentialsContainer → 인증 성공 후 Spring Security가 password를 null로 지워준다 (세션에 비번 해시 안 남음)
 */
@Getter
public class MemberPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String nickname;
    private final Role role;
    private String password;

    private MemberPrincipal(Long id, String email, String password, String nickname, Role role) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(
                member.getId(), member.getEmail(), member.getPassword(),
                member.getNickname(), member.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getAuthority()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // 계정 잠금/만료 기능은 스코프 밖 → 항상 true
    // (Spring Security 6.3+ 에선 기본 구현이 있지만, 하위 버전 호환을 위해 명시)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
