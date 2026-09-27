package com.boardgame.reservation.reservation.dto;

import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 내 예약 응답. 게임은 이름·imageUrl·boardGameVisible(false 면 화면에서 「운영 중지」) 만 담는다.
 * "대여 완료"는 서버가 상태를 바꾸지 않으므로 화면이 APPROVED + endDate 지남으로 판단한다.
 * 게임을 읽으므로 트랜잭션 안에서(또는 boardGame 을 fetch 한 뒤) 호출한다.
 */
public record ReservationResponse(
        Long id,
        Long boardGameId,
        String boardGameName,
        String imageUrl,
        boolean boardGameVisible,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        CancelReason cancelReason,
        String rejectReason,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBoardGame().getId(),
                reservation.getBoardGame().getName(),
                FileKeys.toUrl(reservation.getBoardGame().getImageKey()),
                reservation.getBoardGame().isVisible(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getStatus(),
                reservation.getCancelReason(),
                reservation.getRejectReason(),
                reservation.getCreatedAt()
        );
    }
}
