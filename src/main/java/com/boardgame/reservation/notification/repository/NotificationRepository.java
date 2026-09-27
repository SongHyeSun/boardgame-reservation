package com.boardgame.reservation.notification.repository;

import com.boardgame.reservation.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByReceiverIdOrderByIdDesc(Long receiverId, Pageable pageable);

    long countByReceiverIdAndReadFalse(Long receiverId);

    /** 본인 것만 읽음 처리할 수 있게 receiverId 도 조건에 포함 (남의 알림이면 empty → NOTIFICATION_NOT_FOUND) */
    Optional<Notification> findByIdAndReceiverId(Long id, Long receiverId);

    @Modifying
    @Query("update Notification n set n.read = true where n.receiver.id = :receiverId and n.read = false")
    int markAllRead(@Param("receiverId") Long receiverId);
}
