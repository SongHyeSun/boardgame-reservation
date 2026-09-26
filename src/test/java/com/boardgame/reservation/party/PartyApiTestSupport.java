package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.member.domain.Member;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파티 API 통합 테스트(Security 필터 체인 + 실제 Redis + H2)의 공통 부모.
 * 회원 3명(host/guest/other)과 인원 2~4명 오프라인 보드게임을 준비하고, 파티 개설 요청 헬퍼를 제공한다.
 */
abstract class PartyApiTestSupport extends PartyRedisTestSupport {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;
    protected Member host;
    protected Member guest;
    protected Member other;
    protected BoardGame boardGame;

    @BeforeEach
    void setUpApi() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        host = saveMember("host");
        guest = saveMember("guest");
        other = saveMember("other");
        boardGame = saveBoardGame(2, 4);
    }

    protected String membersKey(long partyId) {
        return "party:" + partyId + ":members";
    }

    /** creator 가 이 JSON 으로 파티를 개설(201 기대)하고 생성된 파티 id 를 돌려준다 */
    protected long createPartyAs(Member creator, String json) throws Exception {
        String response = mockMvc.perform(post("/api/parties")
                        .with(loginAs(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    protected void joinAs(Member member, long partyId) throws Exception {
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(member)))
                .andExpect(status().isOk());
    }
}
