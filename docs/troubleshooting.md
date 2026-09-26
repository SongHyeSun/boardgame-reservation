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
