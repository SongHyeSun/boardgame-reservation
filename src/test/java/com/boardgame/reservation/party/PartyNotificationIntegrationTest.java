package com.boardgame.reservation.party;

import com.boardgame.reservation.notification.domain.Notification;
import com.boardgame.reservation.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D단계에서 새로 채운 파티 이벤트(join/leave/close — kick 은 기존)가 실제로 알림까지 저장되는지 확인한다.
 * PartyConcurrencyTest 와 같은 RedisIntegrationTestSupport 계열이라 알림 리스너가 부수효과로 함께 실행되지만,
 * 서로 다른 테이블·Redis 채널을 쓰므로 PartyConcurrencyTest 의 수치에는 영향이 없다(별도로 재확인 완료).
 */
class PartyNotificationIntegrationTest extends PartyApiTestSupport {

    private List<Notification> notificationsOf(Long receiverId) {
        return notificationRepository.findByReceiverIdOrderByIdDesc(receiverId, Pageable.unpaged()).getContent();
    }

    @Test
    @DisplayName("참여하면 호스트에게 PARTY_JOINED 알림 1건 (닉네임·파티 제목 포함, link=/parties/{id})")
    void join_notifiesHost() throws Exception {
        long partyId = createPartyAs(host, """
                {"boardGameId":%d,"title":"같이 해요","capacity":4,"playMode":"OFFLINE"}
                """.formatted(boardGame.getId()));

        joinAs(guest, partyId);

        List<Notification> hostNotifications = notificationsOf(host.getId());
        assertThat(hostNotifications).hasSize(1);
        Notification notification = hostNotifications.get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.PARTY_JOINED);
        assertThat(notification.getMessage()).contains("guest님이").contains("같이 해요");
        assertThat(notification.getLink()).isEqualTo("/parties/" + partyId);
        assertThat(notification.isRead()).isFalse();
        // 참여한 본인(guest)에게는 알림이 없다
        assertThat(notificationsOf(guest.getId())).isEmpty();
    }

    @Test
    @DisplayName("참여로 정원이 다 차면 호스트에게 PARTY_JOINED 에 이어 PARTY_FULL 도 남는다")
    void join_fillingCapacity_alsoNotifiesFull() throws Exception {
        long partyId = createPartyAs(host, """
                {"boardGameId":%d,"title":"선착순","capacity":2,"playMode":"OFFLINE"}
                """.formatted(boardGame.getId()));

        joinAs(guest, partyId);

        List<Notification> hostNotifications = notificationsOf(host.getId());
        assertThat(hostNotifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.PARTY_JOINED, NotificationType.PARTY_FULL);
    }

    @Test
    @DisplayName("탈퇴하면 호스트에게 PARTY_LEFT 알림이 남는다 (join 알림에 이어 총 2건)")
    void leave_notifiesHost() throws Exception {
        long partyId = createPartyAs(host, """
                {"boardGameId":%d,"title":"같이 해요","capacity":4,"playMode":"OFFLINE"}
                """.formatted(boardGame.getId()));
        joinAs(guest, partyId);

        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());

        List<Notification> hostNotifications = notificationsOf(host.getId());
        assertThat(hostNotifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.PARTY_JOINED, NotificationType.PARTY_LEFT);
        assertThat(hostNotifications).filteredOn(n -> n.getType() == NotificationType.PARTY_LEFT)
                .extracting(Notification::getMessage)
                .allMatch(message -> message.contains("guest님이") && message.contains("나갔어요"));
    }

    @Test
    @DisplayName("내보내면 내보내진 회원에게만 PARTY_KICKED 알림이 남는다 (호스트에게는 없음)")
    void kick_notifiesOnlyKickedMember() throws Exception {
        long partyId = createPartyAs(host, """
                {"boardGameId":%d,"title":"같이 해요","capacity":4,"playMode":"OFFLINE"}
                """.formatted(boardGame.getId()));
        joinAs(guest, partyId);

        mockMvc.perform(delete("/api/parties/{id}/members/{memberId}", partyId, guest.getId()).with(loginAs(host)))
                .andExpect(status().isOk());

        List<Notification> guestNotifications = notificationsOf(guest.getId());
        assertThat(guestNotifications).extracting(Notification::getType).contains(NotificationType.PARTY_KICKED);
        assertThat(guestNotifications).filteredOn(n -> n.getType() == NotificationType.PARTY_KICKED)
                .extracting(Notification::getMessage).allMatch(message -> message.contains("내보내졌어요"));
        // 호스트는 PARTY_JOINED 만 있고 자신이 한 내보내기에 대한 알림은 없다
        assertThat(notificationsOf(host.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.PARTY_JOINED);
    }

    @Test
    @DisplayName("마감하면 참여자에게는 PARTY_CLOSED, 호스트 자신에게는 (본인 행동이라) 남지 않는다")
    void close_notifiesParticipantsButNotHost() throws Exception {
        long partyId = createPartyAs(host, """
                {"boardGameId":%d,"title":"같이 해요","capacity":4,"playMode":"OFFLINE"}
                """.formatted(boardGame.getId()));
        joinAs(guest, partyId);

        mockMvc.perform(patch("/api/parties/{id}/close", partyId).with(loginAs(host)))
                .andExpect(status().isOk());

        List<Notification> guestNotifications = notificationsOf(guest.getId());
        assertThat(guestNotifications).extracting(Notification::getType).contains(NotificationType.PARTY_CLOSED);
        // 호스트 알림은 여전히 PARTY_JOINED 하나뿐 — PARTY_CLOSED 는 행위자 본인이라 스킵됐다
        assertThat(notificationsOf(host.getId())).extracting(Notification::getType)
                .containsExactly(NotificationType.PARTY_JOINED);
    }
}
