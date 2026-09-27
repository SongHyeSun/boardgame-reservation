package com.boardgame.reservation.notification.domain;

import com.boardgame.reservation.global.common.BaseTimeEntity;
import com.boardgame.reservation.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD: NOTIFICATION (id, receiver_id, type, message, link, is_read, created_at)
 * message 는 리스너가 조립한 완성된 한국어 문장, link 는 프론트 경로(예 /parties/3).
 * 목록·안읽음수 조회는 (receiver_id, is_read, created_at) 인덱스로 처리한다.
 */
@Entity
@Table(name = "notification",
        indexes = @Index(name = "idx_notification_receiver_read_created", columnList = "receiver_id, is_read, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(length = 100)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private Notification(Member receiver, NotificationType type, String message, String link) {
        this.receiver = receiver;
        this.type = type;
        this.message = message;
        this.link = link;
        this.read = false;
    }

    public static Notification create(Member receiver, NotificationType type, String message, String link) {
        return new Notification(receiver, type, message, link);
    }

    public void markRead() {
        this.read = true;
    }
}
