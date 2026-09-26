package com.boardgame.reservation.member.dto;

import com.boardgame.reservation.global.file.FileKeys;
import com.boardgame.reservation.member.domain.AvatarType;
import com.boardgame.reservation.member.domain.Member;

/**
 * 아바타 공통 응답. 내 정보, 헤더, 파티 호스트·참여자에서 같은 형식으로 쓴다.
 * imageUrl 은 저장된 이미지가 있으면 type 과 무관하게 내려간다(이모지로 전환해 둔 상태에서도 다시 IMAGE 로 되돌릴 수 있게).
 * 화면에 그릴 때는 type 으로 판단한다.
 */
public record AvatarResponse(AvatarType type, String emoji, String imageUrl) {

    public static AvatarResponse from(Member member) {
        return new AvatarResponse(
                member.getAvatarType(),
                member.getAvatarEmoji(),
                FileKeys.toUrl(member.getAvatarImageKey()));
    }
}
