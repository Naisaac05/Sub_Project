# RAG Cards v2 Isolated Migration Design

## Goal

Generate concept-centered draft RAG cards from `backend/data/devmatch-data-only.sql` without modifying or activating the existing production card store.

## Isolation And Safety

- The migration output root is fixed to `ai/app/knowledge/concepts_v2`.
- The runtime default remains `ai/app/knowledge/concepts`; `ACTIVE_CARD_STORE` is not introduced or changed.
- The migration command is dry-run only. `--write` is rejected.
- No existing card file is deleted, modified, or overwritten.
- Backups are read-only snapshots created under `ai/app/knowledge/concepts_backup`.

## Card Generation

- Parse `questions.content`, `questions.options`, `questions.correct_answer`, and `questions.test_id`.
- Skip questions whose content directly describes E2E failure.
- Generate only `CONCEPT_DEFINITION`, `ANSWER_REASON`, and `WRONG_ANSWER_REASON`.
- All cards and generated payload statuses are `draft`.
- Merge only within the same category using normalized term equality, alias overlap, or embedding similarity.
- Keep `source_question_ids` as trace metadata only.
- Build `embedding_text` from term, up to three aliases, category, and three to seven boost keywords.

## Retrieval And Evaluation

- Exact Match Hit@K is reported only as a data leakage baseline.
- LOO retrieves 50 candidates, removes the source card, then reports candidate availability, average score, and same-category ratio.
- Draft v2 cards are evaluated with explicitly injected card loaders and never enter the production runtime loader.

## Validation

Validation covers schema rules, v2 path isolation, draft status, payload policy, card ID rules, retrieval text limits, dry-run behavior, workflow intent policy, and `git diff --check`.

