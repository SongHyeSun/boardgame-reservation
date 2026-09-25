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
