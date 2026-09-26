package com.boardgame.reservation.member.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.dto.ChangePasswordRequest;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.dto.UpdateProfileRequest;
import com.boardgame.reservation.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 정보 API — 전부 로그인 세션 필수 (SecurityConfig: anyRequest().authenticated()).
 * 세션의 MemberPrincipal 값은 id 만 쓰고, 화면에 나가는 값은 항상 DB 에서 다시 읽는다(수정 즉시 반영).
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** GET /api/members/me */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMember(principal.getId())));
    }

    /** PUT /api/members/me — multipart/form-data: data(JSON) + image(선택) */
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MemberResponse>> updateMe(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestPart("data") UpdateProfileRequest data,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.updateProfile(principal.getId(), data, image)));
    }

    /** PATCH /api/members/me/password */
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        memberService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** POST /api/members/me/admin-request — 관리자 신청(재신청 포함) */
    @PostMapping("/me/admin-request")
    public ResponseEntity<ApiResponse<MemberResponse>> requestAdmin(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.requestAdmin(principal.getId())));
    }
}
