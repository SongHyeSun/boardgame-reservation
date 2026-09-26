package com.boardgame.reservation.party.repository;

import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.domain.PartyMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 인원·목록·Redis 복구는 모두 JOINED 만 기준이다 (KICKED 행은 재참여를 막기 위해서만 남긴다).
 * 상태를 바꾸는 쿼리는 조건(status = JOINED)을 SQL 에 넣은 벌크 연산이라, 읽고-쓰기 사이에 다른 요청이 끼어들 수 없다.
 */
public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    @Query("select count(pm) from PartyMember pm where pm.party.id = :partyId "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED")
    long countJoinedByPartyId(@Param("partyId") Long partyId);

    boolean existsByPartyIdAndMemberIdAndStatus(Long partyId, Long memberId, PartyMemberStatus status);

    /** 자진 탈퇴. KICKED 행은 지우지 않는다 (지우면 재참여 제한이 풀린다). @return 삭제된 행 수 */
    @Modifying
    @Query("delete from PartyMember pm where pm.party.id = :partyId and pm.member.id = :memberId "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED")
    int deleteJoined(@Param("partyId") Long partyId, @Param("memberId") Long memberId);

    /** 내보내기. @return 상태가 바뀐 행 수 (참여 중이 아니면 0) */
    @Modifying
    @Query("update PartyMember pm set pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.KICKED "
            + "where pm.party.id = :partyId and pm.member.id = :memberId "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED")
    int kick(@Param("partyId") Long partyId, @Param("memberId") Long memberId);

    @Query("select pm.member.id from PartyMember pm where pm.party.id = :partyId "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED")
    List<Long> findJoinedMemberIdsByPartyId(@Param("partyId") Long partyId);

    @Query("select pm from PartyMember pm join fetch pm.member where pm.party.id = :partyId "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED "
            + "order by pm.joinedAt, pm.id")
    List<PartyMember> findAllJoinedWithMemberByPartyId(@Param("partyId") Long partyId);

    /** 목록의 currentCount 를 파티마다 세지 않고 한 번에 집계 */
    @Query("select pm.party.id as partyId, count(pm) as count from PartyMember pm "
            + "where pm.party.id in :partyIds "
            + "and pm.status = com.boardgame.reservation.party.domain.PartyMemberStatus.JOINED "
            + "group by pm.party.id")
    List<PartyCount> countJoinedByPartyIds(@Param("partyIds") Collection<Long> partyIds);

    interface PartyCount {
        Long getPartyId();

        long getCount();
    }
}
