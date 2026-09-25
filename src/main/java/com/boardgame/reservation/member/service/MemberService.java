package com.boardgame.reservation.member.service;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.dto.SignupRequest;
import com.boardgame.reservation.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        // 1차 방어: 애플리케이션 레벨 중복 체크 (친절한 에러 메시지용)
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.createUser(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname().trim());

        try {
            // 2차 방어: 동시에 같은 이메일로 가입 요청이 오면 exists 체크를 둘 다 통과할 수 있음
            //          → DB UNIQUE 제약 위반을 잡아서 409로 변환
            //          (IDENTITY 전략이라 save 시점에 INSERT가 바로 나간다)
            return MemberResponse.from(memberRepository.save(member));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    public MemberResponse getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /** "Test@Mail.com " 과 "test@mail.com" 을 같은 계정으로 취급 */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
