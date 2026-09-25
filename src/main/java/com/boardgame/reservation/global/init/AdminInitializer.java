package com.boardgame.reservation.global.init;

import com.boardgame.reservation.member.domain.Member;
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

/**
 * 관리자 계정 부트스트랩.
 * 회원가입 API로는 USER만 만들어지므로, ADMIN은 환경변수로 받은 계정을 서버 시작 시 1회 생성한다.
 *   ADMIN_EMAIL, ADMIN_PASSWORD 가 둘 다 설정된 경우에만 동작 (비번을 코드/깃에 남기지 않기 위함)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

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
        if (memberRepository.existsByEmail(email)) {
            return;
        }
        memberRepository.save(Member.createAdmin(email, passwordEncoder.encode(adminPassword), "관리자"));
        log.info("Admin account created: {}", email);
    }
}
