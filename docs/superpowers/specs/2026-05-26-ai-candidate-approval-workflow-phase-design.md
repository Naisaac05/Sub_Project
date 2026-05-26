# AI Candidate Approval Workflow Phase Design

## Goal

Represent the candidate approval workflow explicitly without breaking the existing `AiReviewCandidateStatus` decision model.

## Design

Keep `AiReviewCandidateStatus` as the durable decision state: `PENDING`, `APPROVED`, `REJECTED`, `MERGED`. Add `AiReviewCandidateWorkflowPhase` as the workflow state: `CAPTURED`, `DRAFTED`, `HUMAN_REVIEW`, `APPROVED`, `REJECTED`, `MERGED`.

Runtime capture maps to `CAPTURED` when no draft exists and `DRAFTED` when `definitionDraft` exists. A reviewer can move a pending candidate into `HUMAN_REVIEW` with `START_REVIEW`. Final review actions update both axes: approve sets `status=APPROVED`, `workflowPhase=APPROVED`; reject sets `REJECTED`; merge sets `MERGED`.

## Guardrails

Only `APPROVED` candidates trigger knowledge reindexing. Captured, drafted, and human-review candidates remain excluded from retrieval because they live in the candidate table and are never promoted through `AiReviewKnowledgeReindexer` before approval.

## Testing

Service tests cover capture phase mapping, start-review reviewer/evidence recording, final phase transitions, and the existing reindex guard for non-approved candidates.
