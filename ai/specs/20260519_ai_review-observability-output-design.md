---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 시스템 운영 상태 파악을 위한 관측성(Observability) 출력 설계서"

---

# AI 리뷰 관측성 출력 설계

## 목표

기존 AI 리뷰의 구조화된 응답 이벤트를 상관관계 ID와 경량 운영 지표가 포함된 실제 FastAPI·Spring Boot 로그 출력으로 전환합니다.

## 아키텍처

FastAPI는 `X-Correlation-ID`를 받고, 값이 없으면 새로 생성합니다. 이를 모든 `observability_events` 항목에 첨부하고 이벤트마다 JSON 로그 한 줄을 출력하며 HTTP 응답 헤더에도 돌려줍니다. Spring Boot는 FastAPI를 호출할 때 같은 헤더를 전송하고 반환된 이벤트 요약을 기록합니다.

## 로그 지표

로그 레코드에는 로그 백엔드가 수집할 수 있는 불리언·개수 필드가 포함됩니다.

- `fallback_used`
- `retrieval_miss`
- `candidate_captured`
- `candidate_backlog_pending`

Phase 20은 로그 기반으로 유지하며 메트릭 레지스트리 의존성은 추가하지 않습니다.

## 범위

이 단계에서는 대시보드나 외부 싱크를 추가하지 않습니다. 이후 인프라가 사용할 수 있도록 안정적인 구조화 로그 페이로드와 상관관계 ID 전파 기능을 만듭니다.
