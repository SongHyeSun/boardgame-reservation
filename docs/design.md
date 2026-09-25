# 설계 문서 — 요구사항 · ERD · API 명세

## 1. 요구사항

**회원/권한**
- 회원가입 (이메일·비밀번호·닉네임)
- 로그인/로그아웃 (세션 기반, 세션은 Redis에 저장 → 세션 외부화)
- 역할: USER, ADMIN

**보드게임 (ADMIN 관리)**
- 관리자가 게임 등록/수정/삭제
- 누구나 목록·상세 조회 (인원·난이도 필터)

**파티 모집 (핵심)**
- 로그인 회원이 특정 게임으로 파티 개설 (정원 설정)
- 다른 회원이 선착순 참여
- 동시성 제어: 동시 요청 시 정원 초과 방지 (Redis 원자적 연산)
- 참여 취소, 호스트의 파티 마감

**AI 챗봇**
- 자연어로 게임 추천 요청 → 등록된 게임(DB) 기반 추천

**비기능**
- 동시성: 정원 N인 파티에 동시 요청이 몰려도 초과 참여 없음
- 세션: 다중 인스턴스에서도 유지 (Redis 외부화)
- 배포: Docker → AWS EC2

**제외(시간 남으면)**: 대여 날짜 예약+승인, 알림

## 2. ERD

MEMBER
- id bigint PK
- email varchar unique (로그인 ID)
- password varchar (BCrypt)
- nickname varchar
- role varchar (USER/ADMIN)
- created_at timestamp

BOARD_GAME
- id bigint PK
- name varchar
- min_players int
- max_players int
- play_time int (분)
- difficulty varchar (EASY/NORMAL/HARD)
- description text
- created_at timestamp

PARTY
- id bigint PK
- board_game_id FK → BOARD_GAME
- host_id FK → MEMBER (개설자)
- title varchar
- description text
- capacity int (정원)
- status varchar (RECRUITING/CLOSED/CANCELLED)
- play_at timestamp (선택)
- created_at timestamp

PARTY_MEMBER
- id bigint PK
- party_id FK → PARTY
- member_id FK → MEMBER
- joined_at timestamp
- UNIQUE(party_id, member_id) → 중복 참여 방지

관계: MEMBER 1:N PARTY(host), MEMBER 1:N PARTY_MEMBER, BOARD_GAME 1:N PARTY, PARTY 1:N PARTY_MEMBER
- 현재 참여 인원 = PARTY_MEMBER COUNT (DB 기준값)
- 실시간 선착순 카운터는 Redis (4장). 현재인원 컬럼은 두지 않음 (락 경합 회피)

## 3. API 명세

- Base URL: /api
- 인증: 세션 기반
- 공통 응답: { "success": true, "data": {...}, "message": null }

### 3-1. 인증
| Method | Path | 설명 | 인증 | body | 성공 |
|---|---|---|---|---|---|
| POST | /api/auth/signup | 회원가입 | X | {email, password, nickname} | 201 |
| POST | /api/auth/login | 로그인 | X | {email, password} | 200 + 세션 |
| POST | /api/auth/logout | 로그아웃 | O | - | 200 |
| GET | /api/members/me | 내 정보 | O | - | 200 |

### 3-2. 보드게임
| Method | Path | 설명 | 인증 | 비고 |
|---|---|---|---|---|
| GET | /api/boardgames | 목록 | X | 쿼리: players, difficulty, keyword |
| GET | /api/boardgames/{id} | 상세 | X | |
| POST | /api/boardgames | 등록 | ADMIN | {name, minPlayers, maxPlayers, playTime, difficulty, description} |
| PUT | /api/boardgames/{id} | 수정 | ADMIN | |
| DELETE | /api/boardgames/{id} | 삭제 | ADMIN | |

### 3-3. 파티
| Method | Path | 설명 | 인증 | 비고 |
|---|---|---|---|---|
| GET | /api/parties | 목록 | X | 쿼리: status, boardGameId |
| GET | /api/parties/{id} | 상세(참여자·남은정원) | X | |
| POST | /api/parties | 개설 | USER | {boardGameId, title, description, capacity, playAt} |
| POST | /api/parties/{id}/join | 선착순 참여 | USER | 4장 로직 |
| DELETE | /api/parties/{id}/leave | 참여 취소 | USER | |
| PATCH | /api/parties/{id}/close | 마감 | host | |

join 응답
- 성공: 200 { success:true, data:{ remaining: 2 } }
- 정원 마감: 409 "정원이 마감되었습니다"
- 중복 참여: 409 "이미 참여한 파티입니다"

### 3-4. AI 챗봇
| Method | Path | 인증 | body |
|---|---|---|---|
| POST | /api/chat/recommend | O | {message} |
응답: { recommendations: [{gameId, name, reason}], answer: "..." }

## 4. 동시성 제어 (Redis DECR)

```
파티 개설 시: SET party:{id}:remaining = capacity
참여 시:
  remaining = DECR party:{id}:remaining
  if remaining >= 0 → PARTY_MEMBER INSERT (실패 시 INCR 롤백) → 200
  else → INCR 원복 → 409 정원 마감
```
- 중복 참여 최종 방어: PARTY_MEMBER UNIQUE(party_id, member_id)
- (선택) 참여 전 SADD party:{id}:members {memberId} 로 선필터

비교(README용): 비관적 락(단순/락경합) vs 낙관적 락(재시도 필요) vs Redis DECR(채택: 빠름·원자적, 정합성 관리 필요)

## 5. 패키지 구조

```
com.boardgame.reservation
├── global (config, exception, response, security, common)
├── member (domain, repository, service, controller, dto)
├── boardgame (동일 구조)
├── party (Redis 동시성 로직)
└── chat
```

## 6. 구현 순서

| 순서 | 내용 | 브랜치 |
|---|---|---|
| 1 | 공통 설정 + 회원/권한 | feature/auth |
| 2 | 보드게임 CRUD | feature/boardgame |
| 3 | 파티 + Redis 선착순 + 동시성 테스트 | feature/party-concurrency |
| 4 | 세션 외부화 (Spring Session + Redis) | feature/session |
| 5 | React 프론트 | feature/frontend |
| 6 | AI 챗봇 | feature/chatbot |
| 7 | Docker·AWS 배포 + README | feature/deploy |
