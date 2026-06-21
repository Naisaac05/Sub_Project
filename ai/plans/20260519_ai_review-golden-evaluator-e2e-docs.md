---
type: plan
category: evaluation
status: active
updated: 2026-06-18
description: "골든 평가기, 프로모션 E2E 검증 및 파이프라인 문서화 계획"

---

# Phase 22: golden evaluator, promotion E2E, and pipeline docs

## Goal

Close the explainability and verification gaps after the hybrid retrieval and approval work:

- expand the golden dataset from the MVP floor into a broader regression set
- make the evaluator check operational workflow signals, not retrieval only
- prove candidate promotion can feed changed-only reindex end to end
- document runtime profiles and pipeline input/output flow
- finish with full build/test evidence

## Checklist

- [x] Add failing tests for 50-row golden dataset and evaluator ops metrics
- [x] Add failing/green E2E test for candidate promotion -> changed-only reindex
- [x] Expand `ai/evals/golden_dataset.jsonl` to 50 rows
- [x] Add evaluator metrics for fallback expectation, candidate capture, and observability events
- [x] Document local/high-performance/runtime env profiles
- [x] Document AI review pipeline and input/output contracts
- [x] Run final Python/backend/frontend verification
