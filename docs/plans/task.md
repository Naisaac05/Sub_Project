# 3차 AI 스트리밍 운영 하드닝 태스크 체크리스트

| ID | 작업 명 | 설명 | 상태 |
| :--- | :--- | :--- | :---: |
| 1 | 데이터 정합성 검사 | 기존 데이터 중 중복 client_request_id 유무를 선제 검사하고 보고 (현재 테스트 샌드박스 환경에서는 DB 커넥션 획득 불가로 "확인 불가"로 기록) | **완료** |
| 2 | DB Unique Constraint 적용 | `ai_review_messages` 테이블에 `UNIQUE(session_id, client_request_id)` 복합 제약 선언 및 DDL/마이그레이션 SQL 문서 가이드 추가 | **완료** |
| 3 | 예외 복구(DataIntegrityViolationException) | DB 중복 인입 시 500 오류 방지 및 기존 메시지 DTO 복구 응답 구현 및 테스트 통과 | **완료** |
| 4 | Reactor MDC 브릿지 유틸리티 | 비동기 스레드 간 traceId/requestId/clientRequestId를 전파하고 cleanup하는 브릿지 구현 | **완료** |
| 5 | X-Correlation-Id 헤더 연동 | Spring ↔ Python 통신 시 X-Correlation-Id 전송 및 파이썬 로깅 연동 | **완료** |
| 6 | 역할 구분 문서화 | requestId / traceId / clientRequestId 식별자 정의 기록 | **완료** |
| 7 | 최종 검증 테스트 | 멱등성 및 예외 복구 관련 백엔드/파이썬 통합 테스트 실행 및 무결함 확인 | **완료** |
