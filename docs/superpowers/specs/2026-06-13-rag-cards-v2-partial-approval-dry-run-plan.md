# RAG Cards v2 Partial Approval Dry-Run Plan

## Status

This document prepares a future partial approval. It does not authorize or perform approval or activation.

- All five target cards remain `draft`.
- All target payload statuses remain `draft`.
- `ACTIVE_CARD_STORE` remains unchanged.
- v1 concepts are not modified.

## Approval Target

Only these five cards are in scope:

| Card ID | Current Card Status | Candidate Evidence |
| --- | --- | --- |
| `frontend-react-key` | `draft` | Candidate passed; React key Top1; three-intent Shadow Fast Path passed |
| `java-equals` | `draft` | Candidate passed; Java equals Top1; three-intent Shadow Fast Path passed |
| `spring-spring-question-59` | `draft` | Candidate passed; Spring cache Top1; three-intent Shadow Fast Path passed |
| `java-extends` | `draft` | Candidate passed; exact-term Top1 calibration passed |
| `python-with` | `draft` | Candidate passed; exact-term Top1 calibration passed |

No other card may be changed by the future approval operation.

## Planned Payload Approval

For each target card, only these generated payload statuses are planned for approval:

- `CONCEPT_DEFINITION`
- `ANSWER_REASON`
- `WRONG_ANSWER_REASON`

The future explicitly authorized approval operation would:

1. Change `review.card_status` from `draft` to `approved`.
2. Change only the three listed entries in `review.payload_status` from `draft` to `approved`.
3. Set a timezone-aware `review.approved_at` and an explicit reviewer identifier.
4. Leave lazy payloads null and unapproved:
   - `COMPARISON`
   - `EXAMPLE_REQUEST`
   - `PRACTICAL_USAGE`
   - `DEBUG_OR_ERROR`

This dry-run does not make any of those changes.

## Expected Fast Path Coverage

After a separately authorized partial approval and before any v2 activation, Shadow validation should confirm:

| Card | Expected Fast Path Questions |
| --- | --- |
| `frontend-react-key` | React key definition, correct-answer reason, wrong-answer reason |
| `java-equals` | Java equals definition, correct-answer reason, wrong-answer reason |
| `spring-spring-question-59` | Spring cache definition, correct-answer reason, wrong-answer reason |
| `java-extends` | Java `extends` definition and answer-reason questions routed to the card |
| `python-with` | Python `with` definition and answer-reason questions routed to the card |

The Phase 4 Shadow baseline is 15/15 Fast Path success, 0% fallback, and no target Top1 misretrievals.

## Future Approval Dry-Run Procedure

The future approval tool must run in preview mode first and produce a patch/report without modifying cards.

1. Resolve exactly the five target JSON paths under `ai/app/knowledge/concepts_v2`.
2. Reject execution if any target is missing, already approved, invalid JSON, or has a missing generated payload.
3. Reject execution if any non-target card appears in the proposed diff.
4. Produce before/after values for `card_status`, three payload statuses, `approved_at`, and reviewer.
5. Verify lazy payloads and all retrieval fields are unchanged.
6. Run v2 lint and the same Shadow sample set against the proposed in-memory state.
7. Confirm `ACTIVE_CARD_STORE` is still v1 or unset and no v1 file appears in the diff.
8. Stop after printing the dry-run report.

No write-capable approval command may run without a separate explicit approval.

## Rollback Plan

Before any future approved write:

1. Create a timestamped backup containing only the five original draft JSON files.
2. Record SHA-256 hashes for the five originals and the full v1 concepts tree.
3. Apply approval through a staging directory and atomically replace only the five target files.
4. If JSON validation, lint, Shadow validation, or file-scope verification fails, atomically restore all five files from the backup.
5. Verify restored hashes match the pre-approval manifest.
6. Keep `ACTIVE_CARD_STORE` on v1 throughout rollback.

Rollback is all-or-nothing for the five-card approval batch. Partial rollback is not allowed.

## Final Pre-Activation Checklist

- [ ] Separate explicit approval for the five-card status change exists.
- [ ] Proposed diff contains exactly five v2 card files.
- [ ] No v1 concepts file is changed.
- [ ] No non-target v2 card is changed.
- [ ] Only `card_status`, the three generated payload statuses, `approved_at`, and reviewer change.
- [ ] Lazy payload statuses remain unapproved.
- [ ] Backup and SHA-256 manifest exist.
- [ ] JSON validation reports zero invalid files and duplicate IDs.
- [ ] v2 lint reports zero errors.
- [ ] Retrieval calibration tests pass.
- [ ] React key, Java equals, Spring cache, `extends`, and `with` remain Top1.
- [ ] Partial-approval Shadow Fast Path passes for all planned target questions.
- [ ] Workflow regression has no new failures beyond the six existing v1-corpus-missing failures.
- [ ] `ACTIVE_CARD_STORE` remains v1 or unset.
- [ ] Activation has a separate approval, canary plan, monitoring criteria, and rollback trigger.
- [ ] No commit or push is performed without separate approval.

## Activation Gate

Partial approval readiness: **PREPARED**

Production activation readiness is intentionally not decided by this plan. Approval and activation remain separate future operations.
