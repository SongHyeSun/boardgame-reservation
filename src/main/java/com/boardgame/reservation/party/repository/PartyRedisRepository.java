package com.boardgame.reservation.party.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 선착순 게이트용 Redis 키.
 *   party:{id}:remaining  남은 자리 (capacity - 1, 호스트 제외)
 *   party:{id}:members    참여자 memberId Set (중복 참여 빠른 차단)
 * 확정 인원의 기준은 항상 DB(PARTY_MEMBER)이고, 여기는 게이트 역할만 한다.
 */
@Repository
@RequiredArgsConstructor
public class PartyRedisRepository {

    private final StringRedisTemplate redis;

    private static String remainingKey(Long partyId) {
        return "party:" + partyId + ":remaining";
    }

    private static String membersKey(Long partyId) {
        return "party:" + partyId + ":members";
    }

    /** 파티 개설 직후(DB 커밋 이후) 키 세팅. members 먼저, remaining 나중. */
    public void init(Long partyId, long remaining, Collection<Long> memberIds) {
        addAllMembers(partyId, memberIds);
        redis.opsForValue().set(remainingKey(partyId), String.valueOf(remaining));
    }

    public boolean hasRemaining(Long partyId) {
        return Boolean.TRUE.equals(redis.hasKey(remainingKey(partyId)));
    }

    /** Redis 재시작 등으로 키가 사라졌을 때 DB 기준으로 재구성. members 먼저, remaining 은 NX 로 한 번만. */
    public void recover(Long partyId, long remaining, Collection<Long> memberIds) {
        addAllMembers(partyId, memberIds);
        redis.opsForValue().setIfAbsent(remainingKey(partyId), String.valueOf(remaining));
    }

    /** @return 새로 추가되면 true, 이미 있으면 false (중복 참여) */
    public boolean addMember(Long partyId, Long memberId) {
        Long added = redis.opsForSet().add(membersKey(partyId), String.valueOf(memberId));
        return added != null && added == 1L;
    }

    /** @return 감소 후 남은 자리 (음수면 정원 초과) */
    public long decrement(Long partyId) {
        Long remaining = redis.opsForValue().decrement(remainingKey(partyId));
        return remaining == null ? -1 : remaining;
    }

    /** join 보상: 자리 원복 + 참여자 제거 */
    public void rollbackJoin(Long partyId, Long memberId) {
        restoreRemaining(partyId);
        redis.opsForSet().remove(membersKey(partyId), String.valueOf(memberId));
    }

    /** 자리만 원복. UNIQUE 위반처럼 이미 실제 참여자인 경우 members Set 은 그대로 둔다. */
    public void restoreRemaining(Long partyId) {
        redis.opsForValue().increment(remainingKey(partyId));
    }

    /** leave 반영: 키가 있을 때만 자리 반환(없으면 다음 join 의 복구가 DB 기준으로 채운다) */
    public void release(Long partyId, Long memberId) {
        if (hasRemaining(partyId)) {
            redis.opsForValue().increment(remainingKey(partyId));
        }
        redis.opsForSet().remove(membersKey(partyId), String.valueOf(memberId));
    }

    public void delete(Long partyId) {
        redis.delete(List.of(remainingKey(partyId), membersKey(partyId)));
    }

    private void addAllMembers(Long partyId, Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        String[] values = memberIds.stream().map(String::valueOf).toArray(String[]::new);
        redis.opsForSet().add(membersKey(partyId), values);
    }
}
