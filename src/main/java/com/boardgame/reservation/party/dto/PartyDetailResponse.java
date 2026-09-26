package com.boardgame.reservation.party.dto;

import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.member.dto.AvatarResponse;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.domain.PartyMember;
import com.boardgame.reservation.party.domain.PartyStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * remaining = capacity - DB 참여자(JOINED) 수 (호스트 포함 기준).
 * onlineLink 는 호스트·참여자에게만 값이 있고 그 외(비로그인·미참여·내보내진 회원)에게는 null 이다.
 */
public record PartyDetailResponse(
        Long id,
        String title,
        String description,
        Long boardGameId,
        String gameName,
        boolean customGame,
        boolean boardGameVisible,
        Long hostId,
        String hostNickname,
        AvatarResponse hostAvatar,
        int capacity,
        int remaining,
        PartyStatus status,
        LocalDateTime playAt,
        PlayMode playMode,
        String onlinePlatform,
        String onlineLink,
        String location,
        List<MemberInfo> members
) {
    public record MemberInfo(Long memberId, String nickname, AvatarResponse avatar, LocalDateTime joinedAt) {
    }

    /**
     * @param joinedMembers 참여 중(JOINED)인 회원만
     * @param viewerId      조회자 id (비로그인이면 null)
     */
    public static PartyDetailResponse of(Party party, List<PartyMember> joinedMembers, Long viewerId) {
        List<MemberInfo> members = joinedMembers.stream()
                .map(pm -> new MemberInfo(
                        pm.getMember().getId(),
                        pm.getMember().getNickname(),
                        AvatarResponse.from(pm.getMember()),
                        pm.getJoinedAt()))
                .toList();
        boolean canSeeLink = viewerId != null
                && (party.isHost(viewerId) || members.stream().anyMatch(m -> m.memberId().equals(viewerId)));
        return new PartyDetailResponse(
                party.getId(),
                party.getTitle(),
                party.getDescription(),
                party.isCustomGame() ? null : party.getBoardGame().getId(),
                party.getGameName(),
                party.isCustomGame(),
                party.isBoardGameVisible(),
                party.getHost().getId(),
                party.getHost().getNickname(),
                AvatarResponse.from(party.getHost()),
                party.getCapacity(),
                party.getCapacity() - members.size(),
                party.getStatus(),
                party.getPlayAt(),
                party.getPlayMode(),
                party.getOnlinePlatform(),
                canSeeLink ? party.getOnlineLink() : null,
                party.getLocation(),
                members
        );
    }
}
