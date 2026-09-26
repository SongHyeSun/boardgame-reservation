package com.boardgame.reservation.party.dto;

import com.boardgame.reservation.member.dto.AvatarResponse;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyStatus;

import java.time.LocalDateTime;

/** 목록/개설 응답 항목 */
public record PartyResponse(
        Long id,
        String title,
        Long boardGameId,
        String boardGameName,
        String hostNickname,
        AvatarResponse hostAvatar,
        int capacity,
        long currentCount,
        PartyStatus status,
        LocalDateTime playAt
) {
    public static PartyResponse of(Party party, long currentCount) {
        return new PartyResponse(
                party.getId(),
                party.getTitle(),
                party.getBoardGame().getId(),
                party.getBoardGame().getName(),
                party.getHost().getNickname(),
                AvatarResponse.from(party.getHost()),
                party.getCapacity(),
                currentCount,
                party.getStatus(),
                party.getPlayAt()
        );
    }
}
