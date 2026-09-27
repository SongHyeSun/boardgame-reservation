package com.boardgame.reservation.boardgame.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.boardgame.domain.YoutubeUrlParser;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.boardgame.repository.BoardGameSpecification;
import com.boardgame.reservation.global.common.TransactionCallbacks;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.global.file.FileStorage;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import com.boardgame.reservation.party.repository.PartyRedisRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationOccupancy;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 게임 수정·이미지·숨기기는 등록한 관리자(createdBy) 본인만 할 수 있다 (SUPER_ADMIN 도 남의 게임은 불가).
 * 파일은 모든 규칙을 검증한 뒤에 저장하고, 롤백되면 지우고, 교체된 옛 파일은 커밋 이후에 지운다 (MemberService 와 같은 방식).
 *
 * 수정·숨기기는 게임 행 비관적 락(findByIdForUpdate)을 잡고 시작한다. 예약 신청(ReservationService.create)이 같은 락을 쓰므로
 * 재고를 바꾸거나 숨기는 도중에 신청이 끼어들 수 없다. 락 조회가 그 트랜잭션에서 게임을 처음 읽는 쿼리여야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardGameService {

    private final BoardGameRepository boardGameRepository;
    private final PartyRepository partyRepository;
    private final MemberRepository memberRepository;
    private final PartyRedisRepository partyRedisRepository;
    private final ReservationRepository reservationRepository;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** @param ownerId null 이면 숨기지 않은 게임만, 있으면 그 관리자가 등록한 게임 전부(숨김 포함) */
    public List<BoardGameResponse> search(Integer players, Difficulty difficulty, String keyword,
                                          PlayMode playMode, Long ownerId) {
        return boardGameRepository
                .findAll(BoardGameSpecification.search(players, difficulty, keyword, playMode, ownerId), Sort.by("id"))
                .stream()
                .map(BoardGameResponse::from)
                .toList();
    }

    /** 숨긴 게임도 반환한다 (파티·예약 이력에서 링크되므로) */
    public BoardGameResponse getBoardGame(Long id) {
        return BoardGameResponse.from(findOrThrow(id));
    }

    @Transactional
    public BoardGameResponse create(Long memberId, BoardGameRequest request, MultipartFile image) {
        validatePlayerRange(request);
        String youtubeVideoId = YoutubeUrlParser.parse(request.youtubeUrl());
        Member owner = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        BoardGame boardGame = BoardGame.create(request.toDetails(), owner);
        boardGame.changeYoutube(youtubeVideoId);
        if (image != null) {
            boardGame.changeImage(storeImage(image));
        }
        return BoardGameResponse.from(boardGameRepository.save(boardGame));
    }

    /**
     * PUT: 전체 교체. 변경 감지(dirty checking)로 UPDATE 된다.
     * 재고를 줄일 때는 오늘 이후 최대 점유 수보다 작게 못 줄이고(STOCK_BELOW_RESERVED),
     * 오프라인을 끌 때는 아직 끝나지 않은 활성 예약이 있으면 막는다(PLAY_MODE_IN_USE, 자동 취소 없음).
     */
    @Transactional
    public BoardGameResponse update(Long id, Long memberId, BoardGameRequest request, MultipartFile image) {
        BoardGame boardGame = lockOwnedOrThrow(id, memberId);
        validatePlayerRange(request);
        if (image != null && request.imageRemoved()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String youtubeVideoId = YoutubeUrlParser.parse(request.youtubeUrl());

        boolean wasOnline = boardGame.isOnlineAvailable();
        boolean wasOffline = boardGame.isOfflineAvailable();
        int oldStock = boardGame.getStock();
        boardGame.update(request.toDetails());
        // 값 검증(400)이 먼저 나가도록 update 뒤에 확인한다. 예외가 나면 트랜잭션이 롤백돼 변경은 버려진다
        ensureDisabledModesNotInUse(boardGame, wasOnline, wasOffline);
        ensureReservationsAllowChange(boardGame, wasOffline, oldStock);
        boardGame.changeYoutube(youtubeVideoId);

        String oldKey = boardGame.getImageKey();
        if (image != null) {
            boardGame.changeImage(storeImage(image));
            deleteAfterCommit(oldKey);
        } else if (request.imageRemoved()) {
            boardGame.removeImage();
            deleteAfterCommit(oldKey);
        }
        return BoardGameResponse.from(boardGame);
    }

    /**
     * 숨기기(운영 중지) / 다시 보이기. 이미 같은 상태면 아무 것도 바꾸지 않는다.
     * 숨기면 한 트랜잭션에서 게임을 숨기고 RECRUITING 파티를 모두 취소하며, 아직 끝나지 않은(endDate >= 오늘) 활성 예약도
     * GAME_SUSPENDED 로 취소한다 (이미 끝난 예약은 이력으로 유지, CLOSED 파티도 유지).
     * 취소된 파티의 Redis 키는 커밋 이후에 지운다. 다시 보여도 취소된 파티·예약은 복구하지 않는다.
     */
    @Transactional
    public BoardGameResponse changeVisibility(Long id, Long memberId, boolean visible) {
        BoardGame boardGame = lockOwnedOrThrow(id, memberId);
        if (boardGame.isVisible() == visible) {
            return BoardGameResponse.from(boardGame);
        }
        if (visible) {
            boardGame.show();
            return BoardGameResponse.from(boardGame);
        }

        boardGame.hide();
        List<Party> recruiting = partyRepository.findByBoardGameIdAndStatus(id, PartyStatus.RECRUITING);
        recruiting.forEach(Party::cancel);
        List<Long> cancelledPartyIds = recruiting.stream().map(Party::getId).toList();

        List<Reservation> upcoming = reservationRepository.findActiveEndingOnOrAfter(id, LocalDate.now(clock));
        upcoming.forEach(reservation -> reservation.cancel(CancelReason.GAME_SUSPENDED));
        List<Long> cancelledReservationIds = upcoming.stream().map(Reservation::getId).toList();

        TransactionCallbacks.afterCommit(() -> deleteRedisKeys(cancelledPartyIds));
        eventPublisher.publishEvent(new BoardGameSuspendedEvent(id, cancelledPartyIds, cancelledReservationIds));
        return BoardGameResponse.from(boardGame);
    }

    // ───────────── 내부 헬퍼 ─────────────

    private BoardGame findOrThrow(Long id) {
        return boardGameRepository.findWithOwnerById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
    }

    /**
     * 게임 행을 잠그고(SELECT … FOR UPDATE) 소유자인지 확인한다. 예약 신청과 같은 락이다.
     * findWithOwnerById 로 먼저 읽으면 1차 캐시의 옛 재고가 재사용되므로 수정·숨기기는 이 메서드로만 게임을 읽는다.
     */
    private BoardGame lockOwnedOrThrow(Long id, Long memberId) {
        BoardGame boardGame = boardGameRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
        if (!boardGame.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_GAME_OWNER);
        }
        return boardGame;
    }

    /**
     * 예약 때문에 막아야 하는 수정 (자동 취소하지 않음).
     * - 오프라인 끄기: 종료일이 오늘 이후인 활성 예약이 있으면 PLAY_MODE_IN_USE (끄면 재고가 0 이 되므로 재고 검사보다 먼저)
     * - 재고 줄이기: 오늘 이후 날짜 중 최대 점유 수보다 작게 못 줄임 (STOCK_BELOW_RESERVED)
     */
    private void ensureReservationsAllowChange(BoardGame boardGame, boolean wasOffline, int oldStock) {
        LocalDate today = LocalDate.now(clock);
        if (wasOffline && !boardGame.isOfflineAvailable()) {
            if (reservationRepository.existsActiveEndingOnOrAfter(boardGame.getId(), today)) {
                // 상태코드는 PLAY_MODE_IN_USE(409) 그대로, 메시지만 예약 기준으로 (기존 메시지는 파티 전용)
                throw new BusinessException(ErrorCode.PLAY_MODE_IN_USE, "남은 대여 예약이 있어 오프라인 진행을 끌 수 없습니다.");
            }
            return;
        }
        if (boardGame.isOfflineAvailable() && boardGame.getStock() < oldStock) {
            int maxOccupied = ReservationOccupancy.maxOccupiedFrom(
                    reservationRepository.findActiveEndingOnOrAfter(boardGame.getId(), today), today);
            if (boardGame.getStock() < maxOccupied) {
                throw new BusinessException(ErrorCode.STOCK_BELOW_RESERVED);
            }
        }
    }

    /** 이번 수정으로 꺼지는 진행 방식에 모집 중(RECRUITING)인 파티가 있으면 막는다 (자동 취소하지 않음) */
    private void ensureDisabledModesNotInUse(BoardGame boardGame, boolean wasOnline, boolean wasOffline) {
        if (wasOnline && !boardGame.isOnlineAvailable() && isRecruitingIn(boardGame, PlayMode.ONLINE)
                || wasOffline && !boardGame.isOfflineAvailable() && isRecruitingIn(boardGame, PlayMode.OFFLINE)) {
            throw new BusinessException(ErrorCode.PLAY_MODE_IN_USE);
        }
    }

    private boolean isRecruitingIn(BoardGame boardGame, PlayMode playMode) {
        return partyRepository.existsByBoardGameIdAndPlayModeAndStatus(
                boardGame.getId(), playMode, PartyStatus.RECRUITING);
    }

    private void validatePlayerRange(BoardGameRequest request) {
        if (request.minPlayers() > request.maxPlayers()) {
            throw new BusinessException(ErrorCode.INVALID_PLAYER_RANGE);
        }
    }

    /** 검증·저장 후 key 를 돌려주고, 트랜잭션이 롤백되면 그 파일을 지우도록 등록 */
    private String storeImage(MultipartFile image) {
        String key = fileStorage.store(image, FileKeys.BOARDGAMES);
        TransactionCallbacks.afterRollback(() -> fileStorage.delete(key));
        return key;
    }

    /** 이전 파일 삭제는 DB 커밋 이후에 (커밋 전에 지우면 롤백 시 이미지만 사라진다) */
    private void deleteAfterCommit(String key) {
        if (key != null) {
            TransactionCallbacks.afterCommit(() -> fileStorage.delete(key));
        }
    }

    /**
     * 커밋 이후라 실패해도 롤백할 수 없다. 예외가 새면 "숨겨졌는데 500" 이 되므로 로그만 남긴다
     * (남은 키는 join 이 DB status 를 먼저 검사해 무해).
     */
    private void deleteRedisKeys(List<Long> partyIds) {
        for (Long partyId : partyIds) {
            try {
                partyRedisRepository.delete(partyId);
            } catch (RuntimeException e) {
                log.warn("failed to delete redis keys of cancelled party: {}", partyId, e);
            }
        }
    }
}
