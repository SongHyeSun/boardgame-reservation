package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.boardgame.service.BoardGameService;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.party.repository.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** DB/스프링 없이 서비스 로직만 빠르게 검증하는 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class BoardGameServiceTest {

    @Mock
    BoardGameRepository boardGameRepository;

    @Mock
    PartyRepository partyRepository;

    @InjectMocks
    BoardGameService boardGameService;

    private static BoardGameRequest request(String name, int min, int max) {
        return new BoardGameRequest(name, min, max, 60, Difficulty.NORMAL, "설명");
    }

    private static BoardGame existing() {
        return BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "설명");
    }

    @Test
    @DisplayName("등록 시 이름 앞뒤 공백이 제거되고 요청값 그대로 저장된다")
    void create_savesTrimmedName() {
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse response = boardGameService.create(request("  Catan  ", 3, 4));

        ArgumentCaptor<BoardGame> captor = ArgumentCaptor.forClass(BoardGame.class);
        verify(boardGameRepository).save(captor.capture());
        BoardGame saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Catan");
        assertThat(saved.getMinPlayers()).isEqualTo(3);
        assertThat(saved.getMaxPlayers()).isEqualTo(4);
        assertThat(saved.getDifficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(response.name()).isEqualTo("Catan");
    }

    @Test
    @DisplayName("등록 시 최소 인원이 최대 인원보다 크면 INVALID_PLAYER_RANGE, 저장하지 않는다")
    void create_minGreaterThanMax_throws() {
        assertThatThrownBy(() -> boardGameService.create(request("Catan", 5, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYER_RANGE);

        verify(boardGameRepository, never()).save(any());
    }

    @Test
    @DisplayName("최소 인원과 최대 인원이 같은 것은 허용된다")
    void create_minEqualsMax_ok() {
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse response = boardGameService.create(request("Duel", 2, 2));

        assertThat(response.minPlayers()).isEqualTo(2);
        assertThat(response.maxPlayers()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 id 조회 시 BOARDGAME_NOT_FOUND")
    void get_notFound_throws() {
        given(boardGameRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardGameService.getBoardGame(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);
    }

    @Test
    @DisplayName("수정 시 모든 필드가 요청값으로 교체된다")
    void update_replacesAllFields() {
        BoardGame boardGame = existing();
        given(boardGameRepository.findById(1L)).willReturn(Optional.of(boardGame));

        BoardGameResponse response = boardGameService.update(1L,
                new BoardGameRequest("Catan 2nd", 2, 6, 90, Difficulty.HARD, "수정됨"));

        assertThat(boardGame.getName()).isEqualTo("Catan 2nd");
        assertThat(boardGame.getMinPlayers()).isEqualTo(2);
        assertThat(boardGame.getMaxPlayers()).isEqualTo(6);
        assertThat(boardGame.getPlayTime()).isEqualTo(90);
        assertThat(boardGame.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(boardGame.getDescription()).isEqualTo("수정됨");
        assertThat(response.name()).isEqualTo("Catan 2nd");
    }

    @Test
    @DisplayName("수정 시 최소 인원이 최대 인원보다 크면 INVALID_PLAYER_RANGE, 조회도 하지 않는다")
    void update_minGreaterThanMax_throws() {
        assertThatThrownBy(() -> boardGameService.update(1L, request("Catan", 5, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYER_RANGE);

        verify(boardGameRepository, never()).findById(any());
    }

    @Test
    @DisplayName("없는 id 수정 시 BOARDGAME_NOT_FOUND")
    void update_notFound_throws() {
        given(boardGameRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardGameService.update(99L, request("Catan", 3, 4)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제 시 조회한 엔티티를 delete 한다")
    void delete_deletesEntity() {
        BoardGame boardGame = existing();
        given(boardGameRepository.findById(1L)).willReturn(Optional.of(boardGame));

        boardGameService.delete(1L);

        verify(boardGameRepository).delete(boardGame);
    }

    @Test
    @DisplayName("없는 id 삭제 시 BOARDGAME_NOT_FOUND, delete 호출 안 함")
    void delete_notFound_throws() {
        given(boardGameRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardGameService.delete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);

        verify(boardGameRepository, never()).delete(any(BoardGame.class));
    }

    @Test
    @DisplayName("파티가 있는 보드게임 삭제 시 BOARDGAME_IN_USE, delete 호출 안 함")
    void delete_inUse_throws() {
        given(boardGameRepository.findById(1L)).willReturn(Optional.of(existing()));
        given(partyRepository.existsByBoardGameId(1L)).willReturn(true);

        assertThatThrownBy(() -> boardGameService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_IN_USE);

        verify(boardGameRepository, never()).delete(any(BoardGame.class));
    }
}
