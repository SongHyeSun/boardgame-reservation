# D. 실시간 알림 구현 계획 (feature/notification)

레포 위치: `docs/notification-plan.md`. 공통 규칙은 `docs/extension-overview.md` 3장. A~C에서 발행해 둔 도메인 이벤트를 구독한다.

---

## 1. 방식 선택: SSE (Server-Sent Events)

| 방식 | 판단 |
|---|---|
| 폴링 | 지연 + 불필요한 요청 → X |
| WebSocket | 양방향 불필요, 설정·보안 부담 → 과함 |
| **SSE** ✅ | 서버→클라이언트 단방향으로 충분. 일반 HTTP라 **세션 쿠키 인증 그대로**, 브라우저 `EventSource`가 자동 재연결 |

## 2. 도메인 (notification 패키지)

- `NotificationType` (6장 표)
- `Notification`: id, receiver(@ManyToOne LAZY Member), type, message(완성된 한국어 문장), link(프론트 경로, 예 `/parties/3`), isRead, createdAt
- 인덱스: `(receiver_id, is_read, created_at)`

## 3. 흐름

```
[A~C 서비스] 이벤트 발행
      ▼  @TransactionalEventListener(AFTER_COMMIT)
[NotificationEventListener] 받는 사람 결정 → Notification 저장(REQUIRES_NEW)
      ▼  Redis Pub/Sub 채널 "notifications" 에 {receiverId, notification} 발행
[각 서버 인스턴스의 Subscriber] 그 회원의 SseEmitter를 가지고 있으면 전송
```
- **AFTER_COMMIT**: 롤백된 작업(예: DB 실패로 보상된 파티 참여)에는 알림이 가지 않음
- 알림 저장·전송 실패가 본 기능을 실패시키면 안 됨 → 리스너 예외는 로그만
- **Redis Pub/Sub을 쓰는 이유**: 세션 외부화와 같은 이유. 서버가 여러 대면 알림을 만든 서버와 사용자가 SSE로 연결된 서버가 다를 수 있음 (README 포인트)
- 행위자 = 받는 사람이면 알림 생략 (예: 호스트 본인 행동)

## 4. SSE 연결

- `GET /api/notifications/stream` (로그인, `text/event-stream`)
- `SseEmitterRepository`: `memberId → List<SseEmitter>` (탭 여러 개 허용), ConcurrentHashMap
- timeout 30분, onCompletion/onTimeout/onError 시 제거
- 연결 직후 `connected` 이벤트 1회, **25초마다 heartbeat**(comment) — 프록시·브라우저가 끊지 않도록 (@Scheduled)
- 이벤트명 `notification`, data = NotificationResponse JSON
- 확인할 것(**구현 전 보고**)
  - Spring Security가 SSE의 async dispatch에서 401/AccessDenied를 내는 경우가 있음 → 발생하면 원인과 대응 보고
  - Vite 프록시로 SSE가 끊김 없이 전달되는지
  - 7단계 nginx 메모: `proxy_buffering off`, `proxy_read_timeout` 늘리기

## 5. REST API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/notifications?page=0&size=20` | 내 알림 최신순 |
| GET | `/api/notifications/unread-count` | `{ count }` |
| PATCH | `/api/notifications/{id}/read` | 본인 것만 (아니면 NOTIFICATION_NOT_FOUND 404) |
| PATCH | `/api/notifications/read-all` | 전부 읽음 |

## 6. 알림 종류

| type | 받는 사람 | 메시지 예 | link |
|---|---|---|---|
| PARTY_JOINED | 호스트 | {닉네임}님이 '{파티}' 파티에 참여했어요 | /parties/{id} |
| PARTY_FULL | 호스트 | '{파티}' 파티 정원이 모두 찼어요 | /parties/{id} |
| PARTY_LEFT | 호스트 | {닉네임}님이 '{파티}' 파티에서 나갔어요 | /parties/{id} |
| PARTY_KICKED | 내보내진 회원 | '{파티}' 파티에서 내보내졌어요 | /parties/{id} |
| PARTY_CLOSED | 참여자(호스트 제외) | '{파티}' 파티 모집이 마감됐어요 | /parties/{id} |
| GAME_SUSPENDED | 취소된 파티 참여자·예약자 | '{게임}' 운영 중지로 '{파티}' 파티가 취소됐어요 / 예약이 취소됐어요 | /parties/{id}, /me/reservations |
| RESERVATION_REQUESTED | 게임 소유 관리자 | {닉네임}님이 '{게임}' 대여를 신청했어요 (기간) | /admin/reservations |
| RESERVATION_APPROVED | 신청자 | '{게임}' 대여 예약이 승인됐어요 | /me/reservations |
| RESERVATION_REJECTED | 신청자 | '{게임}' 대여 예약이 거절됐어요 (사유) | /me/reservations |
| RESERVATION_CANCELLED | 게임 소유 관리자 | {닉네임}님이 '{게임}' 예약을 취소했어요 | /admin/reservations |
| ADMIN_REQUESTED | SUPER_ADMIN | {닉네임}님이 관리자 권한을 신청했어요 | /admin/admin-requests |
| ADMIN_APPROVED / ADMIN_REJECTED | 신청자 | 관리자 신청이 승인됐어요(다시 로그인해주세요) / 거절됐어요 | /me |

- PARTY_FULL: join 결과 remaining == 0 일 때
- ADMIN_APPROVED는 승인과 동시에 세션이 끊기므로 실시간 토스트 대신 **재로그인 후 알림함**에서 확인됨 (정상 동작)
- A~C에서 발행된 이벤트 이름·필드가 이 표와 다르면 표에 맞춰 정리하고 보고

## 7. 프론트

- `hooks/useNotificationStream`: `me`가 있을 때 `EventSource('/api/notifications/stream')` 연결, `me`가 null이 되면 close. 앱 최상단(Layout)에서 1회만
  - 수신 시: 토스트 추가 + `['notifications']`, `['notifications','unread']` invalidate + link 관련 쿼리 invalidate(보고 있는 파티 상세·예약 목록 자동 갱신)
  - onerror: 브라우저가 자동 재연결함. 단 세션이 끊긴 경우 무한 재연결 방지 → `me` 재조회 후 null이면 close
- **토스트**: 화면 오른쪽 하단 고정, 네모 카드(메시지·시각·닫기 X), **10초 후 자동 사라짐**, 여러 개면 위로 쌓임(최대 5), 클릭 시 읽음 처리 + link 이동. 라이브러리 없이 Context로 구현
- **헤더**: `[아바타] 닉네임님` 옆 🔔 버튼 + 안 읽은 수 배지(99+)
  - 클릭 → 드롭다운: 최근 20개, 안 읽은 알림 강조, 클릭 시 읽음 + 이동, "모두 읽음"
- 로그아웃 시 연결 종료 + 캐시 정리(기존 로그아웃 흐름에 추가)

## 8. 테스트
- 리스너: 이벤트 → 받는 사람별 저장, 행위자 본인 제외
- ⭐ AFTER_COMMIT: 트랜잭션이 롤백되면 알림이 저장되지 않음
- 파티 join 성공 → 호스트 알림 1건, 정원이 차면 PARTY_FULL 추가. **PartyConcurrencyTest·ReservationConcurrencyTest 결과 불변**
- REST: 본인 것만 조회·읽음(남의 알림 404), unread-count, read-all
- SSE: 비로그인 401, 연결 후 이벤트 발생 → 수신 (MockMvc async 또는 테스트용 구독으로 가능한 범위에서)
- `http/notification.http` 추가
