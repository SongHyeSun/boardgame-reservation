package com.boardgame.reservation.party.service;

import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
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

    /** UNIQUE(party_id, member_id) 위반 시 DataIntegrityViolationException */
    @Transactional
    public void add(Long partyId, Long memberId) {
        partyMemberRepository.save(PartyMember.create(
                partyRepository.getReferenceById(partyId),
                memberRepository.getReferenceById(memberId)));
    }

    /** @return 실제로 삭제됐으면 true (참여자가 아니면 false) */
    @Transactional
    public boolean remove(Long partyId, Long memberId) {
        return partyMemberRepository.deleteByPartyIdAndMemberId(partyId, memberId) > 0;
    }
}
