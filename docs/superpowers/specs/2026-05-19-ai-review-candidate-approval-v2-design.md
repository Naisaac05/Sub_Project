# AI Review Candidate Approval V2 Design

## Goal

Move AI review candidate approval beyond JSONL-only review into a database-backed admin model with explicit lifecycle states, reviewer edits, audit history, and retention metadata.

## Scope

Phase 19 adds a v2 Spring Boot API and JPA model while leaving the existing JSONL v1 API intact. Python auto-candidates can continue writing JSONL for now; DB import/backfill allows admins to migrate JSONL queues into the v2 model. Phase 20 will add operational logging/metrics, and Phase 21 will secure the Spring/FastAPI boundary.

## Candidate Lifecycle

`AiReviewCandidate` uses these statuses:

- `PENDING`: awaiting human review
- `APPROVED`: approved as-is or with reviewer edits
- `REJECTED`: rejected with a reason
- `MERGED`: resolved into another candidate/concept

Review actions are:

- `APPROVE`: accept the existing definition or provided definition
- `EDIT_AND_APPROVE`: approve using `reviewerEditedAnswer`
- `REJECT`: reject with `rejectedReason`
- `MERGE`: mark as merged into `mergedIntoId`

## Data Model

`AiReviewCandidate` stores source metadata, term/category, definition fields, review fields, retention deadline, and timestamps. `AiReviewCandidateAudit` stores every transition with previous/next status, action, reviewer, edited answer snapshot, reason, and timestamp.

## API

Add v2 routes under `/api/admin/ai-review/candidates/v2`:

- `GET /api/admin/ai-review/candidates/v2`: list DB-backed candidates
- `POST /api/admin/ai-review/candidates/v2/import-jsonl`: import current v1 JSONL rows, deduplicating by external candidate id or term/category
- `PATCH /api/admin/ai-review/candidates/v2/{id}/review`: apply `APPROVE`, `EDIT_AND_APPROVE`, `REJECT`, or `MERGE`

The existing `/api/admin/ai-review/candidates` JSONL API remains available during migration.

## Testing

Unit tests use mocked repositories to verify status transitions, audit creation, reviewer-edited answer persistence, merge metadata, and JSONL import deduplication. Existing JSONL tests continue to prove backward compatibility.
