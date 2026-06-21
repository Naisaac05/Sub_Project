---
type: spec
category: inference
status: active
updated: 2026-06-18
description: "AI 시스템 후보 승인 워크플로우 페이즈(Phase) 연동 상세 설계서"

---

# AI 후보 승인 워크플로 단계 설계

## 목표

기존 `AiReviewCandidateStatus` 결정 모델을 깨지 않고 후보 승인 워크플로를 명시적으로 표현합니다.

## 설계

`AiReviewCandidateStatus`는 영속적인 결정 상태(`PENDING`, `APPROVED`, `REJECTED`, `MERGED`)로 유지합니다. 워크플로 상태로 `AiReviewCandidateWorkflowPhase`(`CAPTURED`, `DRAFTED`, `HUMAN_REVIEW`, `APPROVED`, `REJECTED`, `MERGED`)를 추가합니다.

런타임 수집 시 초안이 없으면 `CAPTURED`, `definitionDraft`가 있으면 `DRAFTED`로 매핑합니다. 검토자는 `START_REVIEW`로 대기 중인 후보를 `HUMAN_REVIEW`로 이동할 수 있습니다. 최종 검토 작업은 두 상태 축을 모두 갱신합니다. 승인은 `status=APPROVED`, `workflowPhase=APPROVED`로 설정하고, 거부는 `REJECTED`, 병합은 `MERGED`로 설정합니다.

## 가드레일

`APPROVED` 후보만 지식 재색인을 실행합니다. 수집·초안·사람 검토 단계의 후보는 후보 테이블에 머물며 승인 전에 `AiReviewKnowledgeReindexer`를 통해 승격되지 않으므로 검색 대상에서 제외됩니다.

## 테스트

서비스 테스트는 수집 단계 매핑, 검토 시작 시 검토자·근거 기록, 최종 단계 전환, 승인되지 않은 후보에 적용되는 기존 재색인 가드를 다룹니다.
