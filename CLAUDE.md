# 보드게임 예약·파티 모집 시스템 (백엔드)

## 설계 기준
- docs/design.md 가 기준 문서. 작업과 관련된 섹션만 읽을 것 (전체 탐색 금지)
  - 2장 ERD / 3장 API 명세 / 4장 동시성 / 5장 패키지 구조 / 6장 구현 순서

## 스택
Java 17, Spring Boot 4.x, Gradle, Spring Security(세션 기반), Spring Data JPA, PostgreSQL, Redis, Lombok

## 코드 규칙
- 패키지: com.boardgame.reservation / 도메인형 (global, member, boardgame, party, chat)
- 응답: ApiResponse { success, data, message }
- 예외: BusinessException(ErrorCode) → GlobalExceptionHandler
- 엔티티 직접 반환 금지, DTO는 record
- 엔티티 생성은 정적 팩토리 메서드, 기본 생성자는 PROTECTED
- 기능마다 테스트 작성

## 작업 방식
- 요청받은 범위만 수정. 관련 없는 파일 리팩터링 금지
- 설명은 짧게, 코드 위주
- 테스트: .\gradlew test  (Windows PowerShell)
- git 커밋/브랜치는 내가 직접 함. 요청 전엔 git 명령 실행 금지

## 프론트엔드
- 위치: `frontend/`. 명령은 `frontend/`에서 실행 (`npm run dev | build | lint`). 설계는 docs/frontend-plan.md
- 스택(설치 버전): React 19.3.0, Vite 8.3.1, TypeScript 6.0.3, react-router 8.4.0, @tanstack/react-query 5.103.2, axios 1.20.0, Tailwind CSS 4.3.3
- 린터: oxlint 1.85.0 (Vite 템플릿 기본, ESLint 아님) 그대로 사용
- TS strict 유지, `any` 사용 금지
- 백엔드 코드 수정 금지 (필요해 보이면 구현 전에 보고)
- API 필드는 백엔드 dto / http/*.http 기준, 추측 금지
- 서버 상태는 TanStack Query, 직접 useEffect+fetch 금지
- dev 서버는 사용자가 직접 실행 (Claude Code가 띄우지 않음)
- 테스트: 프론트는 이번 스코프에서 테스트 작성 제외. 검증은 `npm run lint` + `npm run build` (docs/frontend-plan.md 6장)
