package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.event.PartyClosedEvent;
import com.boardgame.reservation.party.event.PartyMemberJoinedEvent;
import com.boardgame.reservation.party.event.PartyMemberKickedEvent;
import com.boardgame.reservation.party.event.PartyMemberLeftEvent;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 파티 이벤트 → 알림. 읽는 값은 전부 PartyRepository.findWithDetailsById(host·boardGame join fetch),
 * MemberRepository.findById(루트 엔티티 직접 컬럼), findJoinedMemberIdsByPartyId(projection) 뿐이라
 * 지연 연관관계를 추가로 파고들지 않는다 — 그래서 이 리스너는 트랜잭션이 필요 없다(NotificationReadContext 불필요).
 * 예외는 절대 밖으로 던지지 않는다 — 이미 커밋된 요청(파티 참여 등)이 여기서 실패하면 정상 동작이 500 으로 보일 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyNotificationListener {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoined(PartyMemberJoinedEvent event) {
        try {
            Party party = partyRepository.findWithDetailsById(event.partyId()).orElse(null);
            Member joined = memberRepository.findById(event.memberId()).orElse(null);
            if (party == null || joined == null) {
                return;
            }
            Long hostId = party.getHost().getId();
            String link = "/parties/" + party.getId();

            notificationService.notify(hostId, event.memberId(), NotificationType.PARTY_JOINED,
                    joined.getNickname() + "님이 '" + party.getTitle() + "' 파티에 참여했어요", link);
            if (event.remaining() == 0) {
                notificationService.notify(hostId, event.memberId(), NotificationType.PARTY_FULL,
                        "'" + party.getTitle() + "' 파티 정원이 모두 찼어요", link);
            }
        } catch (Exception e) {
            log.error("PARTY_JOINED/PARTY_FULL 알림 실패: partyId={}, memberId={}", event.partyId(), event.memberId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeft(PartyMemberLeftEvent event) {
        try {
            Party party = partyRepository.findWithDetailsById(event.partyId()).orElse(null);
            Member left = memberRepository.findById(event.memberId()).orElse(null);
            if (party == null || left == null) {
                return;
            }
            notificationService.notify(party.getHost().getId(), event.memberId(), NotificationType.PARTY_LEFT,
                    left.getNickname() + "님이 '" + party.getTitle() + "' 파티에서 나갔어요", "/parties/" + party.getId());
        } catch (Exception e) {
            log.error("PARTY_LEFT 알림 실패: partyId={}, memberId={}", event.partyId(), event.memberId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKicked(PartyMemberKickedEvent event) {
        try {
            Party party = partyRepository.findWithDetailsById(event.partyId()).orElse(null);
            if (party == null) {
                return;
            }
            // 수신자는 내보내진 회원(event.memberId()) 본인. 호스트가 내보낸 것이라 actor==receiver 는 구조적으로 불가능하지만
            // notify() 의 공통 가드를 그대로 태우기 위해 actorId 로 호스트를 넘긴다.
            notificationService.notify(event.memberId(), party.getHost().getId(), NotificationType.PARTY_KICKED,
                    "'" + party.getTitle() + "' 파티에서 내보내졌어요", "/parties/" + party.getId());
        } catch (Exception e) {
            log.error("PARTY_KICKED 알림 실패: partyId={}, memberId={}", event.partyId(), event.memberId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(PartyClosedEvent event) {
        try {
            Party party = partyRepository.findWithDetailsById(event.partyId()).orElse(null);
            if (party == null) {
                return;
            }
            Long hostId = party.getHost().getId();
            String link = "/parties/" + party.getId();
            // 호스트도 JOINED 목록에 포함되지만 notify() 가 actor==receiver 를 조용히 걸러낸다.
            for (Long memberId : partyMemberRepository.findJoinedMemberIdsByPartyId(event.partyId())) {
                notificationService.notify(memberId, hostId, NotificationType.PARTY_CLOSED,
                        "'" + party.getTitle() + "' 파티 모집이 마감됐어요", link);
            }
        } catch (Exception e) {
            log.error("PARTY_CLOSED 알림 실패: partyId={}", event.partyId(), e);
        }
    }
}
