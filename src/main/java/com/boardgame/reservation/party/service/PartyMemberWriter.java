package com.boardgame.reservation.party.service;

import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.event.PartyMemberKickedEvent;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PARTY_MEMBER 쓰기 전용 빈. 트랜잭션을 여기서 끝내야 PartyService 가 커밋/롤백 결과를 보고
 * Redis 보상을 확실히 실행할 수 있다 (같은 빈 안에서 호출하면 프록시를 안 거쳐 @Transactional 이 무시됨).
 */
@Component
@RequiredArgsConstructor
public class PartyMemberWriter {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** UNIQUE(party_id, member_id) 위반 시 DataIntegrityViolationException (이미 참여했거나 내보내진 회원) */
    @Transactional
    public void add(Long partyId, Long memberId) {
        partyMemberRepository.save(PartyMember.create(
                partyRepository.getReferenceById(partyId),
                memberRepository.getReferenceById(memberId)));
    }

    /** 자진 탈퇴: 행을 지운다(재참여 가능). @return 실제로 삭제됐으면 true (참여 중이 아니면, 내보내진 회원이어도 false) */
    @Transactional
    public boolean remove(Long partyId, Long memberId) {
        return partyMemberRepository.deleteJoined(partyId, memberId) > 0;
    }

    /**
     * 내보내기: 행을 남기고 KICKED 로 바꾼다(재참여 차단). @return 참여 중이던 회원이 내보내졌으면 true.
     * 알림 리스너가 AFTER_COMMIT 이라 이벤트는 반드시 이 트랜잭션 안에서 발행한다.
     */
    @Transactional
    public boolean kick(Long partyId, Long memberId) {
        if (partyMemberRepository.kick(partyId, memberId) == 0) {
            return false;
        }
        eventPublisher.publishEvent(new PartyMemberKickedEvent(partyId, memberId));
        return true;
    }
}
