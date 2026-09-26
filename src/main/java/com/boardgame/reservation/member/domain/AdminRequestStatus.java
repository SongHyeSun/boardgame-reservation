package com.boardgame.reservation.member.domain;

/** 관리자 권한 신청 상태. NONE → PENDING → APPROVED / REJECTED (REJECTED 는 재신청 가능) */
public enum AdminRequestStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED
}
