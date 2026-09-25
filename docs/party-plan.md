# 파티 모집 + Redis 선착순 구현 계획 (feature/party-concurrency)

기준: design.md 2장 PARTY/PARTY_MEMBER, 3-3절, 4장.
member/boardgame 패키지 구조, ApiResponse, BusinessException(ErrorCode), BaseTimeEntity 규칙을 그대로 따른다.

## 0. 의존성 (build.gradle, 없는 것만 추가)
- implementation 'org.springframework.boot:spring-boot-starter-data-redis'
- testImplementation 'org.springframework.boot:spring-boot-testcontainers'
- testImplementation 'org.testcontainers:junit-jupiter'
- Redis가 필요한 테스트는 Testcontainers(redis:7) + @ServiceConnection 으로 실제 Redis 사용
- 기존 auth/boardgame 테스트는 Redis 없이도 통과해야 함 (깨지면 원인 보고)

## 1. 도메인 (party 패키지)
- PartyStatus enum: RECRUITING, CLOSED, CANCELLED
- Party (extends BaseTimeEntity)
  - boardGame: @ManyToOne(LAZY), host: @ManyToOne(LAZY) Member
  - title, description(text), capacity, status(@Enumerated STRING), playAt(nullable)
  - 정적 팩토리 create(...), close(), isHost(memberId), isRecruiting()
- PartyMember
  - party @ManyToOne(LAZY), member @ManyToOne(LAZY), joinedAt
  - @Table(uniqueConstraints = UNIQUE(party_id, member_id))  ← 중복 참여 최종 방어선

## 2. 핵심 규칙
- **호스트는 개설과 동시에 첫 참여자** (PARTY_MEMBER에 INSERT). capacity는 호스트 포함 인원
- capacity 검증: boardGame.minPlayers <= capacity <= boardGame.maxPlayers, 아니면 INVALID_CAPACITY(400)
- 참여/취소는 status == RECRUITING 일 때만. 아니면 PARTY_NOT_RECRUITING(409)
- 정원이 다 차도 status는 RECRUITING 유지 (마감은 호스트가 명시적으로 close)
- 확정 인원은 항상 DB(PARTY_MEMBER COUNT)가 기준. Redis는 선착순 게이트 역할

## 3. Redis 키 (PartyRedisRepository 로 분리, StringRedisTemplate 사용)
- party:{id}:remaining  → 남은 자리 (capacity - 1, 호스트 제외)
- party:{id}:members    → 참여자 memberId Set (중복 참여 빠른 차단)
- 파티 개설 시 키 세팅은 **DB 커밋 이후**(TransactionSynchronization afterCommit)에 실행 → 롤백된 파티의 키가 남지 않게
- **키 복구**: join 시 remaining 키가 없으면(Redis 재시작 등) DB 기준으로 재구성
  - remaining = capacity - COUNT(PARTY_MEMBER), members = 현재 참여자 id들, SET NX 로 한 번만 세팅

## 4. join 흐름 (design.md 4장)
1. 파티 조회(없으면 PARTY_NOT_FOUND 404), RECRUITING 확인
2. 키 없으면 복구(3번)
3. SADD party:{id}:members {memberId} → 0 이면 ALREADY_JOINED(409 "이미 참여한 파티입니다")
4. DECR party:{id}:remaining
   - 결과 < 0 → INCR 원복 + SREM → PARTY_FULL(409 "정원이 마감되었습니다")
5. DB에 PARTY_MEMBER INSERT (별도 트랜잭션)
   - 실패 시(UNIQUE 위반 포함) INCR + SREM 보상 후 예외 재던짐 (UNIQUE 위반은 ALREADY_JOINED)
6. 응답: { remaining: <DECR 결과> }
- 주의: Redis 연산과 DB 트랜잭션을 한 @Transactional 안에 섞지 말 것 (보상 로직이 확실히 실행되게 서비스 메서드 구조 분리)

## 5. leave / close
- leave: 호스트는 탈퇴 불가 → HOST_CANNOT_LEAVE(400). 참여 안 했으면 NOT_JOINED(400)
  - DB DELETE 성공 후 INCR remaining + SREM members
- close: 호스트만 → NOT_PARTY_HOST(403). status = CLOSED, Redis 키 2개 삭제

## 6. 조회
- GET /api/parties?status&boardGameId : 목록. 각 항목에 boardGameName, hostNickname, capacity, currentCount, status
  - N+1 방지: boardGame, host fetch join + 인원수는 party_id IN (...) GROUP BY 한 번으로
- GET /api/parties/{id} : 상세. 참여자 목록(memberId, nickname, joinedAt) + remaining(= capacity - DB count)

## 7. API (3-3절)
| Method | Path | 인증 | 성공 |
|---|---|---|---|
| GET | /api/parties | X | 200 |
| GET | /api/parties/{id} | X | 200 |
| POST | /api/parties | 로그인 | 201 |
| POST | /api/parties/{id}/join | 로그인 | 200 {remaining} |
| DELETE | /api/parties/{id}/leave | 로그인 | 200 |
| PATCH | /api/parties/{id}/close | 호스트 | 200 |
- SecurityConfig: GET /api/parties/** 는 이미 permitAll, 나머지는 authenticated → 수정 불필요
- 컨트롤러는 @AuthenticationPrincipal MemberPrincipal 로 memberId 사용

## 8. ErrorCode 추가
PARTY_NOT_FOUND(404), INVALID_CAPACITY(400), PARTY_NOT_RECRUITING(409), PARTY_FULL(409 "정원이 마감되었습니다"),
ALREADY_JOINED(409 "이미 참여한 파티입니다"), NOT_JOINED(400), HOST_CANNOT_LEAVE(400), NOT_PARTY_HOST(403),
BOARDGAME_IN_USE(409) ← 파티가 있는 보드게임 삭제 시 (BoardGameService.delete 에서 존재 여부 체크)

## 9. 테스트 (⭐ 포트폴리오 핵심)
- PartyServiceTest (Mockito): capacity 범위 검증, 호스트 탈퇴 불가, 비호스트 close 403, 비모집 상태 join 409
- PartyApiIntegrationTest (MockMvc + Testcontainers Redis): 개설 201, 참여 200, 중복 참여 409, 정원 마감 409, 비로그인 401, close 후 join 409
- **PartyConcurrencyTest** (Testcontainers Redis, 서비스 직접 호출)
  - capacity 5(호스트 포함) 파티에 서로 다른 회원 100명이 ExecutorService(32 스레드) + CountDownLatch 로 동시에 join
  - 검증: 성공 정확히 4, PARTY_FULL 96, DB PARTY_MEMBER 수 = 5, Redis remaining = 0
  - 같은 회원이 10번 동시 join → 성공 1, DB 행 1
- 모든 테스트 .\gradlew test 통과

## 10. 수동 확인
- http/party.http (auth.http 형식): 로그인 → 파티 개설 → 상세 → 참여 → 취소 → 마감
