package com.boardgame.reservation.global.security;

import com.boardgame.reservation.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * AuthenticationManager가 로그인 시 호출 → email로 회원 조회.
 * 비밀번호 비교는 Spring Security(DaoAuthenticationProvider + BCrypt)가 알아서 한다.
 *
 * UsernameNotFoundException은 Security가 BadCredentialsException으로 바꿔서 던지므로
 * "이메일 없음"과 "비번 틀림"이 같은 응답이 된다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return memberRepository.findByEmail(normalized)
                .map(MemberPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("member not found"));
    }
}
