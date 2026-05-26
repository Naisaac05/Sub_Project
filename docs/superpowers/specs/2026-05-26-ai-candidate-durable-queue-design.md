# AI Candidate Durable Queue Design

## Goal

Move runtime AI review candidate capture away from implicit JSONL writes and toward the existing Spring DB-backed candidate queue, while keeping JSONL available only as an explicit dev fallback.

## Architecture

Spring already owns durable candidate state through `AiReviewCandidate`, `AiReviewCandidateAudit`, and the V2 approval workflow. Add an internal capture API that accepts Python auto-candidate payloads and stores them as `source=AUTO`, `status=PENDING`. Python candidate capture will prefer this HTTP sink and will only append JSONL when `AI_REVIEW_CANDIDATE_SINK=jsonl` is explicitly set.

## Data Flow

1. Python workflow builds the same auto-candidate payload it currently writes to JSONL.
2. `candidate_save_node()` sends the payload to Spring when `AI_REVIEW_CANDIDATE_SINK=http`.
3. Spring deduplicates by `external_candidate_id`, updates blank drafts where possible, and logs backlog metrics.
4. Candidate approval continues through the existing V2 admin API.

## Error Handling

Candidate capture remains outside the answer path. HTTP ingest failures, duplicate conflicts, JSONL write failures, or disabled capture all return the normal answer response with `candidate_capture_failed` or `candidate_capture_disabled` quality flags.

## Testing

Use TDD for Spring ingest service/controller behavior and Python sink selection. Regression coverage must prove JSONL is not used by default, explicit JSONL mode still works for dev, duplicate DB ingest is idempotent, and capture failures do not break answer generation.
