package com.boardgame.reservation.party.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.domain.PartyStatus;
import com.boardgame.reservation.party.dto.JoinResponse;
import com.boardgame.reservation.party.dto.PartyCreateRequest;
import com.boardgame.reservation.party.dto.PartyDetailResponse;
import com.boardgame.reservation.party.dto.PartyResponse;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyMemberRepository.PartyCount;
import com.boardgame.reservation.party.repository.PartyRedisRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.party.repository.PartySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * join/leave 는 Redis 연산과 DB 트랜잭션을 한 트랜잭션에 섞지 않는다.
 * DB 쓰기는 PartyMemberWriter(별도 빈, 자체 @Transactional)에서 끝내고,
 * 여기서는 그 결과(성공/예외)를 보고 Redis 반영·보상을 수행한다.
 */
@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final PartyMemberWriter partyMemberWriter;
    private final PartyRedisRepository partyRedisRepository;

    // ───────────── 조회 ─────────────

    @Transactional(readOnly = true)
    public List<PartyResponse> search(PartyStatus status, Long boardGameId) {
        List<Party> parties = partyRepository.findAll(
                PartySpecification.search(status, boardGameId), Sort.by(Sort.Direction.DESC, "id"));
        if (parties.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> counts = partyMemberRepository
                .countByPartyIds(parties.stream().map(Party::getId).toList())
                .stream()
                .collect(Collectors.toMap(PartyCount::getPartyId, PartyCount::getCount));

        return parties.stream()
                .map(party -> PartyResponse.of(party, counts.getOrDefault(party.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartyDetailResponse getParty(Long partyId) {
        Party party = findWithDetailsOrThrow(partyId);
        return PartyDetailResponse.of(party, partyMemberRepository.findAllWithMemberByPartyId(partyId));
    }

    // ───────────── 개설 / 마감 ─────────────

    /** 호스트는 개설과 동시에 첫 참여자. Redis 키는 DB 커밋 이후에만 세팅한다. */
    @Transactional
    public PartyResponse create(Long hostId, PartyCreateRequest request) {
        BoardGame boardGame = boardGameRepository.findById(request.boardGameId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
        Member host = memberRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        int capacity = request.capacity();
        if (capacity < boardGame.getMinPlayers() || capacity > boardGame.getMaxPlayers()) {
            throw new BusinessException(ErrorCode.INVALID_CAPACITY);
        }

        Party party = partyRepository.save(Party.create(
                boardGame, host, request.title().trim(), request.description(), capacity, request.playAt()));
        partyMemberRepository.save(PartyMember.create(party, host));

        Long partyId = party.getId();
        runAfterCommit(() -> partyRedisRepository.init(partyId, capacity - 1L, List.of(hostId)));
        return PartyResponse.of(party, 1);
    }

    @Transactional
    public void close(Long partyId, Long memberId) {
        Party party = findWithDetailsOrThrow(partyId);
        if (!party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_HOST);
        }
        party.close();
        runAfterCommit(() -> partyRedisRepository.delete(partyId));
    }

    // ───────────── 참여 / 취소 (트랜잭션 없음: Redis + Writer 조합) ─────────────

    public JoinResponse join(Long partyId, Long memberId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (!party.isRecruiting()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_RECRUITING);
        }

        recoverKeysIfAbsent(party);

        if (!partyRedisRepository.addMember(partyId, memberId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }

        long remaining = partyRedisRepository.decrement(partyId);
        if (remaining < 0) {
            partyRedisRepository.rollbackJoin(partyId, memberId);
            throw new BusinessException(ErrorCode.PARTY_FULL);
        }

        try {
            partyMemberWriter.add(partyId, memberId);
        } catch (DataIntegrityViolationException e) {
            // 이미 실제 참여자(UNIQUE 위반): 자리만 되돌리고 members Set 에는 남겨둔다
            partyRedisRepository.restoreRemaining(partyId);
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        } catch (RuntimeException e) {
            partyRedisRepository.rollbackJoin(partyId, memberId);
            throw e;
        }
        return new JoinResponse(remaining);
    }

    public void leave(Long partyId, Long memberId) {
        Party party = findWithDetailsOrThrow(partyId);
        if (!party.isRecruiting()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_RECRUITING);
        }
        if (party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.HOST_CANNOT_LEAVE);
        }
        if (!partyMemberWriter.remove(partyId, memberId)) {
            throw new BusinessException(ErrorCode.NOT_JOINED);
        }
        partyRedisRepository.release(partyId, memberId);
    }

    // ───────────── 내부 ─────────────

    /** remaining 키가 없으면(Redis 재시작 등) DB 기준으로 재구성: members 먼저, remaining(NX) 나중 */
    private void recoverKeysIfAbsent(Party party) {
        Long partyId = party.getId();
        if (partyRedisRepository.hasRemaining(partyId)) {
            return;
        }
        List<Long> memberIds = partyMemberRepository.findMemberIdsByPartyId(partyId);
        partyRedisRepository.recover(partyId, party.getCapacity() - (long) memberIds.size(), memberIds);
    }

    private Party findWithDetailsOrThrow(Long partyId) {
        return partyRepository.findWithDetailsById(partyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
    }

    /** 트랜잭션 안이면 커밋 이후, 아니면(단위 테스트 등) 즉시 실행 */
    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
