package com.boardgame.reservation.member.dto;

import com.boardgame.reservation.member.domain.Member;

import java.time.LocalDateTime;

/** SUPER_ADMIN 이 보는 관리자 신청 목록 항목 */
public record AdminRequestResponse(
        Long memberId,
        String email,
        String nickname,
        String name,
        String affiliation,
        String job,
        LocalDateTime requestedAt
) {
    public static AdminRequestResponse from(Member member) {
        return new AdminRequestResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getName(),
                member.getAffiliation(),
                member.getJob(),
                member.getAdminRequestedAt()
        );
    }
}
