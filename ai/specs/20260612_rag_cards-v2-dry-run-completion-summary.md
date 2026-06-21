---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "RAG Cards v2 전환 전 드라이런(Dry-Run) 완료 상태 및 명세 요약서"

---

# RAG Cards v2 Dry-Run Completion Summary

## Status

The isolated RAG cards v2 migration implementation is complete for dry-run use.

- `ai/app/knowledge/concepts_v2` remains empty.
- The migration rejects `--write`.
- `ACTIVE_CARD_STORE` was not introduced or changed.
- The existing `ai/app/knowledge/concepts` store was not modified by the v2 dry-run work.
- No Git commit or push was performed.

This status does not approve actual v2 card file generation or production activation.

## Implemented Behavior

The v2 migration reads `backend/data/devmatch-data-only.sql` and builds draft cards in memory from question content, options, zero-based correct answers, and test IDs.

The pipeline:

- excludes direct E2E failure questions;
- generates only `CONCEPT_DEFINITION`, `ANSWER_REASON`, and `WRONG_ANSWER_REASON`;
- marks every generated card and payload as `draft`;
- merges only within a category using normalized terms, alias overlap, or embedding similarity;
- treats `source_question_ids` as trace metadata only;
- builds compact retrieval text from the term, up to three aliases, category, and boost keywords;
- rejects `--write`;
- evaluates LOO by retrieving 50 candidates before removing the source card.

Intent regression coverage also verifies that `OFF_TOPIC` and `UNKNOWN` do not perform RAG searches. Existing v1 follow-up runtime behavior was preserved.

## Dry-Run Result

The approved 30-question dry-run produced the following in-memory result:

| Metric | Result |
| --- | ---: |
| Questions | 30 |
| Draft cards | 26 |
| Merges | 4 |
| Broad-term specializations | 0 |
| Draft payloads | 78 |
| Card ID collisions handled | 4 |
| Lint errors | 0 |
| Exact Match Hit@1, data-leakage baseline | 90.0% |
| LOO alternative candidate rate | 60.0% |
| LOO average score | 4.8333 |
| LOO same-category rate | 100.0% |

No files were written by this run.

## Workflow Regression Classification

The complete workflow suite currently reports 68 passes and 6 failures. These failures are classified as **v1 corpus missing** and are not v2 blockers because the v2 card store is empty and inactive.

| Failing Test | Classification | Cause |
| --- | --- | --- |
| `test_successful_generation_returns_metadata` | v1 corpus missing | Expected `spring-n-plus-one` card is absent. |
| `test_vague_clarification_filters_low_score_unrelated_context` | v1 corpus missing | Missing N+1 card causes `spring-jpa*` cards to rank instead. |
| `test_contextual_json_question_uses_generator_instead_of_static_definition` | v1 corpus missing | No qualifying v1 JSON context exists, so route is `generation`. |
| `test_typo_alias_question_uses_generated_card_fast_path` | v1 corpus missing | Expected `java-backend-controlleradvice` card is absent. |
| `test_generated_concept_card_question_uses_lightweight_fast_path` | v1 corpus missing | ControllerAdvice generated-card fast path cannot resolve the missing card. |
| `test_generated_answer_returns_rag_generation_route` | v1 corpus missing | Expected equals concept card is absent, so route is `generation`. |

The missing v1 cards are pre-existing working-tree changes. They were not restored or modified during the v2 dry-run work.

## Change Summary By File

### v2 Dry-Run Implementation

- `ai/app/scripts/migrate_rag_cards.py`
  - Implements the isolated, dry-run-only v2 migration, lint, reporting, and LOO evaluation.
- `ai/tests/test_migrate_rag_cards_v2.py`
  - Covers path isolation, rejected writes, SQL parsing, draft policy, merge constraints, card IDs, and LOO candidate removal.
- `docs/superpowers/specs/2026-06-12-rag-cards-v2-isolated-migration-design.md`
  - Records the approved isolation and safety design.
- `docs/superpowers/plans/2026-06-12-rag-cards-v2-isolated-migration.md`
  - Records the implementation and verification plan.

### Intent Policy And Regression Coverage

- `ai/app/workflow/embedding_intent.py`
  - Maps `OFF_TOPIC` to `no_rag` and `UNKNOWN` to fallback behavior.
- `ai/app/workflow/nodes.py`
  - Prevents RAG retrieval for `no_rag` and fallback policies while preserving existing v1 follow-up behavior.
- `ai/tests/test_intent_routing.py`
  - Verifies embedding-intent policy mappings.
- `ai/tests/test_workflow_runner.py`
  - Verifies workflow retrieval gating and existing behavior.

### Diagnosed Errors

- `error/2026-06-12-rag-v2-sql-options-escape.md`
  - Records the SQL dump option-JSON escape parsing defect and fix.
- `error/2026-06-12-rag-v2-follow-up-v1-context-regression.md`
  - Records and documents the reverted v1 follow-up behavior change.
- `error/README.md`
  - Adds both error records to the index.

## Git Diff Summary

The full working tree contains broad pre-existing AI, v1 corpus, vectorstore, evaluation, and test changes. The current tracked diff summary is 43 files, 897 insertions, and 914 deletions, excluding untracked files.

The v2 dry-run work is limited to the migration, intent gating, focused tests, design/plan/completion documentation, backup snapshot, and error records listed above.

Global `git diff --check` still fails on pre-existing trailing whitespace in:

- `ai/app/rag/retriever.py`
- `ai/tests/test_rag_documents.py`
- `ai/tests/test_rag_retriever.py`

Those files were not whitespace-cleaned because they are outside this task's scope.

## Verification Result

- v2, intent, retrieval, and document tests: 29 passed.
- workflow and degraded-mode tests: 68 passed, 6 v1-corpus-missing failures.
- v2 file count: 0.
- `ACTIVE_CARD_STORE`: unset.
- actual v2 write: not performed.
- Git commit and push: not performed.

## Next Work Candidates

### A. Restore Missing v1 Cards

Restore or intentionally replace the missing v1 cards and then rerun the six affected workflow tests. This work changes the active v1 corpus and must be handled as a separate operational-card task.

### B. Review v2 Quality Before Actual File Generation

Review the in-memory v2 cards before any write approval. The review should sample concept terms, aliases, category assignments, merge decisions, broad-term handling, payload accuracy, card ID collisions, and paraphrase retrieval behavior. Only after that review should actual v2 file generation be considered.

