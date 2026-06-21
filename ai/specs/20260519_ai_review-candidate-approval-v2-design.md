---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 후보 승인 시스템 v2 아키텍처 및 워크플로 설계"
---

# AI 리뷰 후보 승인 V2 설계

## 목표

JSONL만을 사용하는 AI 리뷰 후보 승인을 넘어 데이터베이스 기반의 관리자 모델로 확장하여 명확한 수명 주기 상태, 리뷰어 수정 사항, 감사 기록, 보관 메타데이터를 도입합니다.

## 범위

Phase 19에서는 v2 Spring Boot API와 JPA 모델을 추가하며 기존 JSONL v1 API는 그대로 유지됩니다. Python 자동 후보 생성은 현재 JSONL로 계속 작성할 수 있으며, DB 가져오기/기존 데이터 채우기(backfill) 기능을 통해 관리자가 JSONL 큐를 v2 모델로 마이그레이션할 수 있습니다. Phase 20에서는 운영 로깅 및 메트릭을 추가하고, Phase 21에서는 Spring/FastAPI 경계를 보호합니다.

## 후보 수명 주기

`AiReviewCandidate`는 다음 상태를 사용합니다:

- `PENDING`: 인간 검토 대기 중
- `APPROVED`: 기존 정의 또는 리뷰어 수정 사항을 반영하여 승인됨
- `REJECTED`: 이유와 함께 거부됨
- `MERGED`: 다른 후보나 개념으로 통합됨

리뷰 작업은 다음과 같습니다:

- `APPROVE`: 기존 정의 또는 제공된 정의를 수락함
- `EDIT_AND_APPROVE`: `reviewerEditedAnswer`를 사용하여 승인함
- `REJECT`: `rejectedReason`로 거부함
- `MERGE`: `mergedIntoId`로 병합됨

## 데이터 모델

`AiReviewCandidate`는 출처 메타데이터, 용어/카테고리, 정의 필드, 리뷰 필드, 보관 기한, 타임스탬프 등을 저장합니다. `AiReviewCandidateAudit`는 각 상태 전환을 저장하며 이전·다음 상태, 작업, 리뷰어, 편집된 답변 스냅샷, 이유, 타임스탬프를 포함합니다.

## API

`/api/admin/ai-review/candidates/v2` 경로 아래에 v2 라우트를 추가합니다:

- `GET /api/admin/ai-review/candidates/v2`: 데이터베이스 기반 후보 목록 조회
- `POST /api/admin/ai-review/candidates/v2/import-jsonl`: 현재 v1 JSONL 행들을 가져와 외부 후보 ID 또는 용어/카테고리로 중복 제거하여 가져오기
- `PATCH /api/admin/ai-review/candidates/v2/{id}/review`: `APPROVE`, `EDIT_AND_APPROVE`, `REJECT`, `MERGE` 작업 적용

기존 `/api/admin/ai-review/candidates` JSONL API는 마이그레이션 중에도 계속 사용됩니다.

## 테스트

단위 테스트는 모의 리포지토리를 사용하여 상태 전환 검증, 감사 기록 생성, 리뷰어 수정 사항의 보존, 병합 메타데이터 저장, JSONL 중복 제거를 확인합니다. 기존 JSONL 테스트는 여전히 역방향 호환성을 증명합니다.
