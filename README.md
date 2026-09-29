# 보드게임 예약·파티 모집 시스템

Spring Boot 4 (Java 17) 백엔드 + React(Vite) 프론트. 설계는 `docs/design.md`, 배포 계획은 `docs/deploy-plan.md`.

## 로컬 개발

```powershell
docker compose up -d          # PostgreSQL 16 + Redis 7
.\gradlew bootRun             # 또는 IntelliJ 에서 실행 (ADMIN_EMAIL, ADMIN_PASSWORD 등은 실행 구성 환경 변수)
.\gradlew test                # Docker 필요 (Testcontainers)
cd frontend; npm run dev      # http://localhost:5173, /api 는 8080 으로 프록시
```

- 로컬은 `ddl-auto: update` + Flyway `baseline-on-migrate`(기존 개발 DB 는 V1 적용된 것으로 간주). 빈 DB 면 V1 이 적용된다.
- 업로드 이미지는 `./uploads` (`app.storage.type=local`).

## DB 마이그레이션 규칙 (Flyway)

스키마 원본은 `src/main/resources/db/migration/V*.sql` 이다. 운영은 `ddl-auto: validate` 라 마이그레이션이 없으면 기동하지 못한다.

- **엔티티(컬럼·제약·인덱스)를 바꾸면 `V2__*.sql`, `V3__*.sql` … 을 새로 작성한다.** 이미 적용된 V 파일은 수정하지 않는다.
- **enum 값을 추가·변경하면 해당 컬럼의 CHECK 제약(`*_check`)도 같은 마이그레이션에서 함께 바꾼다.** Hibernate 가 enum 컬럼마다 CHECK 를 만들어 두어, 값만 추가하면 INSERT 가 제약 위반으로 실패한다.
- `FlywayMigrationPostgresTest` 가 빈 PostgreSQL 에 마이그레이션만 적용한 뒤 Hibernate `validate` 로 엔티티와 대조하고, 모든 enum 값이 CHECK 를 통과하는지도 확인한다. 이 테스트가 깨지면 마이그레이션이 빠진 것이다.

## 배포 구성 (전부 무료 플랜)

```
브라우저 → Vercel (frontend/, /api/* 를 Render 로 rewrite)
              └→ Render Free Web Service (Dockerfile, Singapore)
                     ├→ Neon PostgreSQL 16   (DB_URL …)
                     ├→ Upstash Redis (TLS)  (REDIS_URL)
                     └→ Cloudflare R2        (업로드 이미지, R2_*)
```

브라우저는 Vercel 주소만 보므로 세션 쿠키·CORS 설정이 로컬과 같다. 환경 변수 이름은 `.env.example` (값은 Render 대시보드에만 입력).

### 무료 플랜 제약과 대응
| 제약 | 대응 |
|---|---|
| Render 15분 무요청 시 잠듦(깨우는 데 약 1분), 재시작 시 로컬 파일 삭제 | 수용 / 이미지는 R2 |
| Vercel 프록시 응답 최대 120초 | SSE 타임아웃 110초(`app.notification.sse-timeout-seconds`, 하트비트 25초, EventSource 자동 재연결), 챗봇 전체 100초 상한(`app.chat.total-budget-seconds`) |
| 512MB / 0.1 CPU | `JAVA_TOOL_OPTIONS`(Dockerfile 기본값), Hikari 풀 5, prod 에서 SQL 로그 끔 |
| Neon 5분 무활동 시 일시정지 | Hikari `minimum-idle: 0`, `max-lifetime: 4분`, keepalive 미사용 |
| Upstash 월 50만 명령 | 세션 만료 정리 10분 주기(`spring.session.data.redis.cleanup-cron`), 배포 1주 뒤 사용량 확인 |

### Upstash 에서 세션 keyspace 알림 설정이 막힐 때
Spring Session(indexed)은 기동 시 `CONFIG SET notify-keyspace-events Egx` 를 실행한다. Upstash 가 거부하면 `application-prod.yml` 의
`spring.session.data.redis.configure-action: none` 주석을 풀고, Upstash 콘솔에서 `notify-keyspace-events` 를 `Egx` 로 직접 설정한다.

### 배포 전 로컬에서 이미지 확인
```powershell
docker build -t boardgame-api .
# 새 빈 DB(boardgame_prod)와 로컬 Redis 에 붙여 512MB 로 기동 (값은 예시)
docker run --rm -p 8080:8080 --memory=512m `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/boardgame_prod -e DB_USERNAME=boardgame -e DB_PASSWORD=boardgame `
  -e REDIS_URL=redis://host.docker.internal:6379 `
  -e R2_ENDPOINT=http://localhost -e R2_BUCKET=x -e R2_ACCESS_KEY_ID=x -e R2_SECRET_ACCESS_KEY=x `
  -e ADMIN_EMAIL=admin@example.com -e ADMIN_PASSWORD=<강한-비밀번호> boardgame-api
# → 로그에 Flyway V1 적용, http://localhost:8080/api/health 200
# (secure 쿠키라 http://localhost 에서는 브라우저 로그인 유지가 안 될 수 있다. 로그인은 curl 로 응답 확인)
```
