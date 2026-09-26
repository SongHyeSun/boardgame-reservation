# A. 회원 확장 · 관리자 승인 구현 계획 (feature/member-profile)

레포 위치: `docs/member-plan.md`. 공통 규칙은 `docs/extension-overview.md` 3장.

---

## 1. Member 필드 추가

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| email | (기존) | ✅ | 변경 불가 |
| nickname | (기존) | ✅ | 기존 DTO 규칙 유지 |
| name | varchar(20) | ✅(가입 DTO) | 2~20자. DB는 nullable로 추가(기존 계정 때문), 가입·수정 DTO에서 필수 |
| birthDate | date | 선택 | 과거 날짜만 |
| affiliation | varchar(50) | 선택 | 소속 |
| job | varchar(50) | 선택 | 직업 |
| bio | varchar(100) | 선택 | 한줄소개 |
| avatarType | varchar | ✅ | `EMOJI` / `IMAGE`, 기본 `EMOJI` |
| avatarEmoji | varchar | ✅ | 기본 `🎲`. 허용 목록 안의 값만 |
| avatarImageKey | varchar | 선택 | FileStorage 키 |
| adminRequestStatus | varchar | ✅ | `NONE` / `PENDING` / `APPROVED` / `REJECTED`, 기본 `NONE` |
| adminRequestedAt | timestamp | 선택 | 신청 시각 |

- 허용 이모지(백엔드 상수 = 프론트 상수, 동일 목록):
  `🎲 🃏 ♟️ 🧩 🎯 🏆 🐉 🦊 🐱 🐻 🐧 🦉 🌟 🔥 🍀 🎩`
- 아바타 응답 공통 형식 `AvatarResponse { type, emoji, imageUrl }` → 내 정보, 헤더, 파티 참여자 목록, 파티 호스트에 사용

## 2. 파일 저장 (global.file)

- `FileStorage`: `String store(MultipartFile file, String dir)`, `Resource load(String key)`, `void delete(String key)`
- `LocalFileStorage`: 저장 경로 `app.upload.dir`(기본 `./uploads`, `.gitignore`에 추가). 키 = `{dir}/{uuid}.{ext}`, dir은 `avatars` / `boardgames`
- 검증: 최대 5MB, `jpg/jpeg/png/webp`. **확장자 + Content-Type + 파일 시그니처(매직 바이트)** 셋 다 확인
- `spring.servlet.multipart.max-file-size=5MB`, `max-request-size=6MB`. `MaxUploadSizeExceededException` → FILE_TOO_LARGE
- 서빙: `GET /api/files/{dir}/{filename}` (permitAll). 키 정규식으로 검증해 경로 조작 차단
  `^(avatars|boardgames)/[0-9a-f-]{36}\.(jpg|jpeg|png|webp)$` 불일치 → 404. `Cache-Control` 장기 캐시
- 파일-DB 정합성
  - 새 파일 저장 후 DB 처리 실패 → 새 파일 삭제
  - 이미지 교체/제거 시 **이전 파일 삭제는 DB 커밋 후**(afterCommit, 파티 개설 Redis 키 세팅과 같은 패턴)

## 3. 회원가입

`POST /api/auth/signup` — **multipart/form-data** 로 변경
- `data`(JSON): `{ email, password, nickname, name, birthDate?, affiliation?, job?, bio?, avatarEmoji?, requestAdmin }`
- `image`(선택): 있으면 `avatarType=IMAGE`
- `requestAdmin=true` → role `USER`, `adminRequestStatus=PENDING`, `adminRequestedAt=now` + `AdminRequestedEvent(memberId)` 발행
- 성공 201
- ⚠️ 기존 가입 테스트·`http/auth.http`는 multipart로 수정 (수정 전 목록 보고)

## 4. 내 정보

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/members/me` | 기존 필드 + name, birthDate, affiliation, job, bio, avatar, adminRequestStatus |
| PUT | `/api/members/me` | multipart. `data {nickname, name, birthDate, affiliation, job, bio, avatarType, avatarEmoji, removeImage}` + `image?` |
| PATCH | `/api/members/me/password` | `{ currentPassword, newPassword }` |
| POST | `/api/members/me/admin-request` | 관리자 신청(재신청) |

- `GET /me`는 **세션의 MemberPrincipal 값이 아니라 DB에서 조회** (닉네임·아바타 수정이 즉시 반영되도록). MemberPrincipal에 nickname 등이 캐시돼 있다면 그 값을 화면용으로 쓰는 곳이 있는지 확인·보고
- PUT 규칙: `avatarType=IMAGE`인데 새 이미지도 기존 이미지도 없으면 400. `removeImage=true`면 이미지 삭제 + `EMOJI`로 전환
- 비밀번호: 현재 비밀번호 불일치 → INVALID_PASSWORD(400). 새 비밀번호 규칙은 가입 DTO와 동일
- 관리자 신청: role이 USER이고 상태가 `NONE`/`REJECTED`일 때만. 아니면 INVALID_ADMIN_REQUEST(409). 성공 시 PENDING + 이벤트 발행

## 5. 관리자 승인 (SUPER_ADMIN)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/admin-requests` | PENDING 목록 (memberId, email, nickname, name, affiliation, job, requestedAt) |
| PATCH | `/api/admin/admin-requests/{memberId}/approve` | role → ADMIN, 상태 APPROVED |
| PATCH | `/api/admin/admin-requests/{memberId}/reject` | 상태 REJECTED |

- SecurityConfig: `/api/admin/admin-requests/**` → `hasRole('SUPER_ADMIN')`
- 대상이 PENDING이 아니면 INVALID_ADMIN_REQUEST(409), 없으면 MEMBER_NOT_FOUND(404)
- 승인/거절 시 `AdminRequestDecidedEvent(memberId, approved)` 발행
- ⭐ **세션 권한 갱신 문제**: Redis 세션에 저장된 권한은 로그인 시점 값이라, 승인돼도 기존 세션은 USER 권한 그대로.
  → 승인 시 **해당 회원의 세션을 모두 무효화**(재로그인 유도). Spring Session의 `FindByIndexNameSessionRepository`로 principal name 기준 조회·삭제
  - 현재 세션 저장소가 인덱스를 지원하는지 확인하고, 필요하면 indexed 방식으로 변경 (Spring Boot 4.1 기준 정확한 설정 확인 후 **구현 전 보고**)
  - troubleshooting.md 기록 대상
- 부트스트랩: `ADMIN_EMAIL` 계정을 `SUPER_ADMIN`으로 생성, 이미 있으면 role을 `SUPER_ADMIN`으로 갱신

## 6. ErrorCode 추가
INVALID_FILE(400 "지원하지 않는 이미지 파일입니다"), FILE_TOO_LARGE(400 "이미지는 5MB 이하만 업로드할 수 있습니다"), FILE_NOT_FOUND(404),
INVALID_PASSWORD(400 "현재 비밀번호가 일치하지 않습니다"), INVALID_ADMIN_REQUEST(409), INVALID_AVATAR(400)
(MEMBER_NOT_FOUND 없으면 추가)

## 7. 프론트

- 타입: `Avatar`, `MemberMe` 확장, `AdminRequestStatus`
- `api/client`에 multipart 헬퍼 (3-2 규칙)
- 공용 컴포넌트
  - `Avatar` (이모지 또는 이미지, 크기 sm/md/lg)
  - `EmojiPicker` (고정 목록)
  - `ImageInput` (미리보기, 제거, 5MB·형식 클라이언트 검증)
- `/signup`: 추가 필드, 아바타(이모지 선택 또는 이미지 업로드), "관리자로 가입 신청" 체크 + 안내 "최고 관리자 승인 후 관리자 권한이 부여됩니다"
- 헤더: `[아바타] 닉네임님` → 클릭 시 `/me` (알림 버튼은 D단계)
- `/me` (ProtectedRoute)
  - 조회 모드 / 수정 모드 전환, 이메일은 읽기 전용
  - 비밀번호 변경 섹션
  - 관리자 신청 상태: NONE → "관리자 신청" 버튼 / PENDING → "승인 대기 중" / REJECTED → "거절됨 · 재신청" / ADMIN·SUPER_ADMIN → 역할 표시
  - 수정 성공 시 `setQueryData(['me'])` 또는 invalidate
- `/admin/admin-requests` (SuperAdminRoute 신규): 목록 + 승인/거절(confirm)
- 헤더: SUPER_ADMIN이면 "관리자 승인" 메뉴
- 파티 상세 참여자 목록, 파티 목록 호스트 옆에 Avatar
- 승인으로 세션이 끊긴 사용자는 다음 요청에서 401 → `me` null → 기존 흐름대로 로그인 유도

## 8. 테스트
- 가입: multipart 성공(이미지 유/무), 필수값 누락 400, 잘못된 파일(형식·시그니처 불일치) 400, 5MB 초과 400, `requestAdmin` → PENDING
- 내 정보 수정(아바타 전환·이미지 제거), 비밀번호 변경(불일치 400)
- 관리자 승인: ADMIN이 호출 → 403, SUPER_ADMIN → 성공, 승인 후 role ADMIN, **승인 전에 만든 세션으로 `/me` → 401**
- RoleHierarchy: SUPER_ADMIN이 ADMIN 전용 API 접근 가능
- 파일 서빙: 잘못된 키(`../` 등) → 404
- 파일 저장 테스트는 임시 디렉터리(@TempDir) 사용
- `http/member.http` 추가(가입 multipart·내 정보·승인 흐름)
