package com.boardgame.reservation.member.service;

import com.boardgame.reservation.global.common.TransactionCallbacks;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.global.security.MemberSessionInvalidator;
import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.AdminRequestResponse;
import com.boardgame.reservation.member.event.AdminRequestDecidedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 가입 신청 처리 (SUPER_ADMIN 전용 — SecurityConfig 에서 hasRole('SUPER_ADMIN')).
 * 동시 승인 요청은 상태(PENDING) 검사만 하고 별도 락은 두지 않는다(관리자 소수 사용 가정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRequestService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberSessionInvalidator sessionInvalidator;

    public List<AdminRequestResponse> getPendingRequests() {
        return memberRepository.findByAdminRequestStatusOrderByAdminRequestedAtAsc(AdminRequestStatus.PENDING).stream()
                .map(AdminRequestResponse::from)
                .toList();
    }

    /**
     * 승인: role → ADMIN, 상태 APPROVED.
     * 세션에 저장된 권한은 로그인 시점 값이라, 커밋 후 해당 회원의 세션을 전부 무효화해 재로그인시킨다.
     */
    @Transactional
    public void approve(Long memberId) {
        Member member = findMember(memberId);
        member.approveAdmin(); // PENDING 이 아니면 INVALID_ADMIN_REQUEST(409)
        eventPublisher.publishEvent(new AdminRequestDecidedEvent(member.getId(), true));

        String email = member.getEmail();
        TransactionCallbacks.afterCommit(() -> sessionInvalidator.invalidateAll(email));
    }

    /** 거절: 상태 REJECTED, role·세션은 그대로 */
    @Transactional
    public void reject(Long memberId) {
        Member member = findMember(memberId);
        member.rejectAdmin(); // PENDING 이 아니면 INVALID_ADMIN_REQUEST(409)
        eventPublisher.publishEvent(new AdminRequestDecidedEvent(member.getId(), false));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
