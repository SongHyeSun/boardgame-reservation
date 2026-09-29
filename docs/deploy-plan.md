# 7단계 배포 구현계획 — 무료 구성 (2026-09-29 확정)

레포에는 `docs/deploy-plan.md`로 넣는다. 진행 흐름은 기존 단계와 같다(브랜치 → plan mode → claude.ai 검토 → 구현 → 테스트 → 커밋 → main 머지·push).

## 0. 결정 사항
- **전부 무료 플랜으로 배포**하고, 좀 더 완성되면 유료 전환 여부를 다시 판단한다.
- 구성

| 역할 | 서비스 | 비고 |
|---|---|---|
| 프론트 | **Vercel** (Hobby) | `frontend/` 빌드 결과물. `vercel.json` rewrites로 `/api/*` → Render 프록시 |
| 백엔드 | **Render** Free Web Service | Dockerfile로 배포, 리전 **Singapore** |
| DB | **Neon** Free | PostgreSQL 16, 리전 **AWS Asia Pacific (Singapore)** |
| Redis | **Upstash** Free | 리전 **ap-southeast-1 (Singapore)**, TLS(`rediss://`) |
| 업로드 이미지 | **Cloudflare R2** | S3 호환, 비공개 버킷 |

- **Redis를 Render Key Value가 아닌 Upstash로 고른 이유**: Render 무료 Key Value는 재시작하면 데이터가 전부 사라진다(세션 → 전원 로그아웃, 파티 잔여석 카운터 유실). Upstash는 데이터가 유지되고, 대신 **월 50만 명령** 한도가 있다.
- **같은 출처 유지**: 브라우저는 Vercel 주소만 보고, `/api`는 Vercel이 Render로 넘긴다 → 지금의 세션 쿠키·CORS 설정을 그대로 쓴다(SameSite=None 불필요).
- **누가 무엇을 하나**

| 누가 | 하는 일 |
|---|---|
| Claude Code | 코드·설정 파일 (Dockerfile, prod 프로필, Flyway, R2 저장소, vercel.json, 문서) |
| 사용자 직접 | 서비스 가입, 대시보드 설정, **비밀값 입력**, 스키마 추출 명령, git, 배포 후 확인 |
| claude.ai | 계획 검토, 대시보드 화면별 안내, 문제 생기면 원인 분석 |

## 1. 무료 한도 요약 (2026-09 확인)
- **Render Free**: RAM 512MB / CPU 0.1, 월 750 인스턴스 시간(서비스 1개 상시 가동이면 충분). **15분간 들어오는 요청이 없으면 잠들고 깨는 데 약 1분**. 재배포·재시작·잠들기 때마다 로컬 파일 삭제. 영구 디스크·SSH 없음. 수시 재시작 가능
- **Neon Free**: 프로젝트당 월 100 CU-hours(0.25 CU로 약 400시간), 저장 0.5GB, 전송 5GB. 5분 무활동 시 일시정지 → 다음 연결에 짧은 지연. 한도 초과 시 다음 달까지 정지(데이터는 유지)
- **Upstash Free**: 월 50만 명령, 256MB, DB 1개
- **Cloudflare R2**: 매달 저장 10GB, 쓰기(Class A) 100만, 읽기(Class B) 1000만 회, 전송료 없음. 활성화할 때 결제 수단 등록을 요구할 수 있음(한도 안이면 청구 0)
- **Vercel Hobby**: 개인·비상업용. 외부로 프록시한 요청은 **최대 120초**까지만 처리

## 2. 알려진 제약과 대응
| 제약 | 영향 | 대응 |
|---|---|---|
| Render 잠들기 | 오래 안 쓰다 처음 접속하면 1분 남짓 대기 | 수용. (선택) 프론트에서 첫 요청이 오래 걸리면 "서버를 깨우는 중이에요(최대 1분)" 안내 |
| Vercel 프록시 120초 | SSE 연결이 최대 2분 안에 끊김. 콜드 스타트와 겹친 요청이 실패할 수 있음 | prod에서 SseEmitter 타임아웃을 **약 110초**로 두어 서버가 먼저 깔끔하게 닫고 EventSource가 자동 재연결. 챗봇 타임아웃 합계도 120초 미만 |
| 512MB / 0.1 CPU | Spring Boot 기동 느림, 메모리 초과 시 강제 종료 | JVM 옵션으로 힙 제한, 로컬에서 `--memory=512m`으로 미리 기동 확인 |
| 파일시스템 휘발 | 업로드 이미지 소실 | `FileStorage` R2 구현체 추가 (로컬 개발은 기존 로컬 저장 유지) |
| Redis 초기화 가능성 | 잔여석 카운터 없음 → 파티 참여 오동작 | 키가 없으면 DB 기준으로 다시 계산해 채우는지 확인, 없으면 추가 |
| `ddl-auto: update` | 운영 DB에서 스키마 사고 위험 | **Flyway** 도입, 운영은 `validate` |
| Upstash 명령 한도 | Spring Session이 요청마다 Redis 호출, indexed 저장소는 1분마다 정리 작업 | 배포 1주 후 사용량 확인. 넘칠 것 같으면 그때 대책 |

## 3. 작업 순서

### D0. 스키마 추출 (사용자, 약 10분) — Flyway V1의 원본
옛 로컬 DB는 `ddl-auto: update`를 거치며 찌꺼기(옛 컬럼·CHECK)가 있을 수 있으므로, **빈 DB에 현재 엔티티로 새로 만든 스키마**를 뽑는다.
1. `docker compose up -d` 후 빈 DB 생성 (서비스 이름·계정은 docker-compose.yml 기준으로 바꿔서)
   `docker compose exec postgres createdb -U <DB계정> boardgame_schema`
2. IntelliJ 실행 구성을 복사해서 환경 변수만 바꿔 한 번 기동 → 기동 완료되면 종료
   `DB_URL=jdbc:postgresql://127.0.0.1:5432/boardgame_schema;SPRING_JPA_HIBERNATE_DDL_AUTO=create;ADMIN_EMAIL=...;ADMIN_PASSWORD=...`
3. 스키마만 덤프 (PowerShell `>` 리다이렉트는 인코딩이 깨질 수 있어 컨테이너 안에서 파일로 만든 뒤 복사)
   `docker compose exec postgres pg_dump -U <DB계정> -d boardgame_schema --schema-only --no-owner --no-privileges -f /tmp/schema.sql`
   `docker compose cp postgres:/tmp/schema.sql .\schema.sql`
4. `schema.sql`을 레포 루트에 두고 Claude Code에 넘긴다(커밋하지 않음, 작업 후 삭제)

### D1. 백엔드 배포 준비 (브랜치 `feature/deploy`, Claude Code)
**구현 전 보고 (먼저 조사해서 계획 맨 앞에)**
1. `application.yml` 구조와 프로필 사용 여부, DB 계정·비밀번호가 어디서 오는지
2. `FileStorage` 인터페이스·구현체, 파일 서빙 컨트롤러(`GET /api/files/...`) 구조
3. 파티 잔여석 Redis 키를 언제 만드는지, **키가 없을 때** 참여·취소가 어떻게 동작하는지
4. `SseEmitter` 타임아웃·하트비트 주기, 연결 종료 시 정리 방식
5. Spring Session 설정(indexed, `configure-action`), Redis 연결 설정 방식
6. Spring Boot 4에서 Flyway에 필요한 의존성(`spring-boot-starter-flyway` + `flyway-database-postgresql` 여부)과 H2 테스트 영향
7. SecurityConfig에서 공개 경로를 추가하는 방식
8. 챗봇 RestClient 타임아웃과 재시도를 합친 최악 소요 시간

**구현**
- a. `application-prod.yml`
  - `server.port: ${PORT:8080}`, `server.forward-headers-strategy: framework`
  - DB: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수, Hikari 최대 풀 크기 작게(예: 5)
  - `spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.enabled: true`
  - Redis: `spring.data.redis.url: ${REDIS_URL}` (`rediss://` TLS)
  - 세션 쿠키 `secure: true`, `same-site: lax`
  - 저장소 `app.storage.type: s3` + R2 설정값
- b. **Flyway**: 의존성 추가, `db/migration/V1__init.sql` (D0 덤프를 정리: `SET`/`SELECT pg_catalog...`/소유자 문 제거, 테이블·제약·인덱스·시퀀스만)
  - 로컬 개발 DB: 기존 데이터가 있으므로 로컬 프로필은 `baseline-on-migrate`로 처리하거나, 로컬 DB를 새로 만드는 방법 중 계획에서 제안
  - 테스트: H2 테스트는 지금처럼 Flyway 끄고 `create-drop` 유지. **Testcontainers PostgreSQL 테스트 하나를 추가해 V1 적용 후 `validate`가 통과하는지 검증**(엔티티-스키마 일치 확인)
- c. **Spring Session + Upstash**: Upstash는 `CONFIG SET notify-keyspace-events`를 지원하므로 기본 설정으로 먼저 시도. 막히면 `configure-action: none`으로 바꾸고 콘솔에서 직접 설정(방법은 README에 기록)
- d. **R2 `FileStorage` 구현체**: AWS SDK v2 S3 클라이언트(엔드포인트 R2), `app.storage.type=local|s3`로 선택, 로컬 기본값은 기존 로컬 저장. **`/api/files/{avatars|boardgames}/{uuid}.{ext}` 주소 계약은 유지**(백엔드가 R2에서 읽어 응답, 캐시 헤더). 프론트 변경 없음. 테스트는 S3 클라이언트 모킹 단위 테스트
- e. **잔여석 카운터 복구**: 보고 3번 결과에 따라, 키가 없으면 DB 기준으로 계산해 `SET NX`로 채우는 로직 추가(동시성 테스트 수치 유지)
- f. **SSE**: prod에서 emitter 타임아웃 110초(설정값화, Vercel 120초보다 먼저 닫기), 하트비트는 그보다 짧게
- g. **챗봇**: 타임아웃 합계가 120초 미만이 되도록 prod 값 조정(설정값만)
- h. **헬스 체크** `GET /api/health`: DB·Redis를 건드리지 않는 가벼운 200 응답, permitAll (Render Health Check Path)
- i. **Dockerfile** (레포 루트, 멀티 스테이지: Gradle 빌드 `-x test` → JRE 17 slim, non-root 실행) + `.dockerignore` (`frontend/`, `uploads/`, `.git/`, `build/`, `.gradle/`, `*.sql`)
  - 기본 `JAVA_TOOL_OPTIONS` 예시: `-Xmx300m -Xss512k -XX:MaxMetaspaceSize=150m -XX:+UseSerialGC -XX:ActiveProcessorCount=1` — 시작값이고 Render 메모리 그래프를 보고 조정
- j. `.env.example`(키 이름만, 값 없음), README 배포 절, CLAUDE.md에 prod 프로필·환경 변수 설명

**검증**
- `.\gradlew test` 전체 통과(기존 583개 + 추가분), 동시성 테스트 수치 동일
- 로컬 Docker로 prod 이미지 확인 (사용자 실행): `docker build -t boardgame-api .` → 로컬 compose의 Postgres(새 빈 DB)·Redis에 붙여 `--memory=512m`으로 기동 → Flyway 적용, `/api/health` 200, 로그인까지 확인
- 커밋 → 프론트 작업(D2)까지 같은 브랜치에서 한 뒤 main 머지·push

### D2. 프론트 (Claude Code, 같은 브랜치)
- `frontend/vercel.json`
  ```json
  {
    "rewrites": [
      { "source": "/api/:path*", "destination": "https://<render-서비스>.onrender.com/api/:path*" },
      { "source": "/(.*)", "destination": "/index.html" }
    ]
  }
  ```
  (두 번째는 react-router 새로고침 404 방지. Render 주소는 D3에서 확정 후 채움)
- API·SSE 주소가 상대경로 `/api`인지 확인 (그렇다면 변경 없음)
- (선택) 첫 요청이 오래 걸릴 때 "서버를 깨우는 중" 안내
- `npm run lint && npm run build`

### D3. 서비스 가입·설정 (사용자 직접, claude.ai에서 화면별 안내)
순서: **Neon → Upstash → R2 → Render → Vercel**. 모두 GitHub 계정으로 가입 가능한 곳은 GitHub로.
1. **Neon**: 프로젝트 생성(Postgres 16, Singapore) → 연결 정보에서 host·db·user·password 확인 → `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`
2. **Upstash**: Redis DB 생성(Singapore) → TLS 연결 주소 `rediss://default:<비밀번호>@<host>:6379`
3. **Cloudflare R2**: R2 활성화 → 버킷 생성(공개 접근 끔) → API 토큰(Object Read & Write, 이 버킷만) → Access Key ID·Secret, 엔드포인트 `https://<account_id>.r2.cloudflarestorage.com`
4. **Render**: New → Web Service → GitHub 레포 → Runtime **Docker**, Region **Singapore**, Instance **Free**, Branch `main`, Health Check Path `/api/health`, 환경 변수 입력(아래 표) → 배포 → 로그에서 Flyway 적용·기동 확인 → `.onrender.com` 주소 확보
5. **Vercel**: Import → 같은 레포, **Root Directory `frontend`**, Framework Vite → `vercel.json`의 Render 주소 반영 커밋 → 배포

**Render 환경 변수** (값은 대시보드에만 입력, 레포·채팅 금지)

| 이름 | 내용 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Neon |
| `REDIS_URL` | Upstash `rediss://...` |
| `R2_ENDPOINT` / `R2_BUCKET` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | R2 (이름은 D1 구현에 맞춤) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | 운영 관리자. **로컬(admin1234!)과 다른 강한 비밀번호** |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | 챗봇 |
| `JAVA_TOOL_OPTIONS` | JVM 메모리 옵션 |

### D4. 배포 후 확인 체크리스트
- [ ] `https://<vercel주소>/api/health` 200 (Vercel → Render 프록시 동작)
- [ ] Neon SQL Editor에서 테이블과 `flyway_schema_history` 확인
- [ ] 회원가입·로그인, 새로고침해도 로그인 유지, 쿠키 `Secure`
- [ ] 관리자 로그인 → 게임 등록 + 이미지 업로드 → R2 버킷에 객체 생성, 화면에 이미지 표시
- [ ] **Render에서 수동 재시작(Manual Deploy → Restart)** 후: 이미지 남아 있음, 로그인 유지, 파티 잔여석 정상
- [ ] 파티 개설·참여·취소·마감, 대여 예약·승인
- [ ] 브라우저 두 개로 실시간 알림 도착 → **3분 이상 열어둔 뒤에도** 알림 도착(재연결 확인)
- [ ] 챗봇 (Gemini 503이 풀렸다면 실제 추천까지)
- [ ] 15분 이상 방치 후 접속 → 대기 시간 측정
- [ ] 휴대폰으로 접속
- [ ] 1주 후: Render 메모리 그래프, Upstash 명령 수, Neon CU-hours 사용량

### D5. 문서
- README: 배포 구성도(Vercel → Render → Neon/Upstash/R2), 무료 제약과 대응, 환경 변수 목록
- `docs/troubleshooting.md`: 배포 중 생긴 문제 기록
- 핸드오프 문서 갱신

## 4. 이후 (선택)
- GitHub Actions로 PR마다 테스트 (배포는 main push 시 Render·Vercel이 자동)
- 커스텀 도메인, PWA(HTTPS는 Vercel 기본 주소로 이미 충족)
- 유료 전환을 검토한다면 효과가 가장 큰 건 **Render 백엔드만 상시 가동 플랜으로** 올리는 것(잠들기·파일 휘발·메모리 문제가 함께 풀림)
