package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.boardgame.reservation.support.MultipartTestUtils.updateBoardGame;
import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진행 방식 끄기 제한(PLAY_MODE_IN_USE): 그 방식으로 모집 중(RECRUITING)인 파티가 있으면 수정할 수 없고,
 * 파티를 자동으로 취소하지 않는다. 검사는 DB 기준이라 파티는 저장소에 직접 만든다.
 */
class BoardGamePlayModeIntegrationTest extends RedisIntegrationTestSupport {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;
    Member admin;
    Member host;
    BoardGame game;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        admin = saveAdmin("admin");
        host = saveMember("host");
        game = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Arena", 2, 4, 60, Difficulty.NORMAL, "온·오프라인", true, true, 2), admin));
    }

    private Party saveParty(PlayMode mode) {
        return partyRepository.save(Party.createWithBoardGame(
                game, host, "같이 해요", null, 4, null, Party.PlayInfo.of(mode, null, null, null)));
    }

    private ResultActions update(boolean offline, boolean online, int stock) throws Exception {
        return mockMvc.perform(updateBoardGame(game.getId(), """
                {"name":"Arena","minPlayers":2,"maxPlayers":4,"playTime":60,"difficulty":"NORMAL","description":"설명",
                 "offlineAvailable":%b,"onlineAvailable":%b,"stock":%d}
                """.formatted(offline, online, stock)).with(loginAs(admin)));
    }

    private PartyStatus statusOf(Party party) {
        return partyRepository.findById(party.getId()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("⭐ 온라인으로 모집 중인 파티가 있으면 온라인을 끌 수 없다 (409), 게임은 그대로이고 파티는 자동 취소되지 않는다")
    void disableOnline_withRecruitingOnlineParty_conflict() throws Exception {
        Party party = saveParty(PlayMode.ONLINE);

        update(true, false, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("해당 방식으로 모집 중인 파티가 있어 변경할 수 없습니다."));

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.onlineAvailable").value(true));
        assertThat(statusOf(party)).isEqualTo(PartyStatus.RECRUITING);
    }

    @Test
    @DisplayName("오프라인으로 모집 중인 파티가 있으면 오프라인을 끌 수 없다 (409)")
    void disableOffline_withRecruitingOfflineParty_conflict() throws Exception {
        saveParty(PlayMode.OFFLINE);

        update(false, true, 0).andExpect(status().isConflict());

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.offlineAvailable").value(true))
                .andExpect(jsonPath("$.data.stock").value(2));
    }

    @Test
    @DisplayName("끄려는 방식이 아닌 다른 방식의 파티만 있으면 끌 수 있다 (온라인 파티만 있을 때 오프라인 끄기)")
    void disableOffline_withOnlyOnlineParty_ok() throws Exception {
        saveParty(PlayMode.ONLINE);

        update(false, true, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offlineAvailable").value(false))
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    @Test
    @DisplayName("마감(CLOSED)·취소(CANCELLED)된 파티는 막지 않는다")
    void disableMode_withClosedOrCancelledParties_ok() throws Exception {
        Party closed = saveParty(PlayMode.ONLINE);
        closed.close();
        partyRepository.save(closed);
        Party cancelled = saveParty(PlayMode.ONLINE);
        cancelled.cancel();
        partyRepository.save(cancelled);

        update(true, false, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineAvailable").value(false));
    }

    @Test
    @DisplayName("방식을 그대로 두는 수정(설명·재고 등)은 모집 중인 파티가 있어도 가능하다")
    void keepModes_withRecruitingParties_ok() throws Exception {
        saveParty(PlayMode.ONLINE);
        saveParty(PlayMode.OFFLINE);

        update(true, true, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(5));
    }

    @Test
    @DisplayName("두 방식을 모두 끄면 모집 중 파티가 있어도 400 (INVALID_PLAY_MODE)이 먼저다")
    void disableBoth_invalidPlayModeFirst() throws Exception {
        saveParty(PlayMode.ONLINE);

        update(false, false, 0).andExpect(status().isBadRequest());
    }
}
