# AI Review Candidate Approval Workflow

> 작성일: 2026-05-26
> 범위: P2 candidate approval workflow minimum

## Two-Axis State Model

`AiReviewCandidateStatus` remains the durable decision state:

- `PENDING`: no final human decision yet
- `APPROVED`: approved for knowledge promotion/reindex
- `REJECTED`: rejected by reviewer
- `MERGED`: merged into another candidate

`AiReviewCandidateWorkflowPhase` records where the candidate is in the review workflow:

- `CAPTURED`: captured from runtime, no usable draft yet
- `DRAFTED`: captured/imported with a draft answer
- `HUMAN_REVIEW`: reviewer has taken the candidate into review
- `APPROVED`: reviewer approved it
- `REJECTED`: reviewer rejected it
- `MERGED`: reviewer merged it into another candidate

## Transition Rules

- Runtime capture with blank `definitionDraft`: `status=PENDING`, `workflowPhase=CAPTURED`
- Runtime capture with nonblank `definitionDraft`: `status=PENDING`, `workflowPhase=DRAFTED`
- Duplicate capture that fills a blank draft: `workflowPhase=DRAFTED`
- `START_REVIEW`: keeps `status=PENDING`, sets `workflowPhase=HUMAN_REVIEW`, records reviewer and timestamp
- `APPROVE`: sets `status=APPROVED`, `workflowPhase=APPROVED`
- `REJECT`: sets `status=REJECTED`, `workflowPhase=REJECTED`
- `MERGE`: sets `status=MERGED`, `workflowPhase=MERGED`

## Reviewer And Evidence Fields

Reviewer evidence is stored in the existing candidate and audit fields:

- `reviewer`
- `reviewedAt`
- `definition` or `reviewerEditedAnswer`
- `rejectedReason`
- `mergedIntoId`
- `AiReviewCandidateAudit.reason`
- `AiReviewCandidateAudit.reviewerEditedAnswer`

## Retrieval Exclusion Guarantee

Captured, drafted, and human-review candidates remain outside retrieval because they are stored in the candidate table only. `AiReviewKnowledgeReindexer.reindexChanged()` is called only after `status=APPROVED`. Rejected and merged candidates are not reindexed.

## Verification

```powershell
cd C:\Users\User\Desktop\Sub_Project\backend
.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest
```
