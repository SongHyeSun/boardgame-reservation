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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 게임 수정·이미지·숨기기는 등록한 관리자(createdBy) 본인만 할 수 있다 (SUPER_ADMIN 도 남의 게임은 불가).
 * 파일은 모든 규칙을 검증한 뒤에 저장하고, 롤백되면 지우고, 교체된 옛 파일은 커밋 이후에 지운다 (MemberService 와 같은 방식).
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
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;

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

    /** PUT: 전체 교체. 변경 감지(dirty checking)로 UPDATE 된다. */
    @Transactional
    public BoardGameResponse update(Long id, Long memberId, BoardGameRequest request, MultipartFile image) {
        BoardGame boardGame = findOwnedOrThrow(id, memberId);
        validatePlayerRange(request);
        if (image != null && request.imageRemoved()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String youtubeVideoId = YoutubeUrlParser.parse(request.youtubeUrl());

        boardGame.update(request.toDetails());
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
     * 숨기면 한 트랜잭션에서 게임을 숨기고 RECRUITING 파티를 모두 취소한다 (CLOSED 는 이력으로 유지).
     * 취소된 파티의 Redis 키는 커밋 이후에 지운다. 다시 보여도 취소된 파티는 복구하지 않는다.
     */
    @Transactional
    public BoardGameResponse changeVisibility(Long id, Long memberId, boolean visible) {
        BoardGame boardGame = findOwnedOrThrow(id, memberId);
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

        TransactionCallbacks.afterCommit(() -> deleteRedisKeys(cancelledPartyIds));
        eventPublisher.publishEvent(new BoardGameSuspendedEvent(id, cancelledPartyIds));
        return BoardGameResponse.from(boardGame);
    }

    // ───────────── 내부 헬퍼 ─────────────

    private BoardGame findOrThrow(Long id) {
        return boardGameRepository.findWithOwnerById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
    }

    private BoardGame findOwnedOrThrow(Long id, Long memberId) {
        BoardGame boardGame = findOrThrow(id);
        if (!boardGame.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_GAME_OWNER);
        }
        return boardGame;
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
