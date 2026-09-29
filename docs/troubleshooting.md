# 트러블슈팅 기록

증상 / 원인 / 해결 / 재발 방지 순으로 적는다. 최신 항목이 아래.

---

## 1. `ddl-auto: update` 로는 스키마 변경이 다 적용되지 않는다 (A단계)

**증상**
- 회원 확장(A단계) 전에 만든 로컬 DB 그대로 `Role.SUPER_ADMIN` 과 새 컬럼을 추가하려고 하면 부트스트랩·조회가 깨질 상황이었다.
  - `member` 테이블에 `member_role_check CHECK (role IN ('USER','ADMIN'))` 가 있어서 `role='SUPER_ADMIN'` 저장이 제약 위반
  - 기존 행(4개)이 있는 테이블에 `NOT NULL` 컬럼(`avatar_type`, `avatar_emoji`, `admin_request_status`)을 추가할 수 없음

**원인**
- `application.yml` 은 `ddl-auto: update`. Hibernate `update` 는 **없는 컬럼/테이블을 추가**할 뿐이다.
  - 이미 만들어진 CHECK 제약(enum STRING 컬럼에 자동 생성됨)은 **갱신하지 않는다** → enum 값을 추가해도 DB 제약은 옛 값 목록 그대로.
  - 기본값 없는 `NOT NULL` 컬럼 추가는 기존 행 때문에 PostgreSQL 에서 실패한다. Hibernate 는 기본 설정(`halt_on_error=false`)이면 오류를 로그로만 남기고 기동을 계속하므로, 컬럼이 없는 상태로 떠서 나중에 런타임 SQL 오류로 드러난다. (일반적인 Hibernate 동작 기준이며 이번에 실제로 재현해 확인하지는 않았다 — 초기화로 우회함)
- 테스트는 H2 `create-drop`(매번 새로 생성)이라 이 문제가 **테스트에서는 절대 드러나지 않는다.**

**해결**
- 로컬 DB 는 테스트 데이터뿐이라 볼륨 초기화: `docker compose down -v` → `docker compose up -d`
  (Redis 컨테이너도 함께 재생성되어 세션·파티 키도 초기화됨. `ADMIN_EMAIL` 계정은 기동 시 SUPER_ADMIN 으로 다시 생성됨)
- 초기화하지 않는 대안(권장하지 않음): `ALTER TABLE member DROP CONSTRAINT member_role_check;` + NOT NULL 컬럼을 default 를 주고 수동 추가

**재발 방지**
- enum 에 값을 추가하거나 기존 테이블에 NOT NULL 컬럼을 넣는 작업(B~C 단계 포함)은 **착수 전에 `\d 테이블` 로 기존 CHECK/NOT NULL 을 확인**하고 초기화 여부를 보고한다.
- 배포 전에는 계획대로 `ddl-auto: validate` + 스키마 스크립트로 전환한다.

---

## 2. 관리자 승인 후에도 기존 세션은 예전 권한(USER)이 그대로 남는다 (A단계)

**증상**
- SUPER_ADMIN 이 신청을 승인해 role 이 ADMIN 으로 바뀌어도, 이미 로그인해 있던 그 회원은 계속 `USER` 권한으로 동작(ADMIN API 는 403).

**원인**
- 세션(Redis)에는 로그인 시점의 `SecurityContext`(→ `MemberPrincipal` 의 role/authorities)가 통째로 저장돼 있어 DB 의 role 이 바뀌어도 반영되지 않는다.
- 기본 Spring Session Redis 저장소(`RedisSessionRepository`, `repository-type: default`)는 `FindByIndexNameSessionRepository` 를 구현하지 않아 **회원별로 세션을 찾을 방법이 없다.**

**해결**
- 세션 저장소를 indexed 로 변경 (Boot 4.1 기준 프로퍼티는 `spring.session.data.redis.*` — 옛 `spring.session.redis.*` 는 4.0.0 부터 error 수준 deprecated)
  ```yaml
  spring:
    session:
      data:
        redis:
          repository-type: indexed
  ```
- principal name(= `Authentication.getName()` = 로그인 email, 소문자 정규화)으로 인덱싱되므로, 승인 트랜잭션이 **커밋된 뒤** `MemberSessionInvalidator` 가 `findByPrincipalName(email)` 로 세션을 찾아 전부 삭제 → 다음 요청은 401 → 재로그인하면 새 권한.
  - 그래서 **email 은 변경 불가**여야 한다(인덱스 키).
  - 커밋 전에 지우지 않는다(롤백됐는데 세션만 끊기는 것 방지). Redis 오류는 로그만 남기고 삼킨다(DB 는 이미 커밋됨).
- 기동 시 Redis 에 `notify-keyspace-events` 를 설정한다(기본 `configure-action`). 관리형 Redis 에서 `CONFIG` 가 막혀 있으면 `configure-action: none` + 파라미터 그룹에서 직접 설정 (7단계 배포 때 확인).

**알아둘 것**
- indexed 저장소는 세션 본문 외에 `sessions:expires:*`, `expirations:*`, `index:*` 키를 함께 만든다. 세션을 삭제해도 본문 해시가 만료 이벤트용으로 잠시 남을 수 있으므로, "키가 없다"가 아니라 "그 세션으로 요청하면 401" 로 검증할 것.
- 테스트 `application.yml` 은 main 의 `application.yml` 을 **가려서**(같은 이름, 클래스패스 우선) main 에만 넣은 설정은 테스트에 적용되지 않는다. 세션 property 는 test yml 에도 넣어야 한다.

---

## 3. 로컬 PostgreSQL 인증 실패 (옛 pgdata 볼륨의 비밀번호 불일치)

- **증상**: 로컬 PostgreSQL 인증 실패.
- **원인**: 옛 `pgdata` 볼륨에 남아 있던 비밀번호가 현재 설정과 달랐다.
- **해결**: `ALTER USER` 로 비밀번호를 맞춤.
- **포인트**: Testcontainers(매번 새 DB)를 쓰는 테스트에서는 이 문제가 드러나지 않았다 → 테스트 통과가 로컬 DB 연결 정상을 보장하지 않는다.

---

## 4. 5432 포트를 WSL(wslrelay)이 `[::1]` 로 함께 점유

- **원인**: 5432 포트를 WSL(wslrelay)이 `[::1]` 로도 함께 점유하고 있었다.
- **해결**: `DB_URL` 을 `127.0.0.1` 로 지정.

---

## 5. 프론트 리팩터링 후 import 누락 런타임 에러

- **증상**: 프론트 리팩터링 후 import 누락으로 런타임 에러.
- **해결/재발 방지**: 리팩터링 뒤에는 화면을 한 바퀴 직접 확인한다.

---

## 6. 게임 숨기기로 CANCELLED 파티가 생기면서 `close()` 가 이를 CLOSED 로 덮어쓸 수 있던 문제 (B-1)

- 원인: `PartyService.close()` 가 상태 검사 없이 `party.close()` 를 호출해, 호스트가 취소된 파티를 닫으면 CANCELLED 가 CLOSED 로 바뀔 수 있었다 → `!isRecruiting` 이면 `PARTY_NOT_RECRUITING`(409) 으로 막음.

---

## 7. B-2: 스키마 NOT NULL 해제와 내보내기 우회 경로

- `ddl-auto: update` 는 기존 컬럼의 NOT NULL 을 풀지 못함 → `party.board_game_id` 는 수동 ALTER 로 해제 (`ALTER TABLE party ALTER COLUMN board_game_id DROP NOT NULL;`)
- 내보내진(KICKED) 회원이 leave 로 자기 행을 지워 재참여하는 우회 경로 → 삭제 조건에 `status = JOINED` 추가로 차단

---

## 8. D단계: `ddl-auto: update` 는 새 테이블의 FOREIGN KEY 제약 추가도 실패 시 WARN 만 남기고 기동을 계속할 수 있음 (notification)

- 개발 중 재기동을 반복하며 테이블이 먼저 생성된 뒤 제약이 누락된 사례 → `notification` 테이블 재생성으로 해결.

---

## 9. Gemini 무료 티어에서 Google Search grounding 미지원 (챗봇, 계획 단계)

- **확인**: Gemini 3.x 계열 무료 티어에서는 Google Search grounding(웹 검색)을 쓸 수 없음을 확인.
- **결정**: 챗봇 첫 구현 범위에서 웹 검색 기능(`app.chat.web-search-enabled`, 응답 `sources` 등)을 전부 제외. `searchBoardGames` function calling으로 DB 기반 추천만 남김.
- **재발 방지**: 나중에 웹 검색을 다시 넣을 땐 그 시점의 유료/무료 티어 지원 여부부터 다시 확인할 것.

---

## 10. Jackson 3에서 `JsonNode.asText()`/`asText(default)` 가 deprecated (챗봇)

- **증상**: `GeminiLlmClient`/`GameSearchTool` 컴파일 시 "Some input files use or override a deprecated API" 경고.
- **원인**: 이 프로젝트가 쓰는 Jackson 3(`tools.jackson.databind`, jackson-databind 3.1.5)는 `asText()`/`asText(String)`을 `asString()`/`asString(String)`으로 바꾸고 옛 이름은 `@Deprecated`(since 3.0)로만 남겨뒀다.
- **해결**: 두 클래스의 `asText` 호출을 전부 `asString`으로 교체.
- **재발 방지**: Jackson 3 코드에서 텍스트 값을 꺼낼 땐 `asString()`/`asString(default)`을 쓴다(`asText`는 옛 API로 남아 있을 뿐).

---

## 11. `RestClient.Builder` 빈이 스프링 컨텍스트에 없어 기존 통합테스트 239개가 연쇄 실패 (챗봇)

- **증상**: `ChatLlmConfig`가 `RestClient.Builder`를 생성자로 주입받게 했더니, 챗봇과 무관한 기존 통합테스트(`MemberProfileIntegrationTest`, `SignupIntegrationTest`, `Party*`, `Reservation*`, `Notification*` 등) 239개가 전부 `IllegalStateException`(`DefaultCacheAwareContextLoaderDelegate`)으로 실패했다. 실제 원인은 `ReservationConcurrencyTest` 쪽에서만 드러난 `NoSuchBeanDefinitionException`(`RestClient$Builder`)이었고, 스프링 테스트 컨텍스트 캐시가 그 실패를 캐싱해 같은 컨텍스트를 쓰는 다른 모든 테스트로 번졌다.
- **원인**: 이 프로젝트 환경에서 `RestClient.Builder` 빈이 자동 등록되지 않는다(`RestClientAutoConfiguration` 미동작 — 정확한 이유는 확인하지 못함).
- **해결**: 빈 주입 대신 `ChatLlmConfig.geminiRestClient()` 안에서 `RestClient.builder()`를 직접 호출하도록 변경. 타임아웃(`SimpleClientHttpRequestFactory.setConnectTimeout`/`setReadTimeout`)은 그대로 직접 설정.
- **재발 방지**: 이 환경에서 `RestClient.Builder`를 주입받는 새 코드를 짤 땐 먼저 `RestClient.builder()` 직접 호출로 되는지부터 확인한다. 스프링 컨텍스트를 쓰는 새 빈을 추가한 뒤에는 그 기능의 테스트만이 아니라 전체 테스트(`.\gradlew test`)를 한 번 돌려봐야 이런 연쇄 실패를 바로 잡을 수 있다.

---

## 12. 실제 키로 호출 시 503(UNAVAILABLE, high demand) — 원인 로그로 구글 서버 문제임을 분리 (챗봇)

- **증상**: 실제 `GEMINI_API_KEY`로 `/api/chat/recommend`를 호출하면 약 8초 후 503 CHAT_UNAVAILABLE.
- **1차 조사**: 처음엔 `GeminiLlmClient`가 예외를 `BusinessException`으로 감싸기만 하고 로그를 남기지 않아 애플리케이션 로그만으로는 원인을 알 수 없었다 → 실패 지점(HTTP 에러/타임아웃/파싱 단계/도구 라운드)마다 `log.warn`을 추가(모델명, 상태코드+응답 본문, 도구 라운드 번호 등. API 키·요청 헤더·사용자 메시지 원문은 남기지 않음).
- **분리**: 추가한 로그로 Gemini가 응답 본문에 직접 503 UNAVAILABLE(high demand)을 돌려주는 걸 확인한 뒤, 애플리케이션을 거치지 않고 PowerShell에서 같은 엔드포인트로 직접 호출해도 같은 503이 나는 것으로 우리 코드 문제가 아니라 구글 서버 쪽 일시적 과부하임을 확인했다.
- **대응**: 429(무료 티어 한도)와 동일하게 503도 1초 대기 후 1회 재시도, 그래도 실패하면 CHAT_BUSY로 매핑(둘 다 "일시적으로 붐빔" 신호로 취급).
