# B. 보드게임 미디어 · 온라인/오프라인 · 숨기기 · 파티 멤버 내보내기 구현 계획 (feature/boardgame-media)

레포 위치: `docs/boardgame-plan.md`. 공통 규칙은 `docs/extension-overview.md` 3장. A 단계(FileStorage, SUPER_ADMIN) 완료 전제.

---

## 1. BoardGame 필드 추가

| 필드 | 타입 | 규칙 |
|---|---|---|
| imageKey | varchar, nullable | FileStorage 키 (dir `boardgames`) |
| youtubeVideoId | varchar(11), nullable | 링크에서 추출한 영상 ID만 저장 |
| offlineAvailable | boolean | 기본 true (기존 게임 true) |
| onlineAvailable | boolean | 기본 false (기존 게임 false) |
| stock | int | 오프라인 가능 게임은 1 이상. 온라인 전용 게임은 0으로 저장 |
| visible | boolean | 기본 true |
| createdBy | @ManyToOne(LAZY) Member | 등록 관리자. 기존 게임은 부트스트랩 시 SUPER_ADMIN으로 채움 |

- `offlineAvailable`, `onlineAvailable` 중 **최소 하나는 true** → 아니면 INVALID_PLAY_MODE(400 "온라인·오프라인 중 하나 이상 선택해야 합니다")

## 2. 유튜브 링크 파서 (`YoutubeUrlParser`, 순수 클래스)

- 허용 형식 → videoId(`[A-Za-z0-9_-]{11}`) 추출
  - `https://www.youtube.com/watch?v=ID` (다른 쿼리 파라미터가 섞여도 OK)
  - `https://m.youtube.com/watch?v=ID`
  - `https://youtu.be/ID` (`?si=...`, `?t=...` 무시)
  - `https://www.youtube.com/shorts/ID`
  - `https://www.youtube.com/embed/ID`
- 빈 값/null → null (영상 제거). 그 외 → INVALID_YOUTUBE_URL(400 "올바른 유튜브 링크가 아닙니다")
- 프론트도 동일 규칙의 `utils/youtube.ts` (입력 즉시 미리보기용). 최종 판정은 서버
- 재생: `https://www.youtube-nocookie.com/embed/{videoId}` iframe (16:9)

## 3. 보드게임 API 변경

| Method | Path | 인증 | 변경 |
|---|---|---|---|
| GET | `/api/boardgames` | ❌ | 기본 `visible=true`만. `mine=true`(ADMIN) → 내가 등록한 게임, 숨김 포함. `playMode=ONLINE|OFFLINE` 필터 추가 |
| GET | `/api/boardgames/{id}` | ❌ | **숨김 게임도 반환**(`visible:false`) — 파티·예약 이력에서 링크되므로 |
| POST | `/api/boardgames` | ADMIN | multipart. `data {name, minPlayers, maxPlayers, playTime, difficulty, description, offlineAvailable, onlineAvailable, stock, youtubeUrl?}` + `image?`. createdBy = 로그인 관리자 |
| PUT | `/api/boardgames/{id}` | 소유자 | multipart. data에 `removeImage` 추가. 소유자 아니면 NOT_GAME_OWNER(403) |
| PATCH | `/api/boardgames/{id}/visibility` | 소유자 | `{ visible }` (4장) |
| ~~DELETE~~ | ~~`/api/boardgames/{id}`~~ | | **제거**. `BOARDGAME_IN_USE`와 관련 테스트도 제거 |

- 응답에 `imageUrl`, `youtubeVideoId`, `offlineAvailable`, `onlineAvailable`, `stock`, `visible`, `owner {id, nickname}` 추가
- stock 수정은 이 단계에선 규칙(오프라인 가능 → 1 이상)만 검증 (C단계에서 "예약 점유량보다 작게 못 줄임" 추가)
- **진행 방식 끄기 제한**: 수정으로 `onlineAvailable` 또는 `offlineAvailable`을 false로 바꿀 때, 그 방식의 RECRUITING 파티가 있으면 PLAY_MODE_IN_USE(409 "해당 방식으로 모집 중인 파티가 있어 변경할 수 없습니다"). 자동 취소하지 않음

## 4. 숨기기 (운영 중지)

`PATCH /api/boardgames/{id}/visibility { visible:false }` — 한 트랜잭션에서:
1. `visible=false`
2. 이 게임의 **RECRUITING 파티 → CANCELLED**
   - CLOSED(이미 마감된) 파티는 이력으로 유지 (화면에 「운영 중지」 배지)
3. 커밋 후 취소된 파티들의 Redis 키 삭제 (`party:{id}:remaining`, `party:{id}:members`)
4. `BoardGameSuspendedEvent(gameId, cancelledPartyIds)` 발행 (D단계에서 참여자 알림)
- C단계에서 이 로직에 "진행 중 예약 취소"가 추가됨
- 다시 보이기(`visible:true`): 게임만 다시 노출. 취소된 파티는 복구하지 않음
- 이미 같은 상태면 변경 없이 200

숨김 게임에 대한 차단:
- 파티 개설 → BOARDGAME_NOT_AVAILABLE(409 "운영이 중지된 게임입니다")
- 참여는 파티가 CANCELLED가 되므로 기존 PARTY_NOT_RECRUITING으로 자동 차단
- 파티 목록/상세 응답에 `boardGameVisible` 추가 → 프론트 배지 (기타 게임 파티는 항상 true)
- 파티 목록 기본 필터(RECRUITING)에는 자연히 안 나옴

## 5. 파티: 게임 종류 · 진행 방식

### 5-1. 게임 종류 — 등록된 보드게임 / 기타 게임(직접 입력)
보드게임이 아닌 게임(예: 구스구스덕, 리그 오브 레전드)도 관리자 등록 없이 파티를 모집할 수 있게 한다.

Party 필드 변경·추가

| 필드 | 타입 | 규칙 |
|---|---|---|
| boardGame | @ManyToOne(LAZY), **nullable로 변경** | 등록된 보드게임 파티일 때 |
| customGameName | varchar(50), nullable | 기타 게임 파티일 때 게임 이름(자유 입력, 앞뒤 공백 제거, 1~50자) |

- **둘 중 정확히 하나만** 있어야 함 → 아니면 INVALID_GAME_SELECTION(400 "보드게임을 선택하거나 게임 이름을 입력해주세요")
- 기타 게임 파티 규칙
  - 정원: `PartyPolicy`의 **2~20명**(호스트 포함). 보드게임 파티는 기존대로 게임의 minPlayers~maxPlayers
  - 진행 방식: 온라인/오프라인 자유 선택 (게임 지원 여부 검사 없음)
  - 숨기기·방식 끄기 제한과 무관, `boardGameVisible`은 항상 true
- ⚠️ **boardGame이 nullable이 되므로 파티 조회의 `join fetch boardGame`은 `left join fetch`로 바꿔야 함** (inner join이면 기타 게임 파티가 목록에서 사라짐). 목록·상세·호스트 기준 조회 전부 확인
- 응답: `boardGameId`(nullable), `gameName`(보드게임 이름 또는 customGameName), `customGame`(boolean). 기존 `boardGameName` 필드를 쓰는 프론트 코드는 `gameName`으로 정리
- 목록 필터 `boardGameId`는 그대로(기타 게임 파티는 자연히 제외). 기타 게임 이름 검색은 이번 스코프 제외
- 기존 파티는 전부 boardGame이 있으므로 데이터 이관 불필요

### 5-2. 진행 방식 (온라인 / 오프라인)

| 필드 | 타입 | 규칙 |
|---|---|---|
| playMode | varchar | `ONLINE` / `OFFLINE`, **필수**. 기존 파티는 `OFFLINE` |
| onlinePlatform | varchar(30), nullable | ONLINE일 때만 (예: 보드게임아레나, 디스코드) |
| onlineLink | varchar(300), nullable | ONLINE일 때만. `http://` / `https://`만 허용 → 아니면 INVALID_ONLINE_LINK(400) |
| location | varchar(100), nullable | OFFLINE일 때만, 자유 텍스트 (예: 동아리방, OO역 보드게임카페) |

- 개설 요청: `{ boardGameId?, customGameName?, title, description, capacity, playAt, playMode, onlinePlatform?, onlineLink?, location? }`
  - 보드게임 파티: 게임이 그 방식을 지원하지 않으면 PLAY_MODE_NOT_SUPPORTED(400 "이 게임은 해당 방식으로 진행할 수 없습니다")
  - 방식과 맞지 않는 필드(ONLINE인데 location 등)는 무시하고 null 저장
- 목록 응답: `playMode` 추가. 목록 필터 `playMode` 추가
- 상세 응답: `playMode`, `onlinePlatform`, `location` 공개. **`onlineLink`는 호스트·참여자(JOINED)에게만** 값, 그 외(비로그인 포함)는 null
  - 상세 GET은 permitAll 유지, 로그인 사용자면 세션에서 memberId를 읽어 판단 (비로그인도 에러 없이 동작해야 함)

## 6. 파티 멤버 내보내기

`DELETE /api/parties/{partyId}/members/{memberId}` — 호스트만
- 호스트 아님 → NOT_PARTY_HOST(403), RECRUITING 아님 → PARTY_NOT_RECRUITING(409)
- 호스트 자신 → CANNOT_KICK_HOST(400), 참여 중이 아님 → NOT_JOINED(400)

설계: **PartyMember에 `status` (`JOINED` / `KICKED`) 추가**
- 내보내기 = 행을 지우지 않고 `KICKED`로 변경 → `(party_id, member_id)` UNIQUE 제약이 그대로 **재참여를 DB에서도 차단**
- Redis: DB 커밋 후 `INCR remaining` + `SREM members`
- join: SADD 전에 DB에서 KICKED 여부 확인 → KICKED_FROM_PARTY(403 "파티장이 내보낸 파티에는 다시 참여할 수 없습니다")
- **인원 COUNT, 참여자 목록, 키 복구(remaining·members 재구성), 상세 remaining, 목록 currentCount, onlineLink 노출 판단 → 모두 `JOINED`만** 기준
- leave(자진 탈퇴)는 기존대로 행 삭제 → 재참여 가능
- `PartyMemberKickedEvent(partyId, memberId)` 발행
- ⚠️ PartyConcurrencyTest 결과 수치 그대로 유지

## 7. ErrorCode
NOT_GAME_OWNER(403 "본인이 등록한 게임만 관리할 수 있습니다"), INVALID_YOUTUBE_URL(400), BOARDGAME_NOT_AVAILABLE(409),
INVALID_PLAY_MODE(400), PLAY_MODE_NOT_SUPPORTED(400), PLAY_MODE_IN_USE(409), INVALID_ONLINE_LINK(400 "http:// 또는 https:// 링크만 입력할 수 있습니다"),
INVALID_GAME_SELECTION(400), CANNOT_KICK_HOST(400), KICKED_FROM_PARTY(403). BOARDGAME_IN_USE 제거.

## 8. 프론트

### 8-1. 보드게임
- 게임 폼(등록/수정): 이미지(미리보기·제거), 유튜브 URL(입력 즉시 미리보기, 잘못된 형식 안내), **"오프라인 가능" / "온라인 가능" 체크박스(최소 1개)**, 재고(오프라인 가능일 때만 표시, 온라인 전용이면 숨기고 0 전송). 삭제 버튼 제거
- 게임 목록: 카드 썸네일(없으면 기본 이미지) + 온·오프라인 표시. 필터에 진행 방식 추가. ADMIN에게 "내 게임" 토글(`mine=true`) + 숨김 게임 「운영 중지」 배지
- 게임 상세: 이미지, 유튜브 플레이어, 온·오프라인, 재고(오프라인 가능일 때), 등록 관리자
  - 소유 관리자에게만 "수정", "숨기기/다시 보이기"
  - 숨기기 confirm: "모집 중인 파티가 모두 취소되며 되돌릴 수 없습니다. 숨길까요?"
  - 숨김 게임이면 상단에 「운영 중지」 배지, "파티 만들기" 버튼 숨김
  - 수정·숨김 버튼 노출 기준: `me.id === owner.id`
- 공용 컴포넌트
  - `GameStatusBadge`: `visible=false` → 「운영 중지」 (파티 목록·상세, C단계 예약 목록에서 재사용)
  - `PlayModeBadge`: 「온라인」/「오프라인」 (게임은 가능한 방식 모두, 파티는 선택한 방식 1개)
  - `CustomGameBadge`: 기타 게임 파티에 「기타 게임」

### 8-2. ⭐ 게임 선택 모달 (`BoardGameSelectModal`)
- 파티 개설 화면의 게임 select box를 **"게임 선택" 버튼 → 모달**로 교체
- 모달 내용
  - 상단 검색 입력(게임 이름). 입력 디바운스 300ms → 기존 `GET /api/boardgames?keyword=` 재사용 (기본이 `visible=true`라 **숨겨지지 않은 모든 게임**이 대상)
  - 목록 한 줄에 **게임 이름 / 인원(`min~max명`) / 온·오프라인 배지**만 표시
  - 행 클릭 → 선택되고 모달 닫힘
  - 로딩·빈 결과("검색 결과가 없습니다")·에러 처리는 기존 공용 컴포넌트 사용
  - 목록 API가 페이징이면 "더 보기" 버튼, 아니면 전체 표시 (API 현황 확인 후 결정)
  - 목록 맨 아래(검색 결과가 없을 때도) **"목록에 없는 게임이에요 → 직접 입력"** 버튼 → 모달 닫고 기타 게임 입력 모드로
  - 닫기: X 버튼, ESC, 바깥 영역 클릭. 열릴 때 검색창 포커스
- 모달은 공용 `Modal` 컴포넌트(오버레이·ESC·포커스)로 만들고 그 위에 게임 선택 모달 구성 → 이후 다른 화면에서도 재사용
- `?boardGameId=` 쿼리로 들어오면 그 게임이 미리 선택된 상태 (기존 동작 유지)

### 8-3. 파티 개설 폼
- 게임 영역은 두 가지 상태
  - **보드게임 선택됨**: 게임 요약(이름·인원·방식) + "변경" 버튼(모달 다시 열기)
  - **기타 게임 입력**: 게임 이름 입력칸(필수, 최대 50자) + "보드게임에서 고르기" 링크(모달 열기)
  - 아무것도 없으면 "게임 선택" 버튼만. 제출 시 "보드게임을 선택하거나 게임 이름을 입력해주세요"
- 진행 방식 라디오(온라인/오프라인)
  - 보드게임: 지원하지 않는 방식은 비활성, 한 방식만 지원하면 자동 선택. 게임을 바꿔서 방식이 불가해지면 초기화
  - 기타 게임: 둘 다 선택 가능
- ONLINE → 플랫폼, 접속 링크(선택) 입력 / OFFLINE → 장소(선택, 자유 텍스트) 입력
- 정원 범위: 보드게임은 minPlayers~maxPlayers, 기타 게임은 2~20 ("호스트 포함 인원" 안내 유지)

### 8-4. 파티 목록·상세
- 목록: 게임명(`gameName`) + 기타 게임이면 `CustomGameBadge`, `PlayModeBadge`, 필터에 진행 방식 추가
- 상세: 게임명(보드게임이면 게임 상세 링크, 기타 게임이면 텍스트만) + 방식 배지 + 온라인이면 플랫폼·접속 링크(참여자에게만 보임, 아니면 "참여하면 접속 링크가 공개됩니다") / 오프라인이면 장소
  - 링크는 `target="_blank" rel="noopener noreferrer"`
- 호스트에게 참여자마다 "내보내기" 버튼(호스트 본인 제외, confirm)
- CANCELLED + `boardGameVisible=false` → "게임 운영 중지로 취소된 파티입니다"
- 내보내진 회원이 "참여하기"를 누르면 403 서버 메시지 표시 (409와 같은 처리)
- 쿼리 invalidate: 숨기기 → `['boardgames']`, `['boardgame', id]`, `['parties']` / 내보내기 → `['party', id]`, `['parties']`

## 9. 테스트
- `YoutubeUrlParserTest`: 형식별 성공, 잘못된 링크 예외, 빈 값 null
- 소유권: 다른 ADMIN이 수정·숨기기 → 403, SUPER_ADMIN도 남의 게임은 403
- 진행 방식: 둘 다 false → 400, 지원하지 않는 방식으로 개설 → 400, 모집 중 파티가 있는 방식 끄기 → 409, 잘못된 onlineLink → 400
- 기타 게임: customGameName으로 개설 201, boardGameId·customGameName 둘 다/둘 다 없음 → 400, 정원 2~20 밖 → 400, **목록·상세에 기타 게임 파티가 나오는지(left join 확인)**, 기타 게임 파티 join·동시성 정상
- onlineLink 노출: 비로그인·미참여 → null, 참여자·호스트 → 값, 내보내진 회원 → null
- 숨기기: RECRUITING → CANCELLED, CLOSED 유지, Redis 키 삭제, 기본 목록에서 제외, 상세는 조회 가능, 숨김 게임으로 개설 → 409
- 내보내기: 비호스트 403, 호스트 자신 400, 내보낸 뒤 remaining +1, 내보낸 회원 재참여 403, 다른 회원은 그 자리에 참여 가능, COUNT·목록에서 제외
- Redis 키가 없을 때 복구 로직이 KICKED를 제외하는지
- `http/boardgame.http`, `http/party.http` 갱신
