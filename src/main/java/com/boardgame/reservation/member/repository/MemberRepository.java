package com.boardgame.reservation.member.repository;

import com.boardgame.reservation.member.domain.AdminRequestStatus;
import com.boardgame.reservation.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 신청 상태별 목록, 오래 기다린 신청부터 */
    List<Member> findByAdminRequestStatusOrderByAdminRequestedAtAsc(AdminRequestStatus status);
}
