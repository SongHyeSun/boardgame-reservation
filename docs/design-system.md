# 디자인 적용 가이드 (최종 · 2026-09-29)

레포 위치: `docs/design-system.md`. Claude Code에서 프론트 스타일을 바꿀 때 이 문서를 기준으로 한다.
**기능·화면 구성·라우트·컴포넌트 props·API 호출은 바꾸지 않는다. 보이는 모습(className, CSS, 마크업 감싸는 div 정도)만 바꾼다.**

- 참고 시안 HTML: `docs/design/mockups/` (29개 화면 + `screens.css` + `ds/boardclub/components/bundle.css`). 브라우저로 열어 볼 수 있고, **구조·간격·색은 이 파일이 정답**. 시안의 `tb-*`·커스텀 클래스는 그대로 옮기지 말고 Tailwind 유틸리티로 옮긴다(8장 매핑)
- 원본: claude.ai 아티팩트 "보드게임 동아리"(Design System), "보드게임 동아리 화면 시안"(Design 캔버스)
- 시안 속 게임 이름·사람·날짜는 예시 데이터. 실제 화면은 API 데이터 그대로

## 1. 방향 — 보드게임 테이블

- 초록 펠트(`felt`) 바탕 위에 흰 카드(`surface`), 노란 미플(`meeple`)은 작은 강조에만.
- 이 서비스만의 표시: **좌석 점**(파티 정원 ●●○○), **주사위 눈**(난이도 점 1·2·3개), 파티 상세의 **초록 좌석 판**(참여자 아바타 + 빈자리 점선 원).
- 글꼴: 제목·게임 이름·큰 숫자 = **Do Hyeon**, 나머지 = **IBM Plex Sans KR**.
- 기본 버튼은 아래 3px 두께 그림자(`shadow-token`), 누르면 2px 내려감 — 보드게임 토큰 느낌.
- 문구: 해요체, 버튼은 동사("참여하기"), 비활성 버튼은 이유를 글자로("정원이 찼어요").

## 2. Tailwind 4 토큰 — `frontend/src/index.css`

```css
@import "tailwindcss";

/* 다크 모드: <html data-theme="dark">. OS 설정을 따르려면 앱 시작 때 matchMedia 결과로 data-theme를 넣는다 */
@custom-variant dark (&:where([data-theme=dark], [data-theme=dark] *));

@theme {
  --color-table: #F2F4EE;
  --color-surface: #FFFFFF;
  --color-sunken: #E8ECE4;
  --color-line: #DCE1D6;
  --color-line-strong: #86938A;
  --color-ink: #17201B;
  --color-ink-muted: #55615A;
  --color-felt: #1D5E45;
  --color-felt-strong: #154A36;
  --color-felt-edge: #0E3526;
  --color-felt-soft: #E0EEE6;
  --color-on-felt: #FFFFFF;
  --color-meeple: #F2B531;
  --color-on-meeple: #2B2000;
  --color-meeple-soft: #FCEFC8;
  --color-meeple-ink: #7D5500;
  --color-danger: #B33A2B;
  --color-danger-soft: #FAE3DE;
  --color-info: #2656B0;
  --color-info-soft: #E2EBFA;
  --color-done: #62479F;
  --color-done-soft: #ECE6F8;
  --color-suspend: #2A322D;
  --color-on-suspend: #FFFFFF;
  --color-seat-red: #FBDAD5;
  --color-seat-blue: #D8E5FA;
  --color-seat-yellow: #FBEBC0;
  --color-seat-green: #D5EEDC;
  --color-seat-purple: #E6DDF7;
  --color-seat-orange: #FCE0CB;
  --color-scrim: #17201B73;

  --font-display: "Do Hyeon", "IBM Plex Sans KR", system-ui, sans-serif;
  --font-sans: "IBM Plex Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif;

  --text-display-lg: 32px;  --text-display-lg--line-height: 38px;
  --text-display: 26px;     --text-display--line-height: 32px;
  --text-numeral: 22px;     --text-numeral--line-height: 24px;
  --text-title-lg: 20px;    --text-title-lg--line-height: 28px;  --text-title-lg--font-weight: 700;
  --text-title: 17px;       --text-title--line-height: 24px;     --text-title--font-weight: 600;
  --text-body: 15px;        --text-body--line-height: 24px;
  --text-small: 13px;       --text-small--line-height: 20px;
  --text-caption: 12px;     --text-caption--line-height: 16px;   --text-caption--font-weight: 600;

  --spacing: 4px;

  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --radius-xl: 24px;

  --shadow-token: 0 3px 0 #0E3526;
  --shadow-card: 0 1px 2px rgba(23,32,27,0.06), 0 4px 12px -4px rgba(23,32,27,0.10);
  --shadow-lifted: 0 2px 6px rgba(23,32,27,0.08), 0 16px 32px -8px rgba(23,32,27,0.24);
}

@layer base {
  :root {
    --header-h: 56px; --action-bar-h: 76px; --toast-w: 360px;
    --z-header: 30; --z-action-bar: 40; --z-dropdown: 50; --z-modal: 70; --z-toast: 80;
  }
  [data-theme=dark] {
    color-scheme: dark;
    --color-table: #101612;
    --color-surface: #19211C;
    --color-sunken: #0B100D;
    --color-line: #2A332D;
    --color-line-strong: #66736A;
    --color-ink: #E6EBE5;
    --color-ink-muted: #A0ACA4;
    --color-felt: #5DBE93;
    --color-felt-strong: #7BD3AC;
    --color-felt-edge: #2F7F5E;
    --color-felt-soft: #16352A;
    --color-on-felt: #08170F;
    --color-meeple: #F2B531;
    --color-on-meeple: #2B2000;
    --color-meeple-soft: #3A2D0C;
    --color-meeple-ink: #F4C95B;
    --color-danger: #FF8C7B;
    --color-danger-soft: #3E1C17;
    --color-info: #8DB2FF;
    --color-info-soft: #172748;
    --color-done: #BDA7F2;
    --color-done-soft: #2A2242;
    --color-suspend: #E6EBE5;
    --color-on-suspend: #101612;
    --color-seat-red: #4A231E;
    --color-seat-blue: #1D2F52;
    --color-seat-yellow: #44360F;
    --color-seat-green: #1B3D28;
    --color-seat-purple: #33284F;
    --color-seat-orange: #4A2D17;
    --color-scrim: #000000A6;
    --shadow-token: 0 3px 0 #2F7F5E;
    --shadow-card: 0 1px 2px rgba(0,0,0,0.4), 0 4px 12px -4px rgba(0,0,0,0.5);
    --shadow-lifted: 0 2px 6px rgba(0,0,0,0.5), 0 16px 32px -8px rgba(0,0,0,0.7);
  }
  body { @apply bg-table text-ink font-sans text-body antialiased; }
}
```

글꼴: `index.html`에 Google Fonts `<link>`(Do Hyeon, IBM Plex Sans KR 400/500/600/700) 또는 PWA 오프라인을 위해 `@fontsource/do-hyeon`, `@fontsource/ibm-plex-sans-kr` 설치(설치 전 보고).
아이콘: `lucide-react`(설치 전 보고). 헤더 🔔 이모지 → `Bell` 아이콘.

## 3. 색 쓰는 규칙

| 용도 | 토큰 |
|---|---|
| 페이지 바탕 / 카드·입력·모달 / 필터·비활성 | `table` / `surface` / `sunken` |
| 본문 / 보조 글자 | `ink` / `ink-muted` |
| 장식 구분선 / 입력칸·보조 버튼 테두리 | `line` / `line-strong` |
| 기본 버튼·링크·선택·포커스 | `felt` (+ `on-felt` 글자) |
| 남은 자리 숫자 칩·달력 선택 범위·활성 탭 밑줄 | `meeple` (+ `on-meeple`). 넓은 면·텍스트 색 금지 |
| 이모지 아바타 바탕 | `seat-*` 6개 중 `memberId % 6` |

다크 테마에서 `felt`는 밝은 민트 → 채움 위 글자는 반드시 `on-felt`(흰색 하드코딩 금지). 대비는 두 테마 모두 4.5:1 이상 확인함.

## 4. 공용 컴포넌트 매핑 (기존 이름 그대로, 스타일만)

| 컴포넌트 | 모습 |
|---|---|
| DifficultyBadge | 쉬움 `felt-soft/felt` + 점 1개 · 보통 `meeple-soft/meeple-ink` + 점 2개 · 어려움 `danger-soft/danger` + 점 3개 |
| PartyStatusBadge | 모집 중 `felt-soft/felt` + 작은 점 · 마감 `sunken/ink-muted` · 취소 `danger-soft/danger` |
| GameStatusBadge | 「운영 중지」 `suspend/on-suspend` 솔리드 + 일시정지 아이콘 |
| PlayModeBadge | 온라인 `info` 테두리 + Wifi · 오프라인 `line-strong` 테두리 + 테이블 아이콘 |
| CustomGameBadge | 「기타 게임」 점선 테두리 + 연필 아이콘 |
| ReservationStatusBadge | 승인 대기 `meeple-soft` · 승인 `felt-soft`+체크 · 거절 `danger-soft`(사유는 아래 작은 글자) · 취소 `sunken` · 대여 완료 `done-soft` |
| 배지 공통 | 높이 24px, `rounded-sm`, `text-caption` |
| Button | 기본 44px / sm 36px / lg 52px, `rounded-md`. primary = `bg-felt text-on-felt shadow-token`, active 시 `translate-y-0.5`+그림자 1px |
| FormField 계열 | 라벨 위 13px 600, 입력 44px `rounded-md border-line-strong`, 포커스 `outline-2 outline-felt`, 에러는 아래 13px `text-danger` |
| 카드 | `bg-surface border border-line rounded-lg shadow-card`, 카드 전체가 링크, 안에 버튼 없음 |
| Avatar | sm 32 / md 40 / lg 56 / xl 80, 파티장은 오른쪽 아래 노란 점 |
| Modal | 640px 미만 바텀시트(위 모서리 `rounded-xl` + 손잡이), 이상은 가운데 |
| 진행 방식·당일/여러 날 | 라디오 대신 세그먼트 버튼 모양(`sunken` 트랙 + 선택 칸 `surface`) |

## 5. ⭐ 고정 요소 · 토스트 겹침 규칙

| 요소 | z |
|---|---|
| 헤더(sticky, 56px) | 30 |
| 하단 고정 바 — 파티 상세(참여하기/마감), 게임 상세(파티 만들기/예약하기), 예약(신청), 파티 개설(만들기), /chat(입력창) | 40 |
| 알림 드롭다운·모바일 메뉴 | 50 |
| 모달 | 70 |
| 토스트 | 80 |

- 토스트: `bottom: calc(16px + var(--page-action-bar, 0px) + env(safe-area-inset-bottom))`
- 하단 고정 바가 있는 페이지는 루트에 `style={{'--page-action-bar': 'var(--action-bar-h)'}}`(채팅은 입력창 실제 높이). 페이지를 떠나면 자연히 0으로 돌아가게 페이지 컴포넌트 루트에 둔다
- 하단 바가 있는 페이지 본문에는 `padding-bottom: calc(var(--action-bar-h) + 24px)`
- 토스트 폭: 모바일 좌우 16px 꽉 채움, `sm:`(640px) 이상 `w-[360px] right-6`
- 하단 바에는 `padding-bottom: env(safe-area-inset-bottom)` 추가(PWA 홈 인디케이터)
- **`lg`(1024px) 이상에서는 하단 고정 바를 쓰지 않는다**(6-2 참고) → `--page-action-bar`는 `lg:`에서 0. 예외: /chat 입력창은 큰 화면에서도 하단 고정이라 토스트는 계속 그 위

## 6. 화면별 요점

- **헤더(모바일)**: 로고 · 🔔(빨간 숫자 배지) · 아바타 · 햄버거 → 전체 화면 메뉴(프로필 카드, 메뉴, "관리" 그룹, 로그아웃). 데스크톱은 가로 메뉴, 활성 메뉴 밑줄 `meeple`
- **파티 목록**: 제목(display) + "파티 만들기" / 필터 칩 가로 스크롤 / 카드: 배지 → 제목 → 게임·일시·장소 → 점선 아래 좌석 점 + "남은 자리 N"
- **파티 상세**: 게임 요약 카드(링크) · 일시/장소/정원 아이콘 행 · 설명 · "참여자 2/4" + 초록 좌석 판 · 하단 바. 파티장에게 참여자 관리 목록(내보내기 danger 버튼)
- **게임 목록**: 2열(모바일) 카드, 이미지 없으면 `seat-*` 단색 위에 게임 이름을 display 글꼴로
- **게임 상세**: "← 보드게임" 링크 → 윗부분 `grid-cols-[30%_1fr] gap-3.5`: 왼쪽 이미지(3:4, `rounded-md`, `object-cover`, 없으면 `seat-*` 단색 + 게임 이름) / 오른쪽 게임 이름(display 30px) · 배지 · 인원/플레이/보유 3칸 미니 통계(숫자 display 19px, 라벨 11px) → 설명 · 등록 관리자 → 규칙 영상 16:9 → 하단 바[파티 만들기][예약하기]
- **회원가입**: 프로필(아바타 + 이모지/사진 세그먼트 + 이모지 16개 8열) → 이메일 · 비밀번호 · **이름 · 닉네임**(2열, 이름이 왼쪽) · 생년월일 · 소속 · 직업(2열) · 한줄소개 · 관리자 신청 박스(`sunken`) · 가입하기
- **예약**: 세그먼트(당일/여러 날) · 달력 칸 48px, 날짜 아래 남은 수량, 마감은 취소선+`sunken`, 선택 범위 `meeple`/`meeple-soft`, 오늘은 `felt` 테두리 · 하단 바에 기간 요약
- **챗봇**: 상단 "AI 추천" + "오늘 남은 추천 N회" 알약 · 내 말풍선 `felt` 오른쪽 · AI는 주사위 마크 + 흰 말풍선 · 추천 카드(72px 이미지, 인원·시간, 배지, `table` 바탕 추천 이유, [게임 보기][이 게임으로 파티 만들기]) · 출처는 점선 박스 안 `info` 링크 · 에러 말풍선 `danger-soft` + 다시 시도 · 로딩 "추천을 찾는 중…" + 입력 비활성
- **관리자(데스크톱)**: 예약 관리는 탭(승인 대기 N) + 표, 거절 시 행 안에서 사유 입력 / 관리자 승인은 신청자 카드 목록

## 6-2. 큰 화면(반응형) 규칙

| 구간 | Tailwind | 바뀌는 것 |
|---|---|---|
| ~639px | 기본 | 모바일 시안 그대로. 좌우 여백 16px, 헤더는 로고·알림·아바타·햄버거 |
| 640px~ | `sm:` | 모달이 바텀시트 → 가운데(폭 520px), 토스트 폭 360px·오른쪽 24px |
| 768px~ | `md:` | 게임 목록 3열, 파티 목록 2열 |
| 1024px~ | `lg:` | 헤더 가로 메뉴(햄버거 숨김, `닉네임님`·로그아웃 노출), 게임 4열, 파티 3열, **하단 고정 바 → 오른쪽 사이드 카드** |

- 본문 최대 폭 `max-w-[1080px] mx-auto`, 위아래 여백 32~40px. 폼(파티 개설)은 720px, 내 예약·내 정보는 960px, 챗봇 대화는 760px 가운데
- 페이지 제목은 큰 화면에서 32px(`text-display-lg`)
- **파티 상세**: `lg:grid-cols-[1fr_360px]`. 왼쪽 = 제목·게임 요약 카드와 일시/장소(2열)·설명·좌석 판(아바타 64px). 오른쪽 = sticky 사이드 카드(`top-20`): "남은 자리" 큰 숫자(display 48px) + 진행 막대 + 일시·장소 + 참여하기(lg 버튼, 꽉 채움)
- **게임 상세**: 윗부분 `lg:grid-cols-[240px_1fr] lg:gap-10`: 왼쪽 이미지 240px(4:5, `rounded-lg`) / 오른쪽 게임 이름 40px · 배지 · 통계 3칸(최대 420px) · 설명 · 등록 관리자 · [예약하기][이 게임으로 파티 만들기]. 그 아래 "규칙 영상" 섹션, 영상 최대 폭 640px
- **예약**: 왼쪽 달력(칸 68px), 오른쪽 사이드 카드(게임 요약, 선택한 기간 크게, 안내 2줄, 예약 신청)
- **파티 개설**: 흰 카드 폼 한 장, 날짜·시간 / 진행 방식·정원을 2열로, 버튼은 폼 맨 아래 오른쪽 [취소][파티 만들기] (하단 바 없음)
- **내 예약**: 한 줄 = 썸네일 72 · 게임·기간 · 상태 배지(+사유) · 취소 버튼. 상태 필터는 칩 대신 탭
- **내 정보**: 왼쪽 300px 프로필 카드(아바타 80, 닉네임, 한줄소개, 역할 배지, 프로필 수정, 내 예약 링크) / 오른쪽 기본 정보·관리자 권한·비밀번호(2열)
- **로그인·회원가입**: 반반 분할, 왼쪽 펠트 면 + 주사위 그림 + "보드게임 동아리", 오른쪽 폼
- **챗봇**: 대화 폭 760px 가운데, 추천 카드 2열, 입력창은 화면 폭 바 안에 760px 가운데 정렬

## 7. Claude Code 작업 순서 제안

1. 토큰(`index.css`) + 글꼴 + 아이콘 설치(보고 후) → 빌드 확인
2. 공용 컴포넌트(배지 6종, Button, FormField, Modal, Avatar, Toast, NotificationBell)
3. Layout/헤더 + 하단 고정 바 + 토스트 위치(`--page-action-bar`) — 모바일과 `lg:` 사이드 카드 전환을 같이
4. 화면별(파티 → 게임 → 예약 → 내 정보 → 관리자 → 챗봇) — 한 번에 한 묶음, 매번 lint/build + 브라우저 확인
5. 다크 모드 토글은 선택(토큰만 준비됨)

## 8. 시안 파일 ↔ 화면 매핑 (`docs/design/mockups/`)

| 화면 | 모바일(390) | 큰 화면(1280) |
|---|---|---|
| 파티 목록 | `Main.html` | `PartyListDesktop.html` |
| 파티 상세 (참여 전 / 파티장·다크) | `PartyDetail.html` / `PartyDetailHost.html` | `PartyDetailDesktop.html` |
| 파티 개설 `/parties/new` + 게임 선택 모달 | `PartyNew.html` · `GameSelectModal.html` | `PartyNewDesktop.html` · `GameSelectModalDesktop.html` |
| 게임 목록 / 상세 `/boardgames/:id` | `GameList.html` / `GameDetail.html` | `GameListDesktop.html` / `GameDetailDesktop.html` |
| 대여 예약 `/boardgames/:id/reserve` | `Reserve.html` | `ReserveDesktop.html` |
| 내 예약 `/me/reservations` · 내 정보 `/me` | `MyReservations.html` · `Me.html` | `MyReservationsDesktop.html` · `MeDesktop.html` |
| 로그인 · 회원가입 | `Login.html` · `Signup.html` | `LoginDesktop.html` (회원가입도 같은 반반 분할) |
| 헤더 · 알림 드롭다운 · 모바일 메뉴 | `MobileMenu.html` | `HeaderDesktop.html` |
| 관리자: 예약 관리 · 관리자 승인 | — | `AdminReservations.html` · `AdminRequests.html` |
| AI 추천 `/chat` | `ChatEmpty.html` · `Chat.html` | `ChatDesktop.html` (첫 화면도 같은 760px 가운데) |

옮길 때 규칙
- 시안의 CSS 변수(`var(--felt)` 등)는 2장 토큰 클래스(`bg-felt`, `text-ink-muted`)로 바꾼다. 픽셀 값은 가장 가까운 Tailwind 값 또는 `[]` 임의값
- 시안의 모바일·큰 화면 두 파일을 하나의 반응형 컴포넌트로 합친다(모바일 기본 + `sm:`/`md:`/`lg:`)
- 여러 화면에서 반복되는 모양(배지, 카드, 좌석 점, 하단 바, 사이드 카드, 토스트)은 공용 컴포넌트로. 시안 HTML을 통째로 복사하지 않는다
- 아이콘은 시안의 인라인 SVG 대신 lucide-react 같은 이름의 아이콘

## 9. taste skill 등 디자인 스킬

- `Leonxlnx/taste-skill`(`npx skills add https://github.com/Leonxlnx/taste-skill`): AI가 흔한 모양(보라 그라데이션, Inter, 똑같은 카드)으로 가지 않게 막는 규칙 모음. 방향이 없을 때 효과가 크다
- 이 프로젝트는 방향·토큰이 이미 정해져 있어서, 스킬의 기본 취향(애니메이션·"프리미엄" 여백 등)이 이 문서와 부딪칠 수 있음 → 쓰려면 CLAUDE.md에 "디자인 결정은 docs/design-system.md가 스킬보다 우선"을 명시
- 설치하기 전에 SKILL.md를 먼저 읽어볼 것(서드파티 문서)
- 적용 후 다듬기 단계에서 `redesign-skill`(기존 화면 점검용)로 한 번 훑는 용도가 가장 잘 맞음
