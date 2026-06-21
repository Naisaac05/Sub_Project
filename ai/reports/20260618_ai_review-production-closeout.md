---
type: report
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 프로덕션 클로즈아웃(Closeout) 체크리스트 완료 보고서"

---

# AI Review Production Closeout Checklist

## Candidate Approval V2

- Admin UI uses `/api/admin/ai-review/candidates/v2`.
- Use `Import JSONL` once per environment to backfill `course_concepts.jsonl` and `auto_candidates.jsonl`.
- Keep v1 JSONL routes available until all pending JSONL rows have been imported and reviewed.

## DB Schema Review

Hibernate `ddl-auto=update` can create the new tables locally, but production should review explicit DDL before rollout:

- `ai_review_candidates`
- `ai_review_candidate_audits`
- indexes on status, external candidate id, term/category, and audit candidate/timestamp
- retention fields: `retention_until`, `reviewed_at`, `reviewer`

## LangGraph Verification

Run in the AI environment after installing optional dependencies:

```powershell
C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m pip install -r ai\requirements-rag.txt
C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -c "from app.workflow.graph import LANGGRAPH_AVAILABLE, build_review_state_graph; print(LANGGRAPH_AVAILABLE); print(build_review_state_graph().compile())"
```

Expected: `LANGGRAPH_AVAILABLE` is `True` and graph compilation succeeds.

## Service Token Rollout

Set the same secret on both services:

- Spring/FastAPI shared env: `AI_REVIEW_SERVICE_TOKEN=<long-random-secret>`
- Spring property path: `app.ai-review.python.service-token`
- FastAPI checks header: `X-AI-Service-Token`

Rotate by deploying FastAPI to accept the new env value, then Spring with the same value.

## Build And Test Commands

```powershell
cd ai
C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_service_helpers tests.test_observability tests.test_workflow_runner -v

cd ..\backend
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest

cd ..\frontend
npm.cmd run build
```

## Log And Metric Collection

Collect structured FastAPI logs from logger `ai_review.observability`.

Fields to index:

- `correlation_id`
- `route`
- `model_used`
- `fallback_used`
- `cache_hit`
- `llm_call_avoided`
- `retrieval_miss`
- `candidate_captured`
- `candidate_id`

Spring logs to collect:

- `ai_review.python_event`
- `ai_review.candidate_backlog`

Initial dashboard counters:

- fallback count by route
- cache hit rate
- LLM call avoided rate
- retrieval miss count
- captured candidate count
- pending candidate backlog
- admission in-flight / available slots

## Next Spikes

See [AI Review Streaming, Evaluation, and Summary Spikes](superpowers/specs/2026-05-21-ai-review-streaming-evaluation-summary-spikes.md).

- Add a separate streaming endpoint before replacing the current synchronous review endpoints.
- Keep rule-based evaluation and Spring Markdown summary builders as fallbacks while testing semantic/LLM evaluation and Python summary generation.
- Measure first-token latency, fallback rate, and golden dataset regression before enabling these paths by default.
