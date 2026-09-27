package com.boardgame.reservation.reservation.repository;

import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationPolicy;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * "활성" = PENDING·APPROVED (재고 점유). 서비스는 아래 default 메서드(활성 전용)를 쓴다.
 * 활성 예약 조회는 게임 행 락을 잡은 뒤에 호출한다 (READ COMMITTED 라 앞선 트랜잭션의 커밋이 보인다).
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // ───────────── 재고 계산 (게임 단위) ─────────────

    /** 기간이 [from, to] 와 하루라도 겹치는 예약. 인덱스 (board_game_id, start_date, end_date) */
    @Query("""
            select r from Reservation r
            where r.boardGame.id = :gameId and r.status in :statuses
              and r.startDate <= :to and r.endDate >= :from
            """)
    List<Reservation> findOverlapping(@Param("gameId") Long gameId,
                                      @Param("statuses") Collection<ReservationStatus> statuses,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    default List<Reservation> findActiveOverlapping(Long gameId, LocalDate from, LocalDate to) {
        return findOverlapping(gameId, ReservationStatus.ACTIVE, from, to);
    }

    /** 종료일이 date 이상인(아직 끝나지 않은) 예약. 숨기기·재고 줄이기·오프라인 끄기 검사용 */
    @Query("""
            select r from Reservation r
            where r.boardGame.id = :gameId and r.status in :statuses and r.endDate >= :date
            """)
    List<Reservation> findEndingOnOrAfter(@Param("gameId") Long gameId,
                                          @Param("statuses") Collection<ReservationStatus> statuses,
                                          @Param("date") LocalDate date);

    default List<Reservation> findActiveEndingOnOrAfter(Long gameId, LocalDate date) {
        return findEndingOnOrAfter(gameId, ReservationStatus.ACTIVE, date);
    }

    boolean existsByBoardGameIdAndStatusInAndEndDateGreaterThanEqual(Long gameId,
                                                                     Collection<ReservationStatus> statuses,
                                                                     LocalDate date);

    default boolean existsActiveEndingOnOrAfter(Long gameId, LocalDate date) {
        return existsByBoardGameIdAndStatusInAndEndDateGreaterThanEqual(gameId, ReservationStatus.ACTIVE, date);
    }

    // ───────────── 상태 변경 (예약 행 락) ─────────────

    /**
     * 승인·거절·취소가 같은 예약에 동시에 오면 마지막 쓰기가 이기는 문제를 막는다.
     * 게임 행은 잡지 않으므로(숨기기는 게임 → 예약 순) 락 순서가 꼬이지 않는다. 쓰기 트랜잭션 안에서만 호출.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = ReservationPolicy.LOCK_TIMEOUT_MILLIS))
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    // ───────────── 목록 (최신순, 페이징 없음) ─────────────

    @EntityGraph(attributePaths = "boardGame")
    List<Reservation> findByMemberIdOrderByIdDesc(Long memberId);

    @EntityGraph(attributePaths = "boardGame")
    List<Reservation> findByMemberIdAndStatusOrderByIdDesc(Long memberId, ReservationStatus status);

    /** 소유 관리자용: 내가 등록한 게임의 예약. 신청자 정보(닉네임·이름·아바타)까지 한 번에 읽는다 */
    @Query("""
            select r from Reservation r
            join fetch r.boardGame g join fetch r.member
            where g.createdBy.id = :ownerId and r.status = :status
            order by r.id desc
            """)
    List<Reservation> findByGameOwner(@Param("ownerId") Long ownerId, @Param("status") ReservationStatus status);
}
