package com.boardgame.reservation.member.dto;

import com.boardgame.reservation.member.domain.AvatarType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PUT /api/members/me 의 data(JSON) 파트. 새 프로필 이미지는 별도 image 파트로 받는다.
 * 선택 필드(birthDate·affiliation·job·bio)는 값이 없으면 지워진다(전체 교체).
 *
 * 아바타 규칙
 * - image 파트가 있으면 avatarType 과 무관하게 IMAGE 로 처리 (removeImage 와 함께 오면 400)
 * - avatarType=IMAGE 인데 새 이미지도 기존 이미지도 없으면 400
 * - removeImage=true 면 기존 이미지 삭제 + EMOJI 로 전환 (avatarType=IMAGE 와 함께 오면 400)
 * - avatarType=EMOJI(removeImage=false)면 기존 이미지는 남겨 둔다
 * - avatarEmoji 는 비우면 현재 이모지 유지, 값이 있으면 허용 목록 안이어야 한다
 */
public record UpdateProfileRequest(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        String nickname,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(min = 2, max = 20, message = "이름은 2~20자여야 합니다.")
        String name,

        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        @Size(max = 50, message = "소속은 50자 이하여야 합니다.")
        String affiliation,

        @Size(max = 50, message = "직업은 50자 이하여야 합니다.")
        String job,

        @Size(max = 100, message = "한줄소개는 100자 이하여야 합니다.")
        String bio,

        @NotNull(message = "아바타 타입은 필수입니다.")
        AvatarType avatarType,

        String avatarEmoji,

        // Boolean(wrapper): Jackson 3 는 생략된 원시형(boolean)을 null 로 보고 400 을 내므로 wrapper 로 받는다
        Boolean removeImage
) {
    /** removeImage 를 생략하면 false */
    public boolean imageRemoved() {
        return Boolean.TRUE.equals(removeImage);
    }
}
