package com.boardgame.reservation.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * multipart/form-data 의 data(JSON) 파트. 프로필 이미지는 별도 image 파트로 받는다.
 * avatarEmoji 가 없으면 기본 이모지, image 가 있으면 아바타 타입은 IMAGE.
 * requestAdmin=true 면 USER 로 가입하되 관리자 신청(PENDING)까지 함께 기록한다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        String password,

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

        String avatarEmoji,

        // Boolean(wrapper): Jackson 3(Boot 4)는 기본값으로 원시형에 null 매핑을 막기 때문에, 생략된 경우도 false 로 받으려면 wrapper 여야 한다
        Boolean requestAdmin
) {
    /** requestAdmin 을 생략하면 false */
    public boolean adminRequested() {
        return Boolean.TRUE.equals(requestAdmin);
    }
}
