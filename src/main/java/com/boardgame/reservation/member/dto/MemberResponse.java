package com.boardgame.reservation.member.dto;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.domain.Role;

import java.time.LocalDateTime;

/** 엔티티를 그대로 반환하지 않고 DTO로 변환 → password 같은 필드가 응답에 새지 않는다 */
public record MemberResponse(
        Long id,
        String email,
        String nickname,
        Role role,
        LocalDateTime createdAt
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
