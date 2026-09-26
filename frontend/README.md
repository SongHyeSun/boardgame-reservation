# 보드게임 예약·파티 모집 — 프론트엔드

React + TypeScript 화면. 백엔드(Spring Boot, `../`)의 REST API(`/api`)를 호출한다.

| 구분 | 사용 기술 (설치 버전) |
|---|---|
| UI | React 19.3.0, react-router 8.4.0, Tailwind CSS 4.3.3 |
| 서버 상태 / HTTP | @tanstack/react-query 5.103.2, axios 1.20.0 |
| 빌드 / 언어 / 린트 | Vite 8.3.1, TypeScript 6.0.3 (strict), oxlint 1.85.0 |

화면 구성과 설계는 [`docs/frontend-plan.md`](../docs/frontend-plan.md), 백엔드 API는 [`docs/design.md`](../docs/design.md) 참고.

## 사전 조건

- Node.js `^20.19.0` 또는 `>=22.12.0` (Vite 8 요구 사항)
- JDK 17 (백엔드)
- Docker Desktop (PostgreSQL, Redis)

## 실행 방법

프로젝트 루트(`boardgame-reservation/`) 기준, PowerShell.

**1. DB / Redis 띄우기**

```powershell
docker compose up -d
```

PostgreSQL `5432`, Redis `6379`가 열린다.

**2. 백엔드 실행** (`http://localhost:8080`)

IntelliJ에서 실행하거나 터미널에서:

```powershell
.\gradlew bootRun
```

관리자(ADMIN) 화면(게임 등록·수정·삭제)을 확인하려면 **두 환경변수를 모두** 설정하고 기동한다. 서버 시작 시 해당 계정이 없으면 1회 생성된다 (닉네임 `관리자`). 하나라도 비어 있으면 생성하지 않는다.

```powershell
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = '<원하는 비밀번호>'
.\gradlew bootRun
```

**3. 프론트 실행** (`http://localhost:5173`)

```powershell
cd frontend
npm install     # 최초 1회
npm run dev
```

백엔드가 꺼져 있으면 화면에 "서버에 연결할 수 없습니다"가 표시된다.

## 프록시 구조

```
브라우저 ──▶ localhost:5173 (Vite dev 서버)
                 ├─ 화면(HTML/JS)  ← 직접 응답
                 └─ /api/**  ──프록시──▶ localhost:8080 (Spring Boot)
```

- 프론트는 항상 상대 경로(`/api/...`)로 호출한다 (`src/api/client.ts`의 `baseURL: '/api'`). 프록시는 `vite.config.ts`의 `server.proxy`.
- 브라우저 입장에서는 모든 요청이 `localhost:5173` 한 곳이라 **같은 출처**다. 그래서 CORS 설정이 필요 없고, `SameSite=Lax` 세션 쿠키도 그대로 전송된다.
- 세션 쿠키 `JSESSIONID`는 `HttpOnly`라 JS에서 읽을 수 없다. 로그인 상태는 쿠키가 아니라 `GET /api/members/me`의 응답(200 = 로그인, 401 = 비로그인)으로 판단한다.
- 배포 시에도 같은 구조를 유지할 예정이다 (nginx가 정적 파일을 서빙하고 `/api`만 Spring으로 리버스 프록시).

## 스크립트

`frontend/`에서 실행.

| 명령 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 (`5173`, HMR) |
| `npm run build` | 타입 검사 + 프로덕션 빌드 (`tsc -b && vite build`, 결과는 `dist/`) |
| `npm run lint` | oxlint |
| `npm run preview` | 빌드 결과 미리보기 |

프론트 테스트는 이번 범위에서 제외했다. 변경 후 검증은 `npm run lint` + `npm run build`.

## 문제 해결

### 백엔드 기동 시 `password authentication failed for user "boardgame"`

기존 `pgdata` 볼륨에 예전 비밀번호가 남아 있는 경우다. `docker-compose.yml`의 `POSTGRES_PASSWORD`는 볼륨을 **처음 만들 때만** 적용되므로, 이후에 바꾸거나 다른 값으로 만든 볼륨은 새 비밀번호를 모른다.

비밀번호만 재설정한다 (데이터 유지):

```powershell
docker exec -it boardgame-postgres psql -U boardgame -d boardgame -c "ALTER USER boardgame PASSWORD 'boardgame';"
```

데이터가 필요 없다면 볼륨째 지우고 다시 만들어도 된다. **DB 데이터가 모두 초기화된다.**

```powershell
docker compose down -v
docker compose up -d
```

### 5432 포트를 WSL이 `[::1]`로 같이 점유하는 경우

`netstat -ano | findstr :5432`에서 Docker 외에 WSL(`wslrelay`)이 `[::1]:5432`(IPv6)를 잡고 있으면, 백엔드의 `localhost`가 IPv6로 해석되어 엉뚱한 곳에 붙을 수 있다. 실행 환경변수 `DB_URL`로 IPv4(`127.0.0.1`)를 강제한다. (`application.yml`의 기본값 `jdbc:postgresql://localhost:5432/boardgame`을 덮어쓴다.)

- IntelliJ: 실행 구성 → 환경 변수에 `DB_URL=jdbc:postgresql://127.0.0.1:5432/boardgame`
- 터미널:

  ```powershell
  $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/boardgame'
  .\gradlew bootRun
  ```
