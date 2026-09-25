package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.party.domain.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    long countByPartyId(Long partyId);

    long deleteByPartyIdAndMemberId(Long partyId, Long memberId);

    @Query("select pm.member.id from PartyMember pm where pm.party.id = :partyId")
    List<Long> findMemberIdsByPartyId(@Param("partyId") Long partyId);

    @Query("select pm from PartyMember pm join fetch pm.member where pm.party.id = :partyId order by pm.joinedAt, pm.id")
    List<PartyMember> findAllWithMemberByPartyId(@Param("partyId") Long partyId);

    /** 목록의 currentCount 를 파티마다 세지 않고 한 번에 집계 */
    @Query("select pm.party.id as partyId, count(pm) as count from PartyMember pm "
            + "where pm.party.id in :partyIds group by pm.party.id")
    List<PartyCount> countByPartyIds(@Param("partyIds") Collection<Long> partyIds);

    interface PartyCount {
        Long getPartyId();

        long getCount();
    }
}
