package com.boardgame.reservation.party.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.domain.PartyMemberStatus;
import com.boardgame.reservation.party.domain.PartyPolicy;
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
    public List<PartyResponse> search(PartyStatus status, Long boardGameId, PlayMode playMode) {
        List<Party> parties = partyRepository.findAll(
                PartySpecification.search(status, boardGameId, playMode), Sort.by(Sort.Direction.DESC, "id"));
        if (parties.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> counts = partyMemberRepository
                .countJoinedByPartyIds(parties.stream().map(Party::getId).toList())
                .stream()
                .collect(Collectors.toMap(PartyCount::getPartyId, PartyCount::getCount));

        return parties.stream()
                .map(party -> PartyResponse.of(party, counts.getOrDefault(party.getId(), 0L)))
                .toList();
    }

    /** @param viewerId 조회자 id (비로그인이면 null). 접속 링크는 호스트·참여자에게만 보인다 */
    @Transactional(readOnly = true)
    public PartyDetailResponse getParty(Long partyId, Long viewerId) {
        Party party = findWithDetailsOrThrow(partyId);
        return PartyDetailResponse.of(party, partyMemberRepository.findAllJoinedWithMemberByPartyId(partyId), viewerId);
    }

    // ───────────── 개설 / 마감 ─────────────

    /**
     * 게임은 등록된 보드게임(boardGameId) 또는 기타 게임 이름(customGameName) 중 정확히 하나.
     * 호스트는 개설과 동시에 첫 참여자. Redis 키는 DB 커밋 이후에만 세팅한다.
     */
    @Transactional
    public PartyResponse create(Long hostId, PartyCreateRequest request) {
        boolean hasBoardGame = request.boardGameId() != null;
        boolean hasCustomGame = request.customGameName() != null && !request.customGameName().isBlank();
        if (hasBoardGame == hasCustomGame) {
            throw new BusinessException(ErrorCode.INVALID_GAME_SELECTION);
        }
        Party.PlayInfo play = Party.PlayInfo.of(
                request.playMode(), request.onlinePlatform(), request.onlineLink(), request.location());

        Party newParty = hasBoardGame
                ? newBoardGameParty(hostId, request, play)
                : newCustomGameParty(hostId, request, play);
        Party party = partyRepository.save(newParty);
        partyMemberRepository.save(PartyMember.create(party, party.getHost()));

        Long partyId = party.getId();
        int capacity = party.getCapacity();
        runAfterCommit(() -> partyRedisRepository.init(partyId, capacity - 1L, List.of(hostId)));
        return PartyResponse.of(party, 1);
    }

    private Party newBoardGameParty(Long hostId, PartyCreateRequest request, Party.PlayInfo play) {
        BoardGame boardGame = boardGameRepository.findById(request.boardGameId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
        if (!boardGame.isVisible()) {
            throw new BusinessException(ErrorCode.BOARDGAME_NOT_AVAILABLE);
        }
        Member host = findHostOrThrow(hostId);

        int capacity = request.capacity();
        if (capacity < boardGame.getMinPlayers() || capacity > boardGame.getMaxPlayers()) {
            throw new BusinessException(ErrorCode.INVALID_CAPACITY);
        }
        if (!boardGame.supports(play.mode())) {
            throw new BusinessException(ErrorCode.PLAY_MODE_NOT_SUPPORTED);
        }
        return Party.createWithBoardGame(boardGame, host, request.title().trim(), request.description(),
                capacity, request.playAt(), play);
    }

    /** 기타 게임은 게임별 인원 범위·방식 제한이 없다: 정원만 PartyPolicy 의 고정 범위 */
    private Party newCustomGameParty(Long hostId, PartyCreateRequest request, Party.PlayInfo play) {
        Member host = findHostOrThrow(hostId);

        int capacity = request.capacity();
        if (capacity < PartyPolicy.CUSTOM_GAME_MIN_CAPACITY || capacity > PartyPolicy.CUSTOM_GAME_MAX_CAPACITY) {
            throw new BusinessException(ErrorCode.INVALID_CAPACITY);
        }
        return Party.createWithCustomGame(request.customGameName(), host, request.title().trim(),
                request.description(), capacity, request.playAt(), play);
    }

    @Transactional
    public void close(Long partyId, Long memberId) {
        Party party = findWithDetailsOrThrow(partyId);
        if (!party.isHost(memberId)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_HOST);
        }
        // 게임 운영 중지로 CANCELLED 가 된 파티를 CLOSED 로 덮어쓰지 않도록 (docs/troubleshooting.md 6번)
        if (!party.isRecruiting()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_RECRUITING);
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
        // 내보내진 회원은 Redis 를 건드리기 전에 막는다 (KICKED 행이 남아 있는 한 DB UNIQUE 도 재참여를 막는다)
        if (isKicked(partyId, memberId)) {
            throw new BusinessException(ErrorCode.KICKED_FROM_PARTY);
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
            // 내보내기 커밋과 이 요청의 SADD 가 엇갈린 경합: 자리와 members 를 모두 되돌리고 내보내진 회원으로 응답
            if (isKicked(partyId, memberId)) {
                partyRedisRepository.rollbackJoin(partyId, memberId);
                throw new BusinessException(ErrorCode.KICKED_FROM_PARTY);
            }
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

    /**
     * 호스트가 참여자를 내보낸다 (트랜잭션 없음: leave 와 같은 Writer + Redis 조합).
     * 행은 KICKED 로 남겨 재참여를 막고, 자리는 Redis 에 돌려준다.
     */
    public void kick(Long partyId, Long hostId, Long targetMemberId) {
        Party party = findWithDetailsOrThrow(partyId);
        if (!party.isHost(hostId)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_HOST);
        }
        if (!party.isRecruiting()) {
            throw new BusinessException(ErrorCode.PARTY_NOT_RECRUITING);
        }
        if (party.isHost(targetMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_HOST);
        }
        if (!partyMemberWriter.kick(partyId, targetMemberId)) {
            throw new BusinessException(ErrorCode.NOT_JOINED);
        }
        partyRedisRepository.release(partyId, targetMemberId);
    }

    // ───────────── 내부 ─────────────

    /** remaining 키가 없으면(Redis 재시작 등) DB 기준으로 재구성: members 먼저, remaining(NX) 나중. 내보내진(KICKED) 회원은 제외 */
    private void recoverKeysIfAbsent(Party party) {
        Long partyId = party.getId();
        if (partyRedisRepository.hasRemaining(partyId)) {
            return;
        }
        List<Long> memberIds = partyMemberRepository.findJoinedMemberIdsByPartyId(partyId);
        partyRedisRepository.recover(partyId, party.getCapacity() - (long) memberIds.size(), memberIds);
    }

    private boolean isKicked(Long partyId, Long memberId) {
        return partyMemberRepository.existsByPartyIdAndMemberIdAndStatus(partyId, memberId, PartyMemberStatus.KICKED);
    }

    private Member findHostOrThrow(Long hostId) {
        return memberRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
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
