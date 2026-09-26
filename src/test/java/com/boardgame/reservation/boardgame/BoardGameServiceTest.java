package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.boardgame.service.BoardGameService;
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
import com.boardgame.reservation.support.ImageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * DB/스프링 없이 서비스 로직만 빠르게 검증하는 단위 테스트.
 * 트랜잭션이 없으므로 TransactionCallbacks.afterCommit 은 즉시 실행되고 afterRollback 은 실행되지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class BoardGameServiceTest {

    private static final long OWNER_ID = 7L;
    private static final long OTHER_ID = 8L;

    @Mock
    BoardGameRepository boardGameRepository;
    @Mock
    PartyRepository partyRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    PartyRedisRepository partyRedisRepository;
    @Mock
    FileStorage fileStorage;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    BoardGameService boardGameService;

    // ───────────── fixture ─────────────

    private static Member member(long id) {
        Member member = Member.createAdmin("admin" + id + "@test.com", "pw", "관리자" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static BoardGameRequest request(String name, int min, int max) {
        return request(name, min, max, true, false, 3, null, null);
    }

    private static BoardGameRequest request(String name, int min, int max, boolean offline, boolean online,
                                            int stock, String youtubeUrl, Boolean removeImage) {
        return new BoardGameRequest(name, min, max, 60, Difficulty.NORMAL, "설명",
                offline, online, stock, youtubeUrl, removeImage);
    }

    private static BoardGame ownedGame(Member owner) {
        return BoardGame.create(
                new BoardGame.Details("Catan", 3, 4, 60, Difficulty.NORMAL, "설명", true, false, 2), owner);
    }

    private static Party recruitingParty(long id, BoardGame game) {
        Party party = Party.create(game, member(99), "같이 해요", null, 4, null);
        ReflectionTestUtils.setField(party, "id", id);
        return party;
    }

    private void givenOwnedGame(BoardGame game) {
        given(boardGameRepository.findWithOwnerById(1L)).willReturn(Optional.of(game));
    }

    private static BusinessException thrown(Runnable action) {
        return (BusinessException) catchThrowable(action::run);
    }

    // ───────────── 등록 ─────────────

    @Test
    @DisplayName("등록 시 이름 앞뒤 공백이 제거되고 요청값·소유 관리자가 그대로 저장된다")
    void create_savesTrimmedNameAndOwner() {
        Member owner = member(OWNER_ID);
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(owner));
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse response = boardGameService.create(OWNER_ID, request("  Catan  ", 3, 4), null);

        ArgumentCaptor<BoardGame> captor = ArgumentCaptor.forClass(BoardGame.class);
        verify(boardGameRepository).save(captor.capture());
        BoardGame saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Catan");
        assertThat(saved.getMinPlayers()).isEqualTo(3);
        assertThat(saved.getMaxPlayers()).isEqualTo(4);
        assertThat(saved.getDifficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(saved.getCreatedBy()).isSameAs(owner);
        assertThat(saved.isVisible()).isTrue();
        assertThat(saved.getImageKey()).isNull();
        assertThat(saved.getYoutubeVideoId()).isNull();
        assertThat(response.name()).isEqualTo("Catan");
        assertThat(response.owner().id()).isEqualTo(OWNER_ID);
        assertThat(response.owner().nickname()).isEqualTo("관리자7");
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("등록 시 최소 인원이 최대 인원보다 크면 INVALID_PLAYER_RANGE, 저장하지 않는다")
    void create_minGreaterThanMax_throws() {
        assertThat(thrown(() -> boardGameService.create(OWNER_ID, request("Catan", 5, 3), null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYER_RANGE);

        verify(boardGameRepository, never()).save(any());
    }

    @Test
    @DisplayName("최소 인원과 최대 인원이 같은 것은 허용된다")
    void create_minEqualsMax_ok() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse response = boardGameService.create(OWNER_ID, request("Duel", 2, 2), null);

        assertThat(response.minPlayers()).isEqualTo(2);
        assertThat(response.maxPlayers()).isEqualTo(2);
    }

    @Test
    @DisplayName("온라인·오프라인 둘 다 false 면 INVALID_PLAY_MODE, 저장하지 않는다")
    void create_noPlayMode_throws() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));

        assertThat(thrown(() -> boardGameService.create(
                OWNER_ID, request("Catan", 3, 4, false, false, 0, null, null), null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAY_MODE);

        verify(boardGameRepository, never()).save(any());
    }

    @Test
    @DisplayName("오프라인 가능인데 재고가 0 이면 INVALID_STOCK")
    void create_offlineWithoutStock_throws() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));

        assertThat(thrown(() -> boardGameService.create(
                OWNER_ID, request("Catan", 3, 4, true, true, 0, null, null), null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STOCK);

        verify(boardGameRepository, never()).save(any());
    }

    @Test
    @DisplayName("온라인 전용이면 요청 재고와 관계없이 0 으로 저장, 둘 다 가능이면 재고 유지")
    void create_stockByPlayMode() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse onlineOnly = boardGameService.create(
                OWNER_ID, request("Online", 2, 4, false, true, 5, null, null), null);
        BoardGameResponse both = boardGameService.create(
                OWNER_ID, request("Both", 2, 4, true, true, 5, null, null), null);

        assertThat(onlineOnly.stock()).isZero();
        assertThat(onlineOnly.onlineAvailable()).isTrue();
        assertThat(onlineOnly.offlineAvailable()).isFalse();
        assertThat(both.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("유튜브 링크는 영상 ID 만 저장한다")
    void create_storesYoutubeVideoId() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));

        BoardGameResponse response = boardGameService.create(OWNER_ID,
                request("Catan", 3, 4, true, false, 1, "https://youtu.be/dQw4w9WgXcQ?si=abc", null), null);

        assertThat(response.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("잘못된 유튜브 링크는 INVALID_YOUTUBE_URL, 이미지는 저장하지 않는다")
    void create_invalidYoutube_throwsBeforeStoringImage() {
        MockMultipartFile image = ImageFixtures.jpeg("image");

        assertThat(thrown(() -> boardGameService.create(OWNER_ID,
                request("Catan", 3, 4, true, false, 1, "https://vimeo.com/123", null), image)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_YOUTUBE_URL);

        verifyNoInteractions(fileStorage);
        verify(boardGameRepository, never()).save(any());
    }

    @Test
    @DisplayName("진행 방식 규칙을 어기면 이미지는 저장하지 않는다")
    void create_invalidPlayMode_doesNotStoreImage() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));

        assertThat(thrown(() -> boardGameService.create(OWNER_ID,
                request("Catan", 3, 4, false, false, 0, null, null), ImageFixtures.jpeg("image"))).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAY_MODE);

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("이미지가 있으면 boardgames 디렉터리에 저장하고 key 를 기록한다 (응답은 imageUrl)")
    void create_storesImage() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.of(member(OWNER_ID)));
        given(boardGameRepository.save(any(BoardGame.class))).willAnswer(inv -> inv.getArgument(0));
        String key = "boardgames/3f2b0c1e-1111-2222-3333-444455556666.jpg";
        given(fileStorage.store(any(), eq(FileKeys.BOARDGAMES))).willReturn(key);

        BoardGameResponse response = boardGameService.create(OWNER_ID, request("Catan", 3, 4), ImageFixtures.jpeg("image"));

        assertThat(response.imageUrl()).isEqualTo("/api/files/" + key);
    }

    @Test
    @DisplayName("없는 회원이면 MEMBER_NOT_FOUND")
    void create_memberNotFound_throws() {
        given(memberRepository.findById(OWNER_ID)).willReturn(Optional.empty());

        assertThat(thrown(() -> boardGameService.create(OWNER_ID, request("Catan", 3, 4), null)).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ───────────── 조회 ─────────────

    @Test
    @DisplayName("없는 id 조회 시 BOARDGAME_NOT_FOUND")
    void get_notFound_throws() {
        given(boardGameRepository.findWithOwnerById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardGameService.getBoardGame(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);
    }

    @Test
    @DisplayName("숨긴 게임도 상세로 반환한다 (visible=false)")
    void get_hiddenGame_returned() {
        BoardGame game = ownedGame(member(OWNER_ID));
        game.hide();
        givenOwnedGame(game);

        assertThat(boardGameService.getBoardGame(1L).visible()).isFalse();
    }

    @Test
    @DisplayName("등록 관리자가 없는(레거시) 게임도 owner=null 로 조회된다")
    void get_legacyGame_ownerNull() {
        given(boardGameRepository.findWithOwnerById(1L))
                .willReturn(Optional.of(BoardGame.create("Old", 2, 4, 30, Difficulty.EASY, "옛날 게임")));

        BoardGameResponse response = boardGameService.getBoardGame(1L);

        assertThat(response.owner()).isNull();
        assertThat(response.offlineAvailable()).isTrue();
        assertThat(response.onlineAvailable()).isFalse();
        assertThat(response.visible()).isTrue();
    }

    // ───────────── 수정 ─────────────

    @Test
    @DisplayName("소유자가 수정하면 모든 필드가 요청값으로 교체된다")
    void update_replacesAllFields() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);

        BoardGameResponse response = boardGameService.update(1L, OWNER_ID,
                new BoardGameRequest("Catan 2nd", 2, 6, 90, Difficulty.HARD, "수정됨",
                        true, true, 4, "https://youtu.be/dQw4w9WgXcQ", null), null);

        assertThat(game.getName()).isEqualTo("Catan 2nd");
        assertThat(game.getMinPlayers()).isEqualTo(2);
        assertThat(game.getMaxPlayers()).isEqualTo(6);
        assertThat(game.getPlayTime()).isEqualTo(90);
        assertThat(game.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(game.getDescription()).isEqualTo("수정됨");
        assertThat(game.isOnlineAvailable()).isTrue();
        assertThat(game.getStock()).isEqualTo(4);
        assertThat(game.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(response.name()).isEqualTo("Catan 2nd");
    }

    @Test
    @DisplayName("소유자가 아니면 NOT_GAME_OWNER, 아무 것도 바뀌지 않는다")
    void update_notOwner_throws() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);

        assertThat(thrown(() -> boardGameService.update(1L, OTHER_ID, request("Hacked", 1, 2), null)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);

        assertThat(game.getName()).isEqualTo("Catan");
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("등록 관리자가 없는(레거시) 게임은 누구도 수정할 수 없다")
    void update_legacyGame_throws() {
        given(boardGameRepository.findWithOwnerById(1L))
                .willReturn(Optional.of(BoardGame.create("Old", 2, 4, 30, Difficulty.EASY, "옛날 게임")));

        assertThat(thrown(() -> boardGameService.update(1L, OWNER_ID, request("Old", 2, 4), null)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);
    }

    @Test
    @DisplayName("수정 시 최소 인원이 최대 인원보다 크면 INVALID_PLAYER_RANGE")
    void update_minGreaterThanMax_throws() {
        givenOwnedGame(ownedGame(member(OWNER_ID)));

        assertThat(thrown(() -> boardGameService.update(1L, OWNER_ID, request("Catan", 5, 3), null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYER_RANGE);
    }

    @Test
    @DisplayName("없는 id 수정 시 BOARDGAME_NOT_FOUND")
    void update_notFound_throws() {
        given(boardGameRepository.findWithOwnerById(99L)).willReturn(Optional.empty());

        assertThat(thrown(() -> boardGameService.update(99L, OWNER_ID, request("Catan", 3, 4), null)).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);
    }

    @Test
    @DisplayName("수정 시 유튜브 링크를 비우면 영상이 제거된다 (PUT 전체 교체)")
    void update_blankYoutube_removesVideo() {
        BoardGame game = ownedGame(member(OWNER_ID));
        game.changeYoutube("dQw4w9WgXcQ");
        givenOwnedGame(game);

        boardGameService.update(1L, OWNER_ID, request("Catan", 3, 4), null);

        assertThat(game.getYoutubeVideoId()).isNull();
    }

    @Test
    @DisplayName("수정 시 잘못된 유튜브 링크는 INVALID_YOUTUBE_URL, 기존 값은 그대로")
    void update_invalidYoutube_throws() {
        BoardGame game = ownedGame(member(OWNER_ID));
        game.changeYoutube("dQw4w9WgXcQ");
        givenOwnedGame(game);

        assertThat(thrown(() -> boardGameService.update(1L, OWNER_ID,
                request("Catan", 3, 4, true, false, 1, "not a link", null), null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_YOUTUBE_URL);

        assertThat(game.getYoutubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("새 이미지로 교체하면 새 key 를 기록하고 옛 파일을 지운다")
    void update_replaceImage() {
        String oldKey = "boardgames/aaaaaaaa-1111-2222-3333-444455556666.jpg";
        String newKey = "boardgames/bbbbbbbb-1111-2222-3333-444455556666.jpg";
        BoardGame game = ownedGame(member(OWNER_ID));
        game.changeImage(oldKey);
        givenOwnedGame(game);
        given(fileStorage.store(any(), eq(FileKeys.BOARDGAMES))).willReturn(newKey);

        BoardGameResponse response = boardGameService.update(1L, OWNER_ID, request("Catan", 3, 4), ImageFixtures.png("image"));

        assertThat(game.getImageKey()).isEqualTo(newKey);
        assertThat(response.imageUrl()).isEqualTo("/api/files/" + newKey);
        verify(fileStorage).delete(oldKey);
    }

    @Test
    @DisplayName("removeImage 면 이미지를 제거하고 옛 파일을 지운다")
    void update_removeImage() {
        String oldKey = "boardgames/aaaaaaaa-1111-2222-3333-444455556666.jpg";
        BoardGame game = ownedGame(member(OWNER_ID));
        game.changeImage(oldKey);
        givenOwnedGame(game);

        BoardGameResponse response = boardGameService.update(1L, OWNER_ID,
                request("Catan", 3, 4, true, false, 1, null, true), null);

        assertThat(game.getImageKey()).isNull();
        assertThat(response.imageUrl()).isNull();
        verify(fileStorage).delete(oldKey);
        verify(fileStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("이미지 파트도 없고 removeImage 도 아니면 이미지는 그대로다")
    void update_keepsImage() {
        String oldKey = "boardgames/aaaaaaaa-1111-2222-3333-444455556666.jpg";
        BoardGame game = ownedGame(member(OWNER_ID));
        game.changeImage(oldKey);
        givenOwnedGame(game);

        boardGameService.update(1L, OWNER_ID, request("Catan", 3, 4), null);

        assertThat(game.getImageKey()).isEqualTo(oldKey);
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("새 이미지와 removeImage=true 를 함께 보내면 INVALID_INPUT, 파일을 저장하지 않는다")
    void update_imageAndRemove_throws() {
        givenOwnedGame(ownedGame(member(OWNER_ID)));

        assertThat(thrown(() -> boardGameService.update(1L, OWNER_ID,
                request("Catan", 3, 4, true, false, 1, null, true), ImageFixtures.jpeg("image"))).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(fileStorage);
    }

    // ───────────── 숨기기 / 다시 보이기 ─────────────

    @Test
    @DisplayName("소유자가 아니면 숨길 수 없다 (NOT_GAME_OWNER), 파티도 건드리지 않는다")
    void hide_notOwner_throws() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);

        assertThat(thrown(() -> boardGameService.changeVisibility(1L, OTHER_ID, false)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);

        assertThat(game.isVisible()).isTrue();
        verifyNoInteractions(partyRepository, partyRedisRepository, eventPublisher);
    }

    @Test
    @DisplayName("숨기면 게임이 비공개되고 모집 중 파티가 CANCELLED, Redis 키 삭제, 이벤트에 취소된 파티 id 가 담긴다")
    void hide_cancelsRecruitingParties() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);
        Party first = recruitingParty(10L, game);
        Party second = recruitingParty(11L, game);
        given(partyRepository.findByBoardGameIdAndStatus(1L, PartyStatus.RECRUITING))
                .willReturn(List.of(first, second));

        BoardGameResponse response = boardGameService.changeVisibility(1L, OWNER_ID, false);

        assertThat(game.isVisible()).isFalse();
        assertThat(response.visible()).isFalse();
        assertThat(first.getStatus()).isEqualTo(PartyStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(PartyStatus.CANCELLED);
        verify(partyRedisRepository).delete(10L);
        verify(partyRedisRepository).delete(11L);
        verify(eventPublisher).publishEvent(new BoardGameSuspendedEvent(1L, List.of(10L, 11L)));
    }

    @Test
    @DisplayName("모집 중 파티가 없어도 숨기면 이벤트는 발행된다 (취소 id 목록은 빈 목록)")
    void hide_withoutParties_publishesEmptyEvent() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);
        given(partyRepository.findByBoardGameIdAndStatus(1L, PartyStatus.RECRUITING)).willReturn(List.of());

        boardGameService.changeVisibility(1L, OWNER_ID, false);

        assertThat(game.isVisible()).isFalse();
        verify(eventPublisher).publishEvent(new BoardGameSuspendedEvent(1L, List.of()));
        verifyNoInteractions(partyRedisRepository);
    }

    @Test
    @DisplayName("Redis 키 삭제가 실패해도 예외를 던지지 않고 나머지 파티 키를 계속 지운다")
    void hide_redisFailure_isSwallowed() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);
        given(partyRepository.findByBoardGameIdAndStatus(1L, PartyStatus.RECRUITING))
                .willReturn(List.of(recruitingParty(10L, game), recruitingParty(11L, game)));
        doThrow(new IllegalStateException("redis down")).when(partyRedisRepository).delete(10L);

        BoardGameResponse response = boardGameService.changeVisibility(1L, OWNER_ID, false);

        assertThat(response.visible()).isFalse();
        verify(partyRedisRepository).delete(11L);
    }

    @Test
    @DisplayName("이미 숨긴 게임을 또 숨기면 변경 없이 끝난다 (파티 조회·이벤트 없음)")
    void hide_alreadyHidden_noop() {
        BoardGame game = ownedGame(member(OWNER_ID));
        game.hide();
        givenOwnedGame(game);

        BoardGameResponse response = boardGameService.changeVisibility(1L, OWNER_ID, false);

        assertThat(response.visible()).isFalse();
        verifyNoInteractions(partyRepository, partyRedisRepository, eventPublisher);
    }

    @Test
    @DisplayName("다시 보이기는 게임만 노출하고 파티는 건드리지 않는다")
    void show_onlyRestoresGame() {
        BoardGame game = ownedGame(member(OWNER_ID));
        game.hide();
        givenOwnedGame(game);

        BoardGameResponse response = boardGameService.changeVisibility(1L, OWNER_ID, true);

        assertThat(game.isVisible()).isTrue();
        assertThat(response.visible()).isTrue();
        verifyNoInteractions(partyRepository, partyRedisRepository, eventPublisher);
    }

    @Test
    @DisplayName("이미 보이는 게임을 또 보이게 해도 변경 없이 끝난다")
    void show_alreadyVisible_noop() {
        BoardGame game = ownedGame(member(OWNER_ID));
        givenOwnedGame(game);

        assertThat(boardGameService.changeVisibility(1L, OWNER_ID, true).visible()).isTrue();

        verifyNoInteractions(partyRepository, partyRedisRepository, eventPublisher);
    }
}
