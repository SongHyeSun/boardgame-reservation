package com.boardgame.reservation.notification;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.notification.domain.Notification;
import com.boardgame.reservation.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST API: 본인 것만 조회·읽음(남 404), unread-count, read-all. SSE 는 비로그인 401 만 여기서 확인한다. */
class NotificationApiIntegrationTest extends NotificationRedisTestSupport {

    Member me;
    Member other;

    @BeforeEach
    void setUpMembers() {
        me = saveMember("me");
        other = saveMember("other");
    }

    private Notification save(Member receiver, NotificationType type, String message, boolean read) {
        Notification notification = Notification.create(receiver, type, message, "/parties/1");
        if (read) {
            notification.markRead();
        }
        return notificationRepository.save(notification);
    }

    @Test
    @DisplayName("비로그인으로 SSE 스트림에 연결하면 401")
    void stream_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("목록은 본인 것만 최신순으로 내려온다")
    void list_ownOnly_newestFirst() throws Exception {
        Notification older = save(me, NotificationType.PARTY_JOINED, "먼저 온 알림", false);
        Notification newer = save(me, NotificationType.PARTY_LEFT, "나중에 온 알림", false);
        save(other, NotificationType.PARTY_JOINED, "남의 알림", false);

        mockMvc.perform(get("/api/notifications?page=0&size=20").with(loginAs(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.data.content[0].message").value("나중에 온 알림"))
                .andExpect(jsonPath("$.data.content[0].type").value("PARTY_LEFT"))
                .andExpect(jsonPath("$.data.content[0].link").value("/parties/1"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andExpect(jsonPath("$.data.content[1].id").value(older.getId()));
    }

    @Test
    @DisplayName("비로그인으로 목록·안읽음수 조회하면 401")
    void listAndUnreadCount_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("안 읽은 알림 수는 read=false 인 본인 알림만 센다")
    void unreadCount_countsOnlyOwnUnread() throws Exception {
        save(me, NotificationType.PARTY_JOINED, "안읽음1", false);
        save(me, NotificationType.PARTY_LEFT, "안읽음2", false);
        save(me, NotificationType.PARTY_KICKED, "이미읽음", true);
        save(other, NotificationType.PARTY_JOINED, "남의 안읽음", false);

        mockMvc.perform(get("/api/notifications/unread-count").with(loginAs(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    @DisplayName("본인 알림은 읽음 처리된다")
    void markRead_own_marksRead() throws Exception {
        Notification notification = save(me, NotificationType.PARTY_JOINED, "알림", false);

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId()).with(loginAs(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/notifications/unread-count").with(loginAs(me)))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("남의 알림을 읽음 처리하면 404 (존재를 숨긴다), 상태는 바뀌지 않는다")
    void markRead_othersNotification_404() throws Exception {
        Notification notification = save(other, NotificationType.PARTY_JOINED, "남의 알림", false);

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId()).with(loginAs(me)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("알림을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/notifications/unread-count").with(loginAs(other)))
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 알림 id 는 404")
    void markRead_missing_404() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", 999_999L).with(loginAs(me)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("전체 읽음은 본인 것만 바꾸고, 다른 회원 알림은 그대로 안 읽음이다")
    void markAllRead_onlyOwn() throws Exception {
        save(me, NotificationType.PARTY_JOINED, "안읽음1", false);
        save(me, NotificationType.PARTY_LEFT, "안읽음2", false);
        save(other, NotificationType.PARTY_JOINED, "남의 안읽음", false);

        mockMvc.perform(patch("/api/notifications/read-all").with(loginAs(me)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count").with(loginAs(me)))
                .andExpect(jsonPath("$.data.count").value(0));
        mockMvc.perform(get("/api/notifications/unread-count").with(loginAs(other)))
                .andExpect(jsonPath("$.data.count").value(1));
    }
}
