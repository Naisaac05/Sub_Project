---
type: report
category: rag
status: active
updated: 2026-06-23
description: "RAG 카드 증진 작업 커밋 전 변경 묶음 제안"
---

# 커밋 전 변경 묶음 제안

현재 브랜치: `isc/feature/ai_RAG_plus`

커밋은 아직 만들지 않았다. 변경량이 크므로 아래처럼 나눠 stage하는 것을 권장한다.

## 1. RAG 카드 기준선과 next20 승인

목적: 카드·체크섬 기준선, 코스별 후보 20장 선정, 승인 82장 달성.

포함 범위:

- `ai/app/knowledge/concepts_v2/**/*.json`
- `ai/app/knowledge/concepts_v2_backups/rag-card-next20-20260621/`
- `ai/app/scripts/capture_rag_card_baseline.py`
- `ai/app/scripts/review_rag_card_next20.py`
- `ai/reports/rag_card_*2026-06-21.json`
- `ai/reports/course_balanced_next20_candidates_2026-06-21.json`
- `ai/evals/rag_card_expansion_gaps_2026-06-21.jsonl`
- 관련 테스트: `ai/tests/test_capture_rag_card_baseline.py`, `ai/tests/test_review_rag_card_next20.py`, `ai/tests/test_rag_card_expansion_candidates.py`

예상 메시지:

```text
feat(ai): expand approved rag cards and capture baseline
```

## 2. 검색/평가 품질 4.5 달성

목적: payload 품질 개선, Java equals/React key 검색 공백 보완, 50문항 Shadow 품질 4.52 달성.

포함 범위:

- `ai/app/rag/documents.py`
- `ai/app/rag/retriever.py`
- `ai/retrieval_eval.py`
- `ai/scripts/audit_rag_cards_v2.py`
- `ai/scripts/evaluate_v2_approved_ollama_e2e.py`
- `ai/tests/test_rag_documents.py`
- `ai/tests/test_rag_retriever.py`
- `ai/tests/test_audit_rag_cards_v2.py`
- `ai/tests/test_course_question_shadow.py`
- `ai/reports/v2_approved_ollama_e2e_2026-06-21.json`
- `ai/reports/v2_approved_ollama_e2e_2026-06-22.json`

예상 메시지:

```text
test(ai): raise approved rag shadow quality baseline
```

## 3. 근거 기반 fallback 안전 생성

목적: 승인 근거 기반 fallback 생성, 품질 게이트, 실패 시 안전 응답, sync/stream 경로 보존.

포함 범위:

- `ai/app/workflow/grounded_fallback.py`
- `ai/app/workflow/nodes.py`
- `ai/app/workflow/runner.py`
- `ai/scripts/evaluate_grounded_fallback_live.py`
- `ai/tests/test_grounded_fallback.py`
- `ai/tests/test_grounded_fallback_live_evaluation.py`
- `ai/tests/test_v2_approved_fast_path.py`
- `ai/reports/grounded_fallback_live_2026-06-22.json`
- `ai/reports/grounded_fallback_live_2026-06-23.json`
- `ai/specs/20260622_grounded-fallback-safety-design.md`
- `ai/plans/20260622_grounded-fallback-safety.md`

예상 메시지:

```text
feat(ai): add grounded fallback safety gate
```

## 4. Shadow 전환 기준과 운영 검증 준비

목적: 운영 Shadow 케이스 확대, fallback 성공 기준 문서화, 전환 기준 재판정, 운영 검증 스크립트 준비.

포함 범위:

- `ai/scripts/evaluate_operational_shadow.py`
- `ai/scripts/extract_operational_missing_candidates.py`
- `ai/tests/test_operational_shadow_verification.py`
- `ai/tests/test_operational_missing_candidates.py`
- `ai/evals/operational_missing_repeated_fixture_2026-06-23.json`
- `ai/evals/operational_shadow_sample_2026-06-23.jsonl`
- `ai/reports/operational_missing_candidate_extraction_2026-06-23.md`
- `ai/reports/operational_missing_candidates_dryrun_2026-06-23.jsonl`
- `ai/reports/operational_missing_candidates_fixture_2026-06-23.jsonl`
- `ai/specs/20260623_grounded-fallback-transition-criteria.md`
- `ai/reports/grounded_fallback_transition_readiness_2026-06-23.md`
- `ai/reports/operational_shadow_runbook_2026-06-23.md`
- `ai/reports/precommit_change_sets_2026-06-23.md`
- `ai/reports/index.md`
- `ai/specs/index.md`
- `ai/README.md`

예상 메시지:

```text
docs(ai): define grounded fallback shadow transition gates
```

## 5. 에러 기록

목적: 이번 작업 중 원인까지 특정한 회귀/버그 기록.

포함 범위:

- `error/2026-06-21-*.md`
- `error/2026-06-22-*.md`
- `error/2026-06-23-grounded-retry-success-overwritten-by-template.md`
- `error/2026-06-23-grounded-generation-overwritten-by-fallback-node.md`
- `error/README.md`

예상 메시지:

```text
docs(error): record rag fallback validation regressions
```

## 커밋 전 검증 명령

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m unittest tests.test_grounded_fallback tests.test_grounded_fallback_live_evaluation tests.test_operational_shadow_verification tests.test_v2_approved_fast_path tests.test_workflow_runner tests.test_course_question_shadow tests.test_synthetic_shadow_traffic tests.test_parallel_rag_config -v
.\.venv\Scripts\python.exe scripts\lint_knowledge_cards.py
```

루트에서:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
```
