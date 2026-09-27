package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.event.AdminRequestDecidedEvent;
import com.boardgame.reservation.member.event.AdminRequestedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.member.service.MemberService;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 관리자 신청·승인/거절 → SUPER_ADMIN ↔ 신청자 알림.
 * SUPER_ADMIN 조회는 AdminInitializer 와 같은 app.admin.email 설정을 그대로 재사용한다(새 리포지토리 메서드 불필요).
 * findById 로 얻은 루트 엔티티의 직접 컬럼(닉네임·id)만 읽으므로 트랜잭션이 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberNotificationListener {

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(AdminRequestedEvent event) {
        try {
            Member requester = memberRepository.findById(event.memberId()).orElse(null);
            if (requester == null) {
                return;
            }
            Long superAdminId = resolveSuperAdminId();
            notificationService.notify(superAdminId, event.memberId(), NotificationType.ADMIN_REQUESTED,
                    requester.getNickname() + "님이 관리자 권한을 신청했어요", "/admin/admin-requests");
        } catch (Exception e) {
            log.error("ADMIN_REQUESTED 알림 실패: memberId={}", event.memberId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(AdminRequestDecidedEvent event) {
        try {
            Long superAdminId = resolveSuperAdminId();
            if (event.approved()) {
                notificationService.notify(event.memberId(), superAdminId, NotificationType.ADMIN_APPROVED,
                        "관리자 신청이 승인됐어요 (다시 로그인해주세요)", "/me");
            } else {
                notificationService.notify(event.memberId(), superAdminId, NotificationType.ADMIN_REJECTED,
                        "관리자 신청이 거절됐어요", "/me");
            }
        } catch (Exception e) {
            log.error("ADMIN_APPROVED/REJECTED 알림 실패: memberId={}", event.memberId(), e);
        }
    }

    private Long resolveSuperAdminId() {
        if (adminEmail == null || adminEmail.isBlank()) {
            return null;
        }
        return memberRepository.findByEmail(MemberService.normalizeEmail(adminEmail))
                .map(Member::getId)
                .orElse(null);
    }
}
