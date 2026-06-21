---
type: troubleshooting
category: rag
status: active
updated: 2026-06-18
description: "RAG v2 intent regression changed the existing follow-up context behavior 발생 원..."

---

# RAG v2 intent regression changed the existing follow-up context behavior

- 발생 일시: 2026-06-12
- 영역: ai / workflow
- 심각도: medium

## 증상

v2 마이그레이션 작업 중 FOLLOW_UP 정책을 active context 재사용으로 해석해 `retrieve_context_node()`가 기존 context를 보존하도록 변경했다. 기존 v1 workflow는 follow-up 진입 시 RAG context를 비우는 동작과 회귀 테스트를 갖고 있어 운영 동작이 달라졌다.

## 원인

v2 카드 정책과 기존 v1 workflow 동작을 같은 변경 범위로 취급했다. v2가 아직 활성화되지 않은 상태에서는 v1 runtime 동작을 유지해야 하지만, follow-up runtime 분기까지 변경했다.

## 해결 방법

`ai/app/workflow/nodes.py:46`의 기존 follow-up context 초기화 동작을 복원했다. v2 migration은 계속 dry-run 전용으로 유지하고 runtime 활성화와 분리했다.

## 재발 방지 / 메모

v2 미활성 단계에서는 migration, schema, 평가 코드만 변경한다. intent 정책이 기존 runtime 동작과 충돌하면 활성화 승인 전에는 기존 v1 동작을 우선한다.

