# 보드게임 동아리 예약·파티 모집 시스템

보드게임 동아리를 위한 **파티 모집 · 게임 대여 예약 · 실시간 알림 · AI 게임 추천** 웹 서비스입니다.  
실무에서 접하기 어려웠던 **Spring Boot·React·Redis·동시성 제어·AI 연동·클라우드 배포**를 직접 경험하기 위해 시작한 개인 프로젝트이며, Cursor와 Claude Code를 적극 활용한 **AI 페어코딩(VIBE Coding)** 방식으로 개발했습니다.

- **서비스**: https://boardgame-reservation.vercel.app
- 무료 플랜으로 운영 중이므로 장시간 미사용 후 첫 접속 시 백엔드가 깨어나는 데 수 분(기동 시간 실측 약 3.5분)이 걸릴 수 있습니다.

---

## 프로젝트를 시작한 이유

실무에서는 eGovFramework·Spring MVC·Oracle Stored Procedure 기반의 대학 종합정보시스템을 개발·운영하고 있습니다.

업무 중 다중 WAS 환경에서 발생한 세션 유실 문제를 분석하며 **Redis 기반 세션 외부화**가 근본적인 해결 방향이라는 점을 확인했지만, 당시에는 인프라 접근 권한의 한계로 직접 적용하지 못했습니다.

이를 개념으로만 이해하는 데 그치지 않고 직접 구현해보기 위해 이 프로젝트를 시작했습니다.  
Spring Session + Redis 기반 세션 외부화에서 출발해 다음 영역까지 경험 범위를 확장했습니다.

- Redis 원자 연산을 활용한 선착순 동시성 제어
- PostgreSQL 비관적 락을 활용한 대여 재고 동시성 제어
- SSE + Redis Pub/Sub 기반 실시간 알림
- Gemini API 기반 게임 추천 챗봇
- Docker 기반 실행 환경
- Vercel · Render · Neon · Upstash · Cloudflare R2를 활용한 클라우드 배포
- Flyway · Testcontainers를 활용한 운영 스키마 및 통합 테스트 검증

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| 회원·권한 | 세션 로그인, 프로필, 일반회원·관리자 권한 분리, 관리자 가입 승인 |
| 보드게임 | 게임 등록·수정·검색·필터, 이미지·유튜브, 재고·소유 관리자, 숨기기 |
| 파티 모집 | 정원 기반 선착순 참여·취소·마감, 호스트의 멤버 관리 |
| 대여 예약 | 기간 대여, 재고 점유, 소유 관리자의 승인·거절 |
| 실시간 알림 | SSE + Redis Pub/Sub 기반 알림, 재연결 시 목록 재조회 |
| AI 추천 | Gemini function calling을 이용해 DB에 등록된 게임을 기준으로 추천 |

---

## 기술 스택

### Backend
- Java 17
- Spring Boot 4
- Spring Security
- Spring Data JPA
- Spring Session
- Flyway
- Gradle

### Frontend
- React 19
- TypeScript
- Vite
- TanStack Query
- Tailwind CSS 4

### Data
- PostgreSQL 16
- Redis 7

### Infra
- Docker
- Vercel
- Render
- Neon
- Upstash
- Cloudflare R2

### Test
- JUnit
- Testcontainers (PostgreSQL, Redis)
- 백엔드 자동 테스트 **609개**

### AI / Development
- Google Gemini API
- Cursor
- Claude Code

---

## 아키텍처

```text
Browser
  │
  │ https://boardgame-reservation.vercel.app
  ▼
Vercel
  │ React 정적 파일
  │ /api/* → Render 프록시
  ▼
Render
  │ Spring Boot + Docker
  │
  ├── Neon
  │    └── PostgreSQL / Flyway
  │
  ├── Upstash
  │    └── Redis / Session / Party Counter / Pub/Sub / AI Usage Limit
  │
  ├── Cloudflare R2
  │    └── 업로드 이미지
  │
  └── Gemini API
       └── AI 게임 추천
```

브라우저에서는 Vercel 주소만 사용하고 `/api/*` 요청은 Vercel rewrites를 통해 Render로 전달합니다.  
이를 통해 프론트엔드와 백엔드가 별도 서비스에 배포되어 있어도 브라우저 관점에서는 같은 출처를 유지하도록 구성했습니다.

---

# 핵심 기술적 도전

## 1. Redis 원자 연산을 이용한 선착순 동시성 제어

### 문제

정원이 정해진 파티에 여러 사용자가 동시에 참여하면, 단순히 DB의 현재 인원만 조회한 뒤 저장하는 방식으로는 정원을 초과할 수 있습니다.

### 해결

- Redis에 파티별 잔여석을 저장
- 참여 요청 시 `DECR` 원자 연산으로 좌석을 먼저 선점
- 같은 회원의 중복 참여는 Redis Set으로 차단
- 이후 DB에 참여 결과 저장
- DB 저장이 실패하면 Redis 잔여석을 복구
- Redis 키가 없는 경우 DB의 실제 참여 인원을 기준으로 잔여석을 다시 계산

### 검증

정원 5명인 파티(호스트 1명이 정원에 포함)에 대해 **100건의 동시 참여 요청**을 발생시키는 테스트를 수행했고, 성공 4건 · 정원 초과 거절 96건, 최종 DB 참여자 수 5명(호스트 포함)과 잔여석 0을 유지하는 것을 확인했습니다.

같은 회원이 동시에 여러 번 참여를 요청하는 상황도 테스트해 중복 참여가 발생하지 않도록 검증했습니다.

---

## 2. PostgreSQL 비관적 락을 이용한 대여 재고 제어

### 문제

재고가 한정된 보드게임을 같은 기간에 여러 사용자가 동시에 예약하면 날짜별 재고를 초과할 수 있습니다.

### 해결

- PostgreSQL `PESSIMISTIC_WRITE` 사용
- 락 타임아웃 3초 설정
- 승인 대기(`PENDING`) 상태도 재고 점유에 포함
- 예약 기간이 겹치는 경우 날짜별 사용 가능 재고를 검증

### 검증

- 재고 1개에 30건 동시 예약 요청 → 최종 성공 1건
- 재고 3개 환경 → 동시에 최대 3건만 성공
- 부분적으로 기간이 겹치는 요청에서도 날짜별 재고 초과가 발생하지 않음을 확인

또한 H2에서는 통과하던 `SELECT ... FOR UPDATE` 관련 테스트가 실제 PostgreSQL의 읽기 전용 트랜잭션에서는 다르게 동작하는 것을 확인해, 동시성 테스트는 **Testcontainers PostgreSQL**을 사용하도록 변경했습니다.

---

## 3. Spring Session + Redis를 이용한 세션 외부화

실무에서 접했던 다중 WAS 환경의 세션 유실 문제를 직접 구현해보기 위해 Spring Session + Redis 구조를 적용했습니다.

애플리케이션 메모리가 아닌 Redis에 세션을 저장하도록 구성해 서버 인스턴스와 세션의 생명주기를 분리했습니다.

또한 관리자 승인으로 회원 권한이 변경될 때 기존 세션을 무효화해 변경된 권한이 즉시 반영되도록 처리했습니다.

---

## 4. SSE + Redis Pub/Sub 기반 실시간 알림

파티 참여·예약 처리 등 서비스 내 이벤트를 실시간으로 전달하기 위해 SSE를 사용했습니다.

서버가 여러 대로 확장되는 상황에서도 이벤트를 전달할 수 있도록 Redis Pub/Sub을 함께 적용했습니다. (현재 배포는 단일 인스턴스이며, 다중 인스턴스 환경에서의 동작은 테스트하지 않았습니다.)

배포 환경에서는 Vercel 프록시의 연결 시간 제한을 고려해 서버 SSE 타임아웃을 110초로 설정했고, 연결 종료 후 브라우저가 자동으로 재연결하도록 구성했습니다. 재연결 시 알림 목록을 다시 조회해 연결이 끊긴 구간의 알림도 보완합니다.

---

## 5. AI 챗봇 — 추천 범위 검증과 비용 제어

Gemini function calling을 이용해 사용자의 조건에 맞는 보드게임을 추천합니다.

AI가 존재하지 않는 게임을 임의로 추천하는 것을 줄이기 위해 다음과 같이 검증합니다.

1. Gemini가 DB 검색 도구 호출
2. 이번 요청에서 도구가 실제 반환한 게임 확인
3. 현재 DB에 존재하고 노출 가능한 게임인지 재검증
4. 게임 상세 정보는 AI 응답이 아닌 DB 값을 기준으로 반환

추가로 다음과 같은 운영 제약도 반영했습니다.

- 회원당 하루 20회 호출 제한 — Redis
- LLM 호출 실패 시 사용 횟수 복구
- LLM 응답을 기다리는 동안 DB 커넥션을 점유하지 않도록 서비스 트랜잭션 미사용
- `LlmClient` 인터페이스를 분리해 다른 모델로 교체 가능
- 호출·재시도·도구 라운드의 총 소요시간을 제한하기 위해 운영 환경에 전체 시간 예산 설정

구현과 단위 테스트(외부 API 모킹)까지 완료했으며, 개발 기간 중 Gemini 서버의 일시적 과부하(503)로 실제 추천 응답 확인은 진행 중입니다.

---

## 6. 무료 클라우드의 제약을 설계로 해결

개인 프로젝트이기 때문에 비용을 최소화하면서도 실제 배포 환경을 경험하는 것을 목표로 했습니다.

단순히 무료 서비스를 선택하는 데 그치지 않고, 각 서비스의 제약이 애플리케이션에 미치는 영향을 확인하고 구조를 변경했습니다.

| 제약 | 대응 |
|---|---|
| Render 무료 환경은 재시작 시 로컬 파일이 유지되지 않음 | 업로드 이미지를 Cloudflare R2에 저장 |
| Redis 상태 데이터 유지 필요 | Upstash Redis 사용 + 잔여석 키가 없으면 DB 기준 복구 |
| 프론트·백엔드가 다른 도메인 | Vercel rewrites로 `/api`를 Render로 프록시해 같은 출처 유지 |
| Vercel 프록시 연결 시간 제한 | SSE 타임아웃 조정 + 자동 재연결 + 알림 재조회 |
| Render 512MB 메모리 | JVM 힙·메타스페이스 제한 후 로컬에서도 512MB 조건으로 검증 |
| Neon 무료 컴퓨트 시간 제한 | Hikari 유휴 커넥션 최소화 |
| Upstash 명령 수 제한 | Spring Session 정리 주기 조정 |
| `ddl-auto: update`의 운영 위험 | Flyway + `validate` 방식으로 전환 |

---

# AI를 활용한 개발 방식

이 프로젝트는 Cursor와 Claude Code를 적극 활용한 **AI 페어코딩(VIBE Coding)** 방식으로 개발했습니다.

단순히 한 번에 전체 코드를 생성하는 방식보다 기능을 단계별로 나누고 문서와 테스트를 기준으로 진행했습니다.

```text
요구사항 정리
    ↓
기능·API·데이터 구조 구체화
    ↓
단계별 구현 계획 작성
    ↓
Cursor / Claude Code를 이용한 구현
    ↓
자동 테스트
    ↓
직접 실행 및 기능 확인
    ↓
문제 수정
    ↓
Git 브랜치 병합
```

주요 기능별 요구사항과 구현 계획은 Markdown 문서로 남겼고, AI가 생성한 결과가 실제 요구사항대로 동작하는지는 테스트와 직접 실행을 통해 확인했습니다.

개발 과정에서 문서화한 항목에는 다음 내용이 포함됩니다.

- 요구사항
- ERD
- API 정의
- 기능별 구현 계획
- 배포 계획
- 트러블슈팅 기록

AI를 이용해 기존에 경험하지 못했던 기술의 구현 방식을 빠르게 탐색하면서도, 최종 결과가 실제로 동작하는지를 코드·테스트·실행 결과를 통해 확인하는 방식으로 프로젝트를 진행했습니다.

---

# 테스트 및 검증

현재 백엔드 기준 **609개의 자동 테스트**를 구성했습니다.

| 영역 | 검증 방식 |
|---|---|
| 일반 서비스 로직 | JUnit + H2 |
| PostgreSQL 동작 및 비관적 락 | Testcontainers PostgreSQL |
| Redis 기반 기능 및 동시성 | Testcontainers Redis |
| Flyway | 실제 PostgreSQL에 V1 적용 후 Hibernate `validate` 검증 |
| 파티 동시성 | 100건 동시 참여 요청 |
| 예약 동시성 | 제한된 재고에 대한 다중 동시 예약 요청 |

특히 운영 환경에서 사용하는 PostgreSQL·Redis의 실제 동작과 테스트 환경의 차이를 줄이기 위해, 동시성 및 DB 스키마 검증에는 Testcontainers를 사용했습니다.

---

# 배포

## 배포 구성

| 역할 | 서비스 |
|---|---|
| Frontend | Vercel |
| Backend | Render Free Web Service + Docker |
| Database | Neon PostgreSQL |
| Redis | Upstash |
| Image Storage | Cloudflare R2 |
| AI | Gemini API |

운영 DB 스키마는 Flyway로 관리하고, Hibernate는 `validate`를 사용해 엔티티와 스키마의 불일치를 확인합니다.

이미지 저장소는 인터페이스로 분리해 로컬 환경에서는 로컬 파일 저장, 운영 환경에서는 R2를 사용하도록 구성했습니다.

---

# 로컬 실행

## 요구사항

- JDK 17
- Docker
- Node.js 24

### 1. PostgreSQL / Redis 실행

```bash
docker compose up -d
```

### 2. 백엔드

필요 환경 변수:

```text
DB_URL
ADMIN_EMAIL
ADMIN_PASSWORD
GEMINI_API_KEY     # 선택
```

실행:

```bash
./gradlew bootRun
```

기본 API 주소:

```text
http://localhost:8080
```

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

기본 주소:

```text
http://localhost:5173
```

개발 환경에서 `/api` 요청은 Vite proxy를 통해 Spring Boot `8080` 포트로 전달됩니다.

`GEMINI_API_KEY`가 없으면 나머지 기능은 사용할 수 있고 AI 챗봇만 비활성화됩니다.

---

# 프로젝트에서 경험한 것

이 프로젝트를 통해 새로운 프레임워크의 문법 자체보다 **실제 서비스에서 발생할 수 있는 문제를 어떻게 구조적으로 해결할지**를 경험하는 데 집중했습니다.

- 단순 CRUD를 넘어선 동시성 문제 재현과 해결
- Redis를 캐시가 아닌 세션·원자 연산·Pub/Sub·사용량 제한에 활용
- 테스트 환경과 실제 PostgreSQL의 차이 확인
- 운영 DB 스키마를 Flyway로 관리
- 상태를 가진 애플리케이션을 무료 클라우드 환경에 배포
- 무료 서비스의 제약을 애플리케이션 설계에 반영
- AI API의 환각·호출 제한·장애 상황 고려
- AI 페어코딩을 활용한 새로운 기술 스택 학습

---

# 한계와 향후 계획

현재 프로젝트는 개인 포트폴리오 및 소규모 사용 환경을 기준으로 개발했습니다.

- Render 무료 플랜 특성상 장시간 요청이 없으면 서버가 잠들며, 다시 깨어날 때 첫 접속이 수 분간 느릴 수 있습니다.
- 대규모 트래픽 환경에서의 부하 테스트는 수행하지 않았습니다.
- 동시성은 실제 Redis·PostgreSQL 기반의 통합 테스트 수준에서 검증했습니다.

향후 다음 기능을 추가할 예정입니다.

- 남은 화면 디자인 정리
- PWA 적용
- GitHub Actions 기반 CI
- 운영 사용량 및 클라우드 리소스 사용 패턴 확인

---

# 문서

`docs/` 디렉터리에 기능별 설계 및 구현 계획을 정리했습니다.

- 요구사항 / ERD / API
- 파티 모집 설계
- 회원 및 권한 설계
- 보드게임 관리 설계
- 대여 예약 설계
- 실시간 알림 설계
- AI 챗봇 설계
- 배포 계획
- Troubleshooting
