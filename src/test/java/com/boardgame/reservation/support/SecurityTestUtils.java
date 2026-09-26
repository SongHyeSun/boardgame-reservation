package com.boardgame.reservation.support;

import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.domain.Member;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** MockMvc 요청에 로그인 상태를 만들어 주는 헬퍼 */
public final class SecurityTestUtils {

    private SecurityTestUtils() {
    }

    /**
     * 컨트롤러가 @AuthenticationPrincipal MemberPrincipal 을 쓰므로 실제 주체 타입으로 로그인 상태를 만든다.
     * (user("x").roles(..) 는 principal 이 MemberPrincipal 이 아니라 컨트롤러에서 null 로 들어온다)
     */
    public static RequestPostProcessor loginAs(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(member);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}
