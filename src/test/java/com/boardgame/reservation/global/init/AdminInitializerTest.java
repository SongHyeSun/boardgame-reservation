package com.boardgame.reservation.global.init;

import com.boardgame.reservation.global.security.MemberSessionInvalidator;
import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;
import com.boardgame.reservation.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 부트스트랩: ADMIN_EMAIL 계정을 SUPER_ADMIN 으로 생성하거나 승격 (@Value 필드는 ReflectionTestUtils 로 주입) */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    MemberSessionInvalidator sessionInvalidator;

    AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminInitializer(memberRepository, passwordEncoder, sessionInvalidator);
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
    }

    @Test
    @DisplayName("이메일 또는 비밀번호가 비어 있으면 아무 것도 하지 않는다")
    void skippedWhenNotConfigured() {
        configure("", "secret");
        initializer.run(null);
        configure("root@test.com", " ");
        initializer.run(null);

        verifyNoInteractions(memberRepository, passwordEncoder, sessionInvalidator);
    }

    @Test
    @DisplayName("계정이 없으면 이메일을 정규화해 SUPER_ADMIN 으로 생성한다 (비밀번호 암호화, 세션 무효화 없음)")
    void createsSuperAdmin() {
        configure(" Root@Test.com ", "secret-pw");
        given(memberRepository.findByEmail("root@test.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("secret-pw")).willReturn("encoded");

        initializer.run(null);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("root@test.com");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(saved.getAdminRequestStatus()).isEqualTo(AdminRequestStatus.NONE);
        verifyNoInteractions(sessionInvalidator);
    }

    @Test
    @DisplayName("이미 ADMIN 으로 있던 계정은 SUPER_ADMIN 으로 승격하고(비밀번호 유지), 그 계정의 기존 세션을 무효화한다")
    void promotesExistingAdmin() {
        configure("root@test.com", "secret-pw");
        Member existing = Member.createAdmin("root@test.com", "old-encoded", "관리자");
        given(memberRepository.findByEmail("root@test.com")).willReturn(Optional.of(existing));

        initializer.run(null);

        assertThat(existing.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(existing.getPassword()).isEqualTo("old-encoded");
        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        verify(sessionInvalidator).invalidateAll("root@test.com");
    }

    @Test
    @DisplayName("같은 이메일의 일반 회원(USER)도 SUPER_ADMIN 으로 갱신된다")
    void promotesExistingUser() {
        configure("root@test.com", "secret-pw");
        Member existing = Member.createUser("root@test.com", "old-encoded", "일반회원");
        given(memberRepository.findByEmail("root@test.com")).willReturn(Optional.of(existing));

        initializer.run(null);

        assertThat(existing.getRole()).isEqualTo(Role.SUPER_ADMIN);
        verify(sessionInvalidator).invalidateAll("root@test.com");
    }

    @Test
    @DisplayName("이미 SUPER_ADMIN 이면 아무 것도 바꾸지 않는다 (재기동해도 세션을 끊지 않음)")
    void unchangedWhenAlreadySuperAdmin() {
        configure("root@test.com", "secret-pw");
        Member existing = Member.createSuperAdmin("root@test.com", "encoded", "관리자");
        given(memberRepository.findByEmail("root@test.com")).willReturn(Optional.of(existing));

        initializer.run(null);

        assertThat(existing.getRole()).isEqualTo(Role.SUPER_ADMIN);
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(sessionInvalidator, passwordEncoder);
    }
}
