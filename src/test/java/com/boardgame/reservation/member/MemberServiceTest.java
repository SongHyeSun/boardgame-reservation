package com.boardgame.reservation.member;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.SignupRequest;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DB/스프링 없이 서비스 로직만 빠르게 검증하는 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    MemberService memberService;

    @Test
    @DisplayName("가입 시 이메일은 소문자로 정규화되고, 비밀번호는 암호화되어 저장된다")
    void signup_encodesPassword_andNormalizesEmail() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("{bcrypt}encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        memberService.signup(new SignupRequest(" HyeSeon@Test.com ", "password123", "혜선"));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("hyeseon@test.com");
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(saved.getRole().name()).isEqualTo("USER");
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 DUPLICATE_EMAIL 예외, 저장하지 않는다")
    void signup_duplicateEmail() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(true);

        assertThatThrownBy(() ->
                memberService.signup(new SignupRequest("hyeseon@test.com", "password123", "혜선")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 가입으로 exists 체크를 통과해도 UNIQUE 위반은 DUPLICATE_EMAIL로 변환된다")
    void signup_uniqueViolation_convertedToDuplicate() {
        given(memberRepository.existsByEmail("hyeseon@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("{bcrypt}encoded");
        given(memberRepository.save(any(Member.class)))
                .willThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() ->
                memberService.signup(new SignupRequest("hyeseon@test.com", "password123", "혜선")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }
}
