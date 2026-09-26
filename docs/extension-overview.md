# 추가 기능 개요 (챗봇 전 확장) — 2026-09-26 확정

레포 위치: `docs/extension-overview.md`. A~D 각 단계 계획서보다 **이 문서가 우선**한다(공통 규칙).

---

## 1. 진행 순서

| 순서 | 브랜치 | 계획서(docs/) | 내용 |
|---|---|---|---|
| A | `feature/member-profile` | `member-plan.md` | 회원정보 확장, 아바타(이모지/이미지), 파일 업로드 공통, SUPER_ADMIN, 관리자 가입 승인, 내 정보 페이지 |
| B | `feature/boardgame-media` | `boardgame-plan.md` | 게임 이미지, 유튜브 임베드, 온라인/오프라인, 재고, 소유 관리자, 숨기기(운영 중지), 게임 선택 모달, 기타 게임 파티, 파티 멤버 내보내기 |
| C | `feature/reservation` | `reservation-plan.md` | 기간 대여 예약, 달력 가용 표시, 재고 동시성(DB 락), 소유 관리자 승인 |
| D | `feature/notification` | `notification-plan.md` | SSE 실시간 알림, 토스트(10초), 헤더 알림함 |

- 각 브랜치에서 **백엔드 → 프론트** 순. Claude Code 세션은 백엔드/프론트를 나누고 각각 `/clear`
- B가 크면 B-1(게임 미디어·방식·숨기기) / B-2(파티 게임 종류·방식·모달·내보내기)로 나눠 진행해도 됨
- 디자인은 "기능이 동작하는 수준"으로 기존 Tailwind 스타일을 따른다. 세부 디자인은 이후 별도 작업
- 각 단계 완료 조건: 백엔드 `.\gradlew test` 전체 통과, 프론트 `npm run lint` + `npm run build` 통과, 브라우저 수동 확인

## 2. 확정된 결정

| 항목 | 결정 |
|---|---|
| 관리자 가입 | 가입 시 "관리자 신청" 선택 → 일단 USER로 가입, 신청 상태 PENDING. SUPER_ADMIN 승인 시 ADMIN |
| 승인 권한 | SUPER_ADMIN(env의 ADMIN_EMAIL 계정)만 |
| 회원 정보 | 이름, 이메일, 닉네임, 생년월일, 한줄소개, 소속, 직업 + 아바타(이모지 또는 이미지) |
| 내 정보 | 헤더의 본인 이름 클릭 → 내 정보 페이지(조회·수정·비밀번호 변경·관리자 신청 상태) |
| 게임 미디어 | 대표 이미지 1장, 유튜브 링크 1개(저장 후 상세에서 바로 재생) |
| 게임 진행 방식 | 등록 시 "오프라인 가능" / "온라인 가능" 체크(최소 1개, 둘 다 가능) |
| 파티 게임 종류 | **등록된 보드게임 선택** 또는 **기타 게임 이름 직접 입력**(예: 구스구스덕, 롤) 중 하나 필수. 기타 게임은 정원 2~20, 방식 자유 |
| 파티 진행 방식 | 온라인/오프라인 중 하나 필수(보드게임은 게임이 지원하는 방식만). 온라인은 플랫폼·접속 링크(선택, **호스트·참여자에게만 공개**), 오프라인은 장소(선택, 자유 텍스트) |
| 게임 선택 UI | select box 대신 **검색 모달**. 표시 정보: 이름 / 인원 / 온·오프라인. 대상: 숨기지 않은 모든 게임. 하단에 "직접 입력" |
| 방식 끄기 | 그 방식으로 모집 중인 파티(오프라인이면 남은 예약 포함)가 있으면 수정 차단(자동 취소 X) |
| 재고 | 게임별 재고 수량(stock). 온라인 전용 게임은 재고 없음(0) |
| 예약 | 기간 대여(당일 / 여러 날). **승인 전(PENDING)도 재고를 점유** → 달력에서 해당 날짜 선택 불가. **온라인 전용 게임은 예약 불가** |
| 예약 승인 | 그 게임을 등록한 관리자 본인만 |
| 반납 | 관리하지 않음 (종료일이 지나면 화면에 "대여 완료"로만 표시) |
| 삭제 → 숨기기 | 게임 삭제 기능 제거, 보이기/숨기기로 대체. 숨기면 **진행 중인 파티·예약은 전부 취소** |
| 숨김 게임 표시 | 배지 문구 **「운영 중지」** (다시 보이게 할 수 있으므로 "종료"보다 "중지") |
| 파티 멤버 관리 | 호스트가 참여자를 내보낼 수 있음. 내보낸 회원은 그 파티에 재참여 불가 |
| 알림 | 로그인 중이면 오른쪽 하단 네모 토스트 → 10초 후 사라짐. 헤더 이름 옆 알림(🔔) 버튼 |

## 3. 공통 규칙

### 3-1. 역할·권한
- `Role`: `USER`, `ADMIN`, `SUPER_ADMIN`
- **RoleHierarchy**: `SUPER_ADMIN > ADMIN > USER` → `hasRole('ADMIN')` API에 SUPER_ADMIN도 통과
- SUPER_ADMIN은 부트스트랩(env `ADMIN_EMAIL`) 계정 1개뿐. 가입으로 만들 수 없음
- **게임 소유권**: `BoardGame.createdBy`. 게임 수정·이미지·숨기기·예약 승인은 **소유 관리자 본인만** (SUPER_ADMIN도 자기 게임만). 기존에 등록된 게임은 SUPER_ADMIN 소유로 이관

### 3-2. 파일 업로드 (A에서 구현, B에서 재사용)
- `FileStorage` 인터페이스 + `LocalFileStorage` 구현. 7단계 배포 때 S3 구현체로 교체 예정 → 인터페이스 밖에서 로컬 경로를 가정하지 말 것
- 이미지가 포함된 생성/수정 API는 **multipart/form-data**: `data`(JSON part) + `image`(파일 part, 선택)
- 프론트: `FormData`에 data를 `new Blob([JSON.stringify(...)], {type:'application/json'})`로 넣는다. Content-Type 헤더는 직접 지정하지 않음(브라우저가 boundary 포함해 설정)
- 응답은 파일 키가 아니라 **`imageUrl`**(`/api/files/...`)로 내려준다

### 3-3. 스키마 변경
- 기존 데이터가 있는 테이블(member, board_game, party, party_member)에 컬럼이 추가·변경된다(party.board_game_id는 nullable로). NOT NULL 컬럼은 기본값이 없으면 PostgreSQL에서 실패
- **구현 전에 `application.yml`의 `ddl-auto` 설정을 확인하고 적용 방식을 보고**할 것 (`ddl-auto: update`는 기존 컬럼의 NOT NULL을 풀지 않음 → 확인 필요)
- 로컬 DB는 테스트 데이터뿐이라 필요하면 볼륨 초기화(`docker compose down -v`) 허용. 그 경우 반드시 알려줄 것 (ADMIN 부트스트랩은 재실행 시 다시 생성됨)

### 3-4. 날짜·시간
- 날짜 판단(오늘, 예약 가능 기간)은 `Asia/Seoul` 기준. 서비스는 `java.time.Clock` 빈을 주입받아 사용 → 테스트에서 고정 시각 사용

### 3-5. 도메인 이벤트 (D 대비)
- A~C는 알림이 필요한 지점에서 **이벤트 발행만** 해 둔다 (`ApplicationEventPublisher`, 이벤트는 `record`, 패키지 `global.event` 또는 각 도메인 `event`). 리스너는 D에서 추가
- 이벤트에는 id만 담는다(엔티티 X). 이벤트 목록은 notification-plan.md 6장이 기준

### 3-6. 기존 규칙 유지
- ApiResponse / BusinessException(ErrorCode) / BaseTimeEntity / 도메인형 패키지 구조
- 기존 테스트(69개)는 계속 통과해야 함. API 형식 변경으로 수정이 필요한 테스트는 **수정 전에 목록 보고**
- 동시성 테스트(PartyConcurrencyTest)는 결과 수치가 그대로여야 함
