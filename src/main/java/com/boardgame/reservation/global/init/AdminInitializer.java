package com.boardgame.reservation.global.init;

import com.boardgame.reservation.global.common.TransactionCallbacks;
import com.boardgame.reservation.global.security.MemberSessionInvalidator;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 최고 관리자(SUPER_ADMIN) 계정 부트스트랩.
 * 회원가입 API 로는 USER 만 만들어지고 ADMIN 은 SUPER_ADMIN 의 승인으로만 생기므로,
 * SUPER_ADMIN 은 환경변수로 받은 계정을 서버 시작 시 보장한다.
 *   ADMIN_EMAIL, ADMIN_PASSWORD 가 둘 다 설정된 경우에만 동작 (비번을 코드/깃에 남기지 않기 위함)
 *   - 계정이 없으면 SUPER_ADMIN 으로 생성
 *   - 이미 있으면(예전에 ADMIN 으로 만들어진 계정 등) role 을 SUPER_ADMIN 으로 갱신하고, 비밀번호는 건드리지 않는다
 *     → 기존 세션에는 예전 권한만 들어 있으므로 그 계정의 세션도 무효화(재로그인)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberSessionInvalidator sessionInvalidator;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        String email = MemberService.normalizeEmail(adminEmail);
        Optional<Member> existing = memberRepository.findByEmail(email);

        if (existing.isEmpty()) {
            memberRepository.save(Member.createSuperAdmin(email, passwordEncoder.encode(adminPassword), "관리자"));
            log.info("Super admin account created: {}", email);
            return;
        }

        Member member = existing.get();
        if (member.getRole() != Role.SUPER_ADMIN) {
            member.promoteToSuperAdmin();
            TransactionCallbacks.afterCommit(() -> sessionInvalidator.invalidateAll(email));
            log.info("Existing account promoted to super admin: {}", email);
        }
    }
}
