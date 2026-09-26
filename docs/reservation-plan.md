# C. 기간 대여 예약 구현 계획 (feature/reservation)

레포 위치: `docs/reservation-plan.md`. 공통 규칙은 `docs/extension-overview.md` 3장. B 단계(stock, visible, createdBy, offlineAvailable) 완료 전제.

---

## 1. 도메인 (reservation 패키지)

- `ReservationStatus`: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`
- `CancelReason`: `MEMBER`(본인 취소), `GAME_SUSPENDED`(게임 운영 중지)
- `Reservation` (extends BaseTimeEntity)
  - boardGame @ManyToOne(LAZY), member @ManyToOne(LAZY)
  - startDate, endDate (`LocalDate`, 당일 대여는 start = end)
  - status, cancelReason(nullable), rejectReason(nullable, ≤100), decidedAt(nullable)
  - 인덱스: `(board_game_id, start_date, end_date)`
  - 메서드: approve(), reject(reason), cancel(reason), overlaps(start, end), isActive()(PENDING·APPROVED)

## 2. 규칙

- 정책 상수는 `ReservationPolicy`로 분리: **시작일 오늘 ~ +60일, 기간 최대 7일**(당일 = 1일)
  - 위반 → INVALID_RESERVATION_PERIOD(400) (과거 날짜, start > end, 60일 초과, 7일 초과)
- **온라인 전용 게임(`offlineAvailable=false`)은 예약 불가** → RESERVATION_NOT_SUPPORTED(409 "온라인 전용 게임은 대여할 수 없습니다")
- **재고 점유: PENDING + APPROVED 모두 점유** (승인 전이라도 그 날짜는 다른 사람이 못 빌림)
- 날짜별 가용 수량 = `stock - (그 날짜를 포함하는 활성 예약 수)`
  - 신청 기간의 **모든 날짜**가 1 이상이어야 함 → 아니면 NOT_AVAILABLE(409 "선택한 기간에 대여 가능한 재고가 없습니다")
- 같은 회원이 같은 게임에 기간이 겹치는 활성 예약 → DUPLICATE_RESERVATION(409 "이미 해당 기간에 예약한 게임입니다")
- 숨김 게임 → BOARDGAME_NOT_AVAILABLE(409)
- 승인/거절: **게임 소유 관리자만**(NOT_GAME_OWNER 403), PENDING만(INVALID_RESERVATION_STATUS 409). 승인 시 재고 재검사 불필요(신청 시 이미 점유)
- 거절·취소되면 재고 점유 해제 → 달력에 다시 선택 가능
- 본인 취소: PENDING·APPROVED이고 **시작일 전날까지** → 아니면 CANNOT_CANCEL_RESERVATION(409)
- 반납 관리 없음: 종료일이 지난 APPROVED는 화면에서 "대여 완료"로만 표시(상태 변경 X)

### B단계 코드에 추가
- **재고 줄이기**(PUT 게임 수정): 오늘 이후 날짜 중 최대 점유 수보다 작게 못 줄임 → STOCK_BELOW_RESERVED(409 "이미 예약된 수량보다 재고를 줄일 수 없습니다")
- **오프라인 끄기**(PUT 게임 수정, `offlineAvailable` true→false): `endDate >= 오늘`인 활성 예약이 있으면 PLAY_MODE_IN_USE(409). 자동 취소하지 않음
- **숨기기**: 이 게임의 활성 예약 중 `endDate >= 오늘` → CANCELLED(`GAME_SUSPENDED`). 지난 예약은 이력 유지. `BoardGameSuspendedEvent`에 취소된 예약 id 추가

## 3. ⭐ 동시성 설계

**문제:** 재고 1인 게임에 두 사람이 같은 날짜로 동시에 신청 → 둘 다 "가용 1"을 읽고 INSERT → 초과 예약

**해결: 게임 행 비관적 락 (SELECT … FOR UPDATE)**
```
신청 트랜잭션:
  1) BoardGame을 PESSIMISTIC_WRITE 락으로 조회  ← 같은 게임 신청은 줄 세우기
  2) 기간 규칙·숨김·온라인 전용·중복 검사
  3) 기간 내 활성 예약 조회 → 날짜별 점유 수 계산 → 모든 날짜 가용 ≥ 1 확인
  4) INSERT → 커밋(락 해제)
```
- **재고 수정·오프라인 끄기·숨기기도 같은 락**을 잡음 → 재고를 바꾸는 중에 신청이 끼어드는 경합 방지
- 락 대기 타임아웃 설정(예: 3초). 초과 시 RESERVATION_BUSY(409 "요청이 몰리고 있습니다. 잠시 후 다시 시도해주세요")
- 날짜별 집계는 기간 내 활성 예약 목록을 가져와 자바에서 계산 (범위가 최대 7~62일이라 단순·테스트 쉬움)

**README 비교 포인트 (왜 파티는 Redis, 예약은 DB 락?)**
| | 파티 참여 | 대여 예약 |
|---|---|---|
| 자원 모양 | 파티당 카운터 1개 | 게임 × **날짜별** 재고 (기간 신청) |
| 방식 | Redis DECR | DB 비관적 락 |
| 이유 | 단일 카운터 → 원자 연산이 가장 빠르고 단순 | 날짜 범위 검사는 단일 카운터로 표현 불가, 트래픽 낮음, 정합성을 DB 한 곳에서 보장 |

## 4. API

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/boardgames/{id}/availability?from=&to=` | ❌ | `[{ date, available }]`. 범위 최대 62일, 초과·역순 → 400. 온라인 전용 게임은 409(RESERVATION_NOT_SUPPORTED) |
| POST | `/api/reservations` | 로그인 | `{ boardGameId, startDate, endDate }` → 201 |
| GET | `/api/reservations/me?status=` | 로그인 | 내 예약 (게임명·imageUrl·boardGameVisible·기간·상태·cancelReason·rejectReason) 최신순 |
| PATCH | `/api/reservations/{id}/cancel` | 본인 | 본인 아니면 RESERVATION_NOT_FOUND(404) |
| GET | `/api/admin/reservations?status=` | ADMIN | **내 소유 게임**의 예약 (신청자 닉네임·이름·아바타 포함), 기본 PENDING |
| PATCH | `/api/admin/reservations/{id}/approve` | 소유자 | |
| PATCH | `/api/admin/reservations/{id}/reject` | 소유자 | `{ reason? }` |

- SecurityConfig: `/api/admin/reservations/**` → `hasRole('ADMIN')`, availability GET은 permitAll(기존 `/api/boardgames/**` GET 규칙 확인)
- 이벤트: `ReservationRequestedEvent`(→소유 관리자), `ReservationDecidedEvent`(→신청자), `ReservationCancelledEvent`(회원 취소 → 소유 관리자)

## 5. ErrorCode
RESERVATION_NOT_FOUND(404), INVALID_RESERVATION_PERIOD(400), NOT_AVAILABLE(409), DUPLICATE_RESERVATION(409),
INVALID_RESERVATION_STATUS(409), CANNOT_CANCEL_RESERVATION(409), STOCK_BELOW_RESERVED(409), RESERVATION_BUSY(409),
RESERVATION_NOT_SUPPORTED(409)

## 6. 프론트

- 게임 상세: 로그인 + visible + **오프라인 가능**이면 "예약하기" → `/boardgames/:id/reserve` (비로그인은 "로그인하고 예약하기"). 온라인 전용 게임은 예약 버튼·재고 표시 없음
- 예약 페이지 `/boardgames/:id/reserve` (ProtectedRoute)
  - **당일 / 여러 날** 선택 토글
  - 달력: 오늘 ~ 60일. availability 조회해서 **가용 0인 날짜는 비활성 + "예약 마감" 표시**
  - 재고 2 이상인 게임은 날짜별 남은 수량 표시
  - 여러 날: 범위 선택, 최대 7일, 중간에 마감 날짜가 끼면 선택 불가 안내
  - 달력 라이브러리 사용 가능(예: react-day-picker) — **설치 전 보고**
  - 409 → 서버 메시지 표시 후 availability 재조회
- 내 예약 `/me/reservations` (ProtectedRoute, 내 정보 페이지에서도 링크)
  - 상태 배지: 승인 대기 / 승인 / 거절(사유) / 취소 / 대여 완료(APPROVED + 종료일 지남)
  - 운영 중지로 취소된 건: 「운영 중지」 배지 + "게임 운영 중지로 취소됨"
  - 취소 버튼(취소 가능 조건일 때만)
- 예약 관리 `/admin/reservations` (AdminRoute): 내 게임 예약, 상태 필터(기본 승인 대기), 승인 / 거절(사유 입력 선택)
- 헤더: 로그인 → "내 예약", ADMIN → "예약 관리"
- 쿼리 키: `['availability', gameId, from, to]`, `['reservations', 'me', status]`, `['admin-reservations', status]`
  - 신청·취소 → availability + 내 예약 invalidate / 승인·거절 → admin-reservations invalidate

## 7. 테스트
- `ReservationServiceTest`(Mockito + 고정 Clock): 기간 규칙 4종, 숨김 게임 409, 온라인 전용 게임 409, 소유자 아닌 승인 403, 비PENDING 승인 409, 취소 가능 시점, 재고 줄이기·오프라인 끄기 검증
- 가용 계산: 재고 2, 부분적으로 겹치는 예약들 → 날짜별 값이 정확한지
- **`ReservationConcurrencyTest`** ⭐
  - 비관적 락 검증에는 **실제 PostgreSQL 필요**. 현재 테스트 DB 구성을 확인하고, H2 등이면 Testcontainers `postgres:16` 추가 (**구현 전 보고**)
  - 재고 1 게임, 서로 다른 회원 30명이 같은 날짜로 동시 신청(ExecutorService + CountDownLatch) → 성공 1 / NOT_AVAILABLE 29
  - 재고 3 → 성공 3
  - 서로 부분적으로 겹치는 기간들로 동시 신청 → 어떤 날짜도 재고 초과 없음(DB 기준 검증)
- 숨기기 연동: 미래 예약 CANCELLED(GAME_SUSPENDED), 지난 예약 유지, 그 날짜 availability 복구
- API 통합: 신청 201, 비로그인 401, 내 예약 조회, 관리자 승인/거절, 다른 관리자 403
- `http/reservation.http` 추가
