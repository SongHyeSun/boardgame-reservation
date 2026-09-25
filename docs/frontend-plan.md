# React 프론트 구현 계획 (feature/frontend)

기준: docs/design.md 3장(API), docs/party-plan.md, 진행현황_핸드오프 4장(프론트가 알아야 할 백엔드 사실).
**백엔드 코드는 수정하지 않는다.** (필요해 보이면 구현 전에 보고만)

---

## 0. 기술 선택 (확정안)

| 구분 | 선택 | 이유 |
|---|---|---|
| 위치 | 같은 레포 `frontend/` (모노레포) | 백엔드 DTO를 Claude Code가 바로 읽고 타입을 맞출 수 있음 |
| 빌드 | Vite + React + **TypeScript** | 명세 스택(Vite). TS는 백엔드 DTO와 타입 일치 → 실수 감소, 포트폴리오 가점 |
| 패키지 매니저 | npm | 추가 설치 불필요 |
| 라우팅 | react-router (최신) | 표준 |
| 서버 상태 | TanStack Query | 로딩/에러/캐시/재조회 자동. 참여 후 목록 갱신(invalidate)이 간단 |
| HTTP | axios (`withCredentials: true`) | 인터셉터로 공통 응답·에러 처리 |
| 스타일 | Tailwind CSS | 별도 UI 라이브러리 없이 빠르게. 디자인보다 기능 완성 우선 |
| 폼 | 기본 useState (라이브러리 X) | 폼이 단순함 |

- 버전은 설치 시점 최신을 사용하고, 실제 설치된 버전을 CLAUDE.md에 기록
- TS 설정은 템플릿 기본(strict) 유지, `any` 사용 금지

---

## 1. 백엔드 연동 규칙

### 1-1. 개발 서버 프록시 (CORS 회피)
`vite.config.ts`
```ts
server: {
  port: 5173,
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
}
```
- 브라우저 입장에선 모든 요청이 `localhost:5173` → 같은 출처라 CORS·SameSite 문제 없음
- 세션 쿠키 `JSESSIONID`(HttpOnly)는 JS로 못 읽음 → **로그인 상태는 `GET /api/members/me`(200/401)로 판단**
- 배포 시에도 같은 구조 유지 예정 (7단계: nginx가 정적 파일 서빙 + `/api` → Spring 리버스 프록시)

### 1-2. API 클라이언트 (`src/api/client.ts`)
- axios 인스턴스: `baseURL: '/api'`, `withCredentials: true`
- 응답 인터셉터
  - 성공: `{ success, data, message }`에서 `data`만 꺼내 반환
  - 실패: 서버 `message`(한국어)를 담은 `ApiError { status, message }`로 변환해 throw
  - 네트워크 오류: "서버에 연결할 수 없습니다"
- 401 처리: 인터셉터에서 강제 이동하지 않음. 보호 라우트/화면에서 처리 (me 조회 401은 정상 흐름이므로)

### 1-3. 타입 (`src/types/`)
- **백엔드 dto 패키지와 http/*.http를 읽고 그대로 옮긴다.** 추측으로 필드 만들지 말 것
- enum: `Role = 'USER' | 'ADMIN'`, `Difficulty = 'EASY' | 'NORMAL' | 'HARD'`, `PartyStatus = 'RECRUITING' | 'CLOSED' | 'CANCELLED'`
- 날짜(`playAt`, `joinedAt` 등)는 string으로 받고 표시할 때만 포맷
- `playAt` 전송 형식: `<input type="datetime-local">` 값(`YYYY-MM-DDTHH:mm`)이 백엔드 LocalDateTime으로 파싱되는지 http 파일/DTO로 확인 후 맞춤

### 1-4. API 모듈 (도메인별 분리 — 백엔드 패키지 구조와 대응)
```
src/api/auth.ts       signup, login, logout, getMe
src/api/boardgames.ts getBoardGames(params), getBoardGame(id), create, update, remove
src/api/parties.ts    getParties(params), getParty(id), createParty, joinParty, leaveParty, closeParty
```
- 각 도메인에 TanStack Query 훅: `useMe`, `useBoardGames(filter)`, `useParty(id)`, `useJoinParty()` …
- 쿼리 키 규칙: `['me']`, `['boardgames', filter]`, `['boardgame', id]`, `['parties', filter]`, `['party', id]`
- mutation 성공 시 invalidate
  - login → invalidate 대신 응답(`MemberResponse`, /me와 동일 형태)을 `setQueryData(['me'], member)`로 바로 반영. invalidate만 하면 재조회 전 캐시가 `null`이라 이동한 보호 페이지에서 `/login?redirect=`로 되돌아가는 경합이 생김
  - logout → 성공 시 `/parties`로 이동(replace) 후 `setQueryData(['me'], null)` + `me` 외 캐시 `removeQueries`. `queryClient.clear()`는 마운트된 `useMe` 옵저버(Header)를 갱신하지 않아 사용하지 않음. 로그아웃 요청이 401(세션 만료)이면 성공으로 간주해 동일하게 처리(`api/auth.ts`의 `logout()`이 401을 흡수)
  - join/leave/close → `['party', id]`, `['parties']`
  - 게임 등록/수정/삭제 → `['boardgames']`, `['boardgame', id]`

---

## 2. 폴더 구조

```
frontend/src
├── api/          client.ts, auth.ts, boardgames.ts, parties.ts
├── hooks/        useMe.ts, useBoardGames.ts, useParties.ts ...
├── types/        auth.ts, boardgame.ts, party.ts, api.ts(ApiResponse, ApiError)
├── components/   Layout, Header, ProtectedRoute, AdminRoute, ErrorMessage, Loading, 배지 등
├── pages/
│   ├── auth/       LoginPage, SignupPage
│   ├── boardgames/ BoardGameListPage, BoardGameDetailPage, BoardGameFormPage(관리자)
│   └── parties/    PartyListPage, PartyDetailPage, PartyCreatePage
├── utils/        format.ts (날짜·난이도 한글 표시)
├── App.tsx       라우터 정의
└── main.tsx      QueryClientProvider, RouterProvider
```

---

## 3. 화면 · 라우트

| 경로 | 화면 | 권한 | 주요 내용 |
|---|---|---|---|
| `/` | → `/parties` 리다이렉트 | 공개 | |
| `/login` | 로그인 | 비로그인 | 성공 시 이전 페이지(또는 `/parties`)로 |
| `/signup` | 회원가입 | 비로그인 | 성공(201) 시 `/login`으로 + 안내 |
| `/boardgames` | 게임 목록 | 공개 | 필터: 인원(players), 난이도, 키워드. 카드: 이름·인원·플레이타임·난이도 |
| `/boardgames/:id` | 게임 상세 | 공개 | 게임 정보 + 이 게임의 모집 중 파티(`/api/parties?boardGameId=`) + "이 게임으로 파티 만들기" |
| `/boardgames/new`, `/boardgames/:id/edit` | 게임 등록/수정 | ADMIN | 상세 화면에 관리자만 수정/삭제 버튼 |
| `/parties` | 파티 목록 | 공개 | 상태 필터(기본 RECRUITING). 항목: 제목·게임명·호스트·`currentCount/capacity`·상태 |
| `/parties/new` | 파티 개설 | 로그인 | 게임 선택(쿼리 `?boardGameId=` 있으면 미리 선택), 제목, 설명, 정원, 플레이 일시 |
| `/parties/:id` | 파티 상세 | 공개(행동은 로그인) | 참여자 목록, 남은 자리, 버튼(아래 규칙) |
| `*` | 404 | | |

### 헤더
- 비로그인: 게임 · 파티 · 로그인 · 회원가입
- 로그인: 게임 · 파티 · 파티 만들기 · `닉네임님` · 로그아웃
- ADMIN이면 "게임 등록" 추가

### 보호 라우트
- `ProtectedRoute`: `useMe` 로딩 중 → Loading, 401 → `/login?redirect=현재경로`
- `AdminRoute`: role !== ADMIN → 게임 목록으로 + 안내 (진짜 방어는 백엔드 403, 프론트는 UX용)

---

## 4. 파티 상세 버튼 규칙 (핵심 화면)

| 상태 | 표시 |
|---|---|
| 비로그인 | "로그인하고 참여하기" → `/login?redirect=` |
| 로그인 + 호스트 + RECRUITING | "모집 마감" (confirm 후 PATCH close). 호스트는 탈퇴 버튼 없음 |
| 로그인 + 참여자(비호스트) + RECRUITING | "참여 취소" |
| 로그인 + 미참여 + RECRUITING + 남은 자리 > 0 | "참여하기" |
| 로그인 + 미참여 + RECRUITING + 남은 자리 0 | "정원 마감" (비활성) |
| CLOSED / CANCELLED | 상태 배지만, 버튼 없음 |

- 참여 여부·호스트 여부는 상세 응답의 참여자 목록/호스트 정보와 `me.id`(필드명은 DTO 기준)로 판단
- **선착순 UX**
  - 요청 중 버튼 비활성 + "참여 중…" (연타 방지)
  - 409 응답이면 서버 message("정원이 마감되었습니다" / "이미 참여한 파티입니다")를 그대로 표시하고 상세 재조회
  - 성공 시 응답 `remaining`으로 즉시 표시 + 상세 invalidate
- 화면의 남은 자리는 조회 시점 값일 뿐, 최종 판정은 서버(Redis DECR). 프론트에서 정원 체크로 요청을 막지 않는다 (0일 때 버튼 비활성은 UX 목적)

## 5. 폼 검증 (클라이언트 1차, 서버가 최종)
- 회원가입: 이메일 형식, 비밀번호·닉네임 필수 (길이 규칙은 백엔드 DTO의 @Size 등과 동일하게)
- 파티 개설: 게임 선택 시 **정원 입력 범위를 게임의 minPlayers~maxPlayers로 제한** + 안내 문구 "호스트 포함 인원"
- 게임 등록: minPlayers ≤ maxPlayers, playTime > 0
- 서버 400 메시지는 폼 상단에 표시

---

## 6. 구현 단계 (Claude Code 세션 단위, 단계마다 /clear)

| 단계 | 내용 | 완료 확인 |
|---|---|---|
| F1 | 기반: 의존성, Tailwind, 프록시, client·타입·api 모듈, QueryClient, 라우터 뼈대, Layout/Header, useMe, 보호 라우트 | `npm run build` 통과, 헤더에 로그인 상태 반영 |
| F2 | 인증: 로그인·회원가입·로그아웃, redirect 처리 | 가입→로그인→헤더 닉네임→로그아웃, 새로고침해도 로그인 유지 |
| F3 | 보드게임: 목록(필터)·상세·관리자 등록/수정/삭제 | USER로는 등록 화면 접근 불가, ADMIN으로 CRUD |
| F4 | 파티: 목록·상세·개설·참여/취소/마감 | 브라우저 2개(일반+시크릿)로 두 계정 참여/마감 시나리오 |
| F5 | 마무리: 로딩/에러/빈 목록 처리 통일, 404, 날짜·난이도 표시, `frontend/README.md` 실행법 | `npm run lint`, `npm run build` 통과 |

- 프론트 테스트(Vitest)는 이번 스코프 제외 (백엔드 테스트 69개가 핵심). 시간 남으면 파티 버튼 규칙만 테스트
- 각 단계 끝: `npm run lint` + `npm run build` 통과해야 완료

## 7. CLAUDE.md에 추가할 프론트 규칙 (F1에서 반영)
- 프론트는 `frontend/`, 명령은 `frontend/`에서 실행 (`npm run dev|build|lint`)
- 백엔드 코드 수정 금지 (필요하면 보고)
- API 필드는 백엔드 dto / http/*.http 기준, 추측 금지
- 서버 상태는 TanStack Query, 직접 useEffect+fetch 금지
- dev 서버는 사용자가 직접 실행 (Claude Code가 띄우지 않음)
