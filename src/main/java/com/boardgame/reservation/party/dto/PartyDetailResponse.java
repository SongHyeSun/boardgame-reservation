package com.boardgame.reservation.party.dto;

import com.boardgame.reservation.member.dto.AvatarResponse;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.domain.PartyStatus;

import java.time.LocalDateTime;
import java.util.List;

/** remaining = capacity - DB 참여자 수 (호스트 포함 기준) */
public record PartyDetailResponse(
        Long id,
        String title,
        String description,
        Long boardGameId,
        String boardGameName,
        Long hostId,
        String hostNickname,
        AvatarResponse hostAvatar,
        int capacity,
        int remaining,
        PartyStatus status,
        LocalDateTime playAt,
        List<MemberInfo> members
) {
    public record MemberInfo(Long memberId, String nickname, AvatarResponse avatar, LocalDateTime joinedAt) {
    }

    public static PartyDetailResponse of(Party party, List<PartyMember> partyMembers) {
        List<MemberInfo> members = partyMembers.stream()
                .map(pm -> new MemberInfo(
                        pm.getMember().getId(),
                        pm.getMember().getNickname(),
                        AvatarResponse.from(pm.getMember()),
                        pm.getJoinedAt()))
                .toList();
        return new PartyDetailResponse(
                party.getId(),
                party.getTitle(),
                party.getDescription(),
                party.getBoardGame().getId(),
                party.getBoardGame().getName(),
                party.getHost().getId(),
                party.getHost().getNickname(),
                AvatarResponse.from(party.getHost()),
                party.getCapacity(),
                party.getCapacity() - members.size(),
                party.getStatus(),
                party.getPlayAt(),
                members
        );
    }
}
