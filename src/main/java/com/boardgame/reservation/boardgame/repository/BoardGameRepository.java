package com.boardgame.reservation.boardgame.repository;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.ReservationPolicy;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 목록 조회의 동적 필터는 BoardGameSpecification 으로 조합한다. */
public interface BoardGameRepository
        extends JpaRepository<BoardGame, Long>, JpaSpecificationExecutor<BoardGame> {

    /** 응답의 owner(닉네임)까지 한 번에 읽는다. 등록 관리자가 없는(레거시) 게임도 조회되도록 left join */
    @EntityGraph(attributePaths = "createdBy")
    Optional<BoardGame> findWithOwnerById(Long id);

    /**
     * 게임 행 비관적 락(SELECT … FOR UPDATE) — 예약 신청·재고 수정·진행 방식 끄기·숨기기가 모두 이 한 메서드로 같은 락을 잡아 게임 단위로 직렬화된다.
     * 주의: ① 그 트랜잭션에서 게임을 처음 읽는 쿼리여야 한다(먼저 다른 쿼리로 로드하면 1차 캐시의 옛 값이 재사용된다).
     * ② 쓰기 트랜잭션에서만 호출(PostgreSQL 은 read-only 트랜잭션의 FOR UPDATE 를 거부, H2 는 통과).
     * ③ createdBy 는 fetch join 하지 않는다(PostgreSQL 은 outer join 대상 잠금 제약). LAZY 프록시로 두고 트랜잭션 안에서 읽는다.
     * 대기 한도 3초: 넘으면 PessimisticLockingFailureException → GlobalExceptionHandler 가 RESERVATION_BUSY(409).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = ReservationPolicy.LOCK_TIMEOUT_MILLIS))
    @Query("select g from BoardGame g where g.id = :id")
    Optional<BoardGame> findByIdForUpdate(@Param("id") Long id);

    /**
     * 등록 관리자가 없는 기존 게임의 소유자를 채운다 (기동 시 SUPER_ADMIN 으로). @return 채운 게임 수
     * 호출 시점에 BoardGame 엔티티가 영속성 컨텍스트에 없어 clearAutomatically 가 필요 없고,
     * 같은 트랜잭션에서 방금 승격한 회원의 미반영 변경을 em.clear() 로 버릴 가능성도 피한다.
     */
    @Modifying
    @Query("update BoardGame g set g.createdBy = :owner where g.createdBy is null")
    int assignOwnerIfMissing(@Param("owner") Member owner);
}
