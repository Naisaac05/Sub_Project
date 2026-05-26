# AI Review Candidate Durable Queue

> 작성일: 2026-05-26
> 범위: P2 candidate queue DB/durable queue migration

## Runtime Capture Path

- Default Python sink: `AI_REVIEW_CANDIDATE_SINK=http`
- Spring ingest endpoint: `POST /api/internal/ai-review/candidates/capture`
- Python payload fields are mapped from the existing auto-candidate shape:
  - `candidate_id` → `candidateId`
  - `definition_draft` → `definitionDraft`
  - `source_question` → `sourceQuestion`
  - `resolved_query` → `resolvedQuery`
  - `needs_review_reason` → `needsReviewReason`
- Optional Spring URL override: `AI_REVIEW_CANDIDATE_CAPTURE_URL`
- Optional timeout override: `AI_REVIEW_CANDIDATE_CAPTURE_TIMEOUT_SECONDS`

## JSONL Dev Mode

JSONL runtime capture is no longer the implicit default. Use it only for local development:

```powershell
$env:AI_REVIEW_CANDIDATE_SINK="jsonl"
$env:AI_REVIEW_AUTO_CANDIDATES_PATH="C:\tmp\auto_candidates.jsonl"
```

`AI_REVIEW_CANDIDATE_SINK=off` disables the HTTP sink path. `AI_REVIEW_NO_CANDIDATE_CAPTURE=true` remains the stronger kill switch at workflow level.

## Candidate Lifecycle

- `PENDING`: captured runtime candidate waiting for human review
- `APPROVED`: reviewer-approved candidate eligible for knowledge promotion/reindex
- `REJECTED`: reviewer rejected candidate
- `MERGED`: reviewer merged duplicate candidate into another candidate

Runtime capture always creates or updates `source=AUTO`, `status=PENDING`. Approval still happens through the V2 admin candidate workflow.

## Audit Trail

Capture writes an `AiReviewCandidateAudit` row with `action=CAPTURE`, `previousStatus=PENDING`, `nextStatus=PENDING`, `reviewer=python-runtime`, and a reason of `captured` or `duplicate_draft_updated`. Human review transitions continue to write approval/rejection/merge audit rows.

## Failure Behavior

Candidate capture is still outside the learner answer path. HTTP ingest failure, timeout, or JSONL write failure leaves the answer response intact and adds `candidate_capture_failed` to the workflow quality flags.

## Verification

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest --tests com.devmatch.config.AiReviewPropertiesTest

cd C:\Users\User\Desktop\Sub_Project\ai
.\.venv\Scripts\python.exe -m pytest tests\test_workflow_runner.py tests\test_auto_candidates.py tests\test_observability.py -q
```
