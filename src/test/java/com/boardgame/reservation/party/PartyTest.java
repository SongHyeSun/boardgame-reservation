package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.Party.PlayInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Party 엔티티·진행 방식 값 객체의 규칙 (스프링·DB 없음) */
class PartyTest {

    private static final Member HOST = Member.createUser("host@test.com", "pw", "host");

    private static ErrorCode codeOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    // ───────────── PlayInfo ─────────────

    @Test
    @DisplayName("ONLINE 이면 플랫폼·링크를 (공백 제거해) 유지하고 장소는 버린다")
    void playInfo_online_dropsLocation() {
        PlayInfo play = PlayInfo.of(PlayMode.ONLINE, " 디스코드 ", " https://discord.gg/abc ", "동아리방");

        assertThat(play.mode()).isEqualTo(PlayMode.ONLINE);
        assertThat(play.platform()).isEqualTo("디스코드");
        assertThat(play.link()).isEqualTo("https://discord.gg/abc");
        assertThat(play.location()).isNull();
    }

    @Test
    @DisplayName("OFFLINE 이면 장소만 유지하고 플랫폼·링크는 버린다 (잘못된 링크여도 검사하지 않는다)")
    void playInfo_offline_dropsOnlineFields() {
        PlayInfo play = PlayInfo.of(PlayMode.OFFLINE, "디스코드", "not-a-link", " 동아리방 ");

        assertThat(play.platform()).isNull();
        assertThat(play.link()).isNull();
        assertThat(play.location()).isEqualTo("동아리방");
    }

    @Test
    @DisplayName("공백뿐인 값은 null 로 저장된다")
    void playInfo_blankBecomesNull() {
        PlayInfo online = PlayInfo.of(PlayMode.ONLINE, "  ", "   ", null);
        PlayInfo offline = PlayInfo.of(PlayMode.OFFLINE, null, null, "   ");

        assertThat(online.platform()).isNull();
        assertThat(online.link()).isNull();
        assertThat(offline.location()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://a.com", "https://a.com/path?x=1", "HTTPS://A.COM"})
    @DisplayName("http:// / https:// 링크는 허용된다 (대소문자 무관)")
    void playInfo_validLinks(String link) {
        assertThat(PlayInfo.of(PlayMode.ONLINE, null, link, null).link()).isEqualTo(link);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://a.com", "javascript:alert(1)", "a.com", "//a.com", "https://", "http:// a.com", "https://a .com"})
    @DisplayName("http/https 가 아니거나 형식이 깨진 링크는 INVALID_ONLINE_LINK")
    void playInfo_invalidLinks(String link) {
        assertThatThrownBy(() -> PlayInfo.of(PlayMode.ONLINE, null, link, null))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_ONLINE_LINK);
    }

    @Test
    @DisplayName("진행 방식이 없으면 INVALID_PLAY_MODE")
    void playInfo_nullMode_throws() {
        assertThatThrownBy(() -> PlayInfo.of(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_PLAY_MODE);
    }

    // ───────────── 게임 종류 ─────────────

    @Test
    @DisplayName("보드게임 파티: 게임 이름은 보드게임 이름, 커스텀 이름은 null, 숨김 여부는 게임을 따른다")
    void boardGameParty() {
        BoardGame game = BoardGame.create("Catan", 2, 4, 60, Difficulty.NORMAL, "설명");
        Party party = Party.createWithBoardGame(game, HOST, "제목", null, 4, null, PlayInfo.offline());

        assertThat(party.isCustomGame()).isFalse();
        assertThat(party.getGameName()).isEqualTo("Catan");
        assertThat(party.getCustomGameName()).isNull();
        assertThat(party.isBoardGameVisible()).isTrue();

        game.hide();
        assertThat(party.isBoardGameVisible()).isFalse();
    }

    @Test
    @DisplayName("기타 게임 파티: 보드게임 없이 이름(공백 제거)만 저장되고 숨김 여부는 항상 true")
    void customGameParty() {
        Party party = Party.createWithCustomGame("  구스구스덕 ", HOST, "제목", null, 8, null,
                PlayInfo.of(PlayMode.ONLINE, "디스코드", "https://discord.gg/abc", null));

        assertThat(party.isCustomGame()).isTrue();
        assertThat(party.getBoardGame()).isNull();
        assertThat(party.getGameName()).isEqualTo("구스구스덕");
        assertThat(party.getPlayMode()).isEqualTo(PlayMode.ONLINE);
        assertThat(party.getOnlineLink()).isEqualTo("https://discord.gg/abc");
        assertThat(party.isBoardGameVisible()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("기타 게임 이름이 비어 있으면 INVALID_GAME_SELECTION")
    void customGameParty_blankName_throws(String name) {
        assertThatThrownBy(() -> Party.createWithCustomGame(name, HOST, "제목", null, 4, null, PlayInfo.offline()))
                .isInstanceOf(BusinessException.class)
                .extracting(PartyTest::codeOf)
                .isEqualTo(ErrorCode.INVALID_GAME_SELECTION);
    }

    @Test
    @DisplayName("기존 6-인자 팩토리는 오프라인 보드게임 파티를 만든다")
    void legacyFactory_isOfflineBoardGameParty() {
        BoardGame game = BoardGame.create("Catan", 2, 4, 60, Difficulty.NORMAL, "설명");

        Party party = Party.create(game, HOST, "제목", null, 4, null);

        assertThat(party.getPlayMode()).isEqualTo(PlayMode.OFFLINE);
        assertThat(party.getOnlineLink()).isNull();
        assertThat(party.isCustomGame()).isFalse();
    }
}
