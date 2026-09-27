package com.boardgame.reservation.reservation.dto;

import jakarta.validation.constraints.Size;

/** PATCH /api/admin/reservations/{id}/reject — 사유는 선택(생략·공백이면 사유 없음), 최대 100자 */
public record ReservationRejectRequest(

        @Size(max = 100, message = "거절 사유는 100자 이하여야 합니다.")
        String reason
) {
}
