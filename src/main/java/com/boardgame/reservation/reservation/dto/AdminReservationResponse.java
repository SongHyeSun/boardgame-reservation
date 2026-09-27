package com.boardgame.reservation.reservation.dto;

import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.dto.AvatarResponse;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 소유 관리자용 예약 응답: ReservationResponse 의 필드 + 신청자(닉네임·이름·아바타). 신청자·게임은 트랜잭션 안에서 읽는다 */
public record AdminReservationResponse(
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
        LocalDateTime createdAt,
        Requester requester
) {
    /** name 은 선택 입력이라 null 일 수 있다 */
    public record Requester(Long id, String nickname, String name, AvatarResponse avatar) {
        static Requester from(Member member) {
            return new Requester(member.getId(), member.getNickname(), member.getName(), AvatarResponse.from(member));
        }
    }

    public static AdminReservationResponse from(Reservation reservation) {
        return new AdminReservationResponse(
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
                reservation.getCreatedAt(),
                Requester.from(reservation.getMember())
        );
    }
}
