package com.boardgame.reservation.boardgame.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.boardgame.repository.BoardGameSpecification;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardGameService {

    private final BoardGameRepository boardGameRepository;

    public List<BoardGameResponse> search(Integer players, Difficulty difficulty, String keyword) {
        return boardGameRepository
                .findAll(BoardGameSpecification.search(players, difficulty, keyword), Sort.by("id"))
                .stream()
                .map(BoardGameResponse::from)
                .toList();
    }

    public BoardGameResponse getBoardGame(Long id) {
        return BoardGameResponse.from(findOrThrow(id));
    }

    @Transactional
    public BoardGameResponse create(BoardGameRequest request) {
        validatePlayerRange(request);

        BoardGame boardGame = BoardGame.create(
                request.name().trim(),
                request.minPlayers(),
                request.maxPlayers(),
                request.playTime(),
                request.difficulty(),
                request.description());
        return BoardGameResponse.from(boardGameRepository.save(boardGame));
    }

    /** PUT: 전체 교체. 변경 감지(dirty checking)로 UPDATE 된다. */
    @Transactional
    public BoardGameResponse update(Long id, BoardGameRequest request) {
        validatePlayerRange(request);

        BoardGame boardGame = findOrThrow(id);
        boardGame.update(
                request.name().trim(),
                request.minPlayers(),
                request.maxPlayers(),
                request.playTime(),
                request.difficulty(),
                request.description());
        return BoardGameResponse.from(boardGame);
    }

    @Transactional
    public void delete(Long id) {
        boardGameRepository.delete(findOrThrow(id));
    }

    private BoardGame findOrThrow(Long id) {
        return boardGameRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
    }

    private void validatePlayerRange(BoardGameRequest request) {
        if (request.minPlayers() > request.maxPlayers()) {
            throw new BusinessException(ErrorCode.INVALID_PLAYER_RANGE);
        }
    }
}
