---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "RAG Cards v2 데이터 정합성 검증을 위한 품질 감사(Quality Audit) 요약서"

---

# RAG Cards v2 Quality Audit Summary

## Priority Fix Re-Audit

The priority extraction and merge-safety fixes regenerated 142 isolated v2 cards. Every card and generated payload remains `draft`.

| Metric | Before | After |
| --- | ---: | ---: |
| Approval-review candidates | 1 | 138 |
| Held cards | 128 | 4 |
| Weak aliases | 124 | 0 |
| Broad-term risks | 5 | 0 |
| Confirmed suspicious cross-concept merges | 2 | 0 |

The four remaining held cards are:

- `algorithm-bfs`: low-distinctive retrieval fields
- `frontend-jsx`: low-distinctive retrieval fields
- `frontend-2`: duplicate normalized term `함수형`
- `java`: duplicate normalized term `함수형`

Priority retrieval now returns dedicated cards at rank 1:

| Query | Rank 1 | Score |
| --- | --- | ---: |
| `React key` | `frontend-react-key` | 9.0 |
| `Java equals` | `java-equals` | 7.5 |

The full current top-five results and all held-card fields are recorded in `2026-06-12-rag-cards-v2-quality-audit.json`.

## Final Held-Card And Loader Fix

The four remaining held cards were regenerated with specific terms and distinctive aliases:

- `algorithm-bfs` became `algorithm-breadth-first-search`.
- `frontend-jsx` became `frontend-jsx-expression`.
- `frontend-2` became `frontend-functional-component`.
- `java` became `java-functional-interface`.

The final audit reports 142 approval-review candidates and 0 held cards. All remain `draft`.

Workflow failures are now separated as:

- Group A, v1 corpus missing: 6 failures remain.
- Group B, Markdown generated-card loader compatibility: 2 failures fixed.

The loader now supports legacy Markdown `ConceptCard` and JSON `RagCard` files together. The legacy Markdown lint filters to its own card model instead of rejecting JSON cards.

## Regeneration Verification

- Generated draft cards: 142
- Generated draft payloads: 426
- Valid JSON failures: 0
- Duplicate card IDs: 0
- Non-draft card or payload statuses: 0
- Migration and audit tests: 19 passed
- Workflow extended regression: 77 passed, 8 failed
- `ACTIVE_CARD_STORE`: unset
- v1 concepts content hash: unchanged

Six workflow failures remain the previously classified v1-corpus-missing cases. Two additional extended-suite failures are existing Markdown generated-card loader compatibility failures in `test_generated_card_fast_path.py`; v2 is inactive and was not used by these tests.

## Historical Initial Audit

The sections below preserve the initial 129-card audit for before/after traceability.

## Scope And Safety

This audit reviewed all 129 draft cards in `ai/app/knowledge/concepts_v2`.

- No card or payload was promoted to `approved`.
- `ACTIVE_CARD_STORE` was not changed.
- The v1 `ai/app/knowledge/concepts` store was not modified by this audit.
- No commit or push was performed.
- The exhaustive held-card fields, risks, and retrieval top-five logs are recorded in `2026-06-12-rag-cards-v2-quality-audit.json`.

## Classification

| Classification | Count |
| --- | ---: |
| Approval-review candidates | 1 |
| Held cards | 128 |
| Broad-term risk cards | 5 |

The only approval-review candidate is `spring-spring-cache`. It is still a draft and requires human content review before any status change.

The audit conservatively holds any card with a broad or duplicate normalized term, weak aliases, a confirmed cross-concept merge, or a confirmed priority-query false positive.

## Risk Distribution

| Risk | Cards |
| --- | ---: |
| Weak aliases | 124 |
| Duplicate normalized term | 6 |
| Broad term | 5 |
| Confirmed suspicious cross-concept merge | 2 |
| Confirmed priority-query false positive | 1 |

## Broad-Term Risks

| Card ID | Term | Reason |
| --- | --- | --- |
| `frontend-3` | `에서` | Broad, duplicate, and non-concept term |
| `frontend-server` | `server` | Category-insufficient broad term |
| `java` | `에서` | Broad, duplicate, and non-concept term |
| `python` | `에서` | Broad, duplicate, and non-concept term |
| `spring` | `에서` | Broad, duplicate, and non-concept term |

The explicit priority terms `API`, `loading`, `latency`, and `concept` returned no candidates. The query `key` returned unrelated `에서` cards. The query `cache` correctly returned `spring-spring-cache`.

## Priority Retrieval Top Five

### React key

| Rank | Card ID | Term | Score |
| ---: | --- | --- | ---: |
| 1 | `frontend-3` | `에서` | 1.0 |
| 2 | `frontend-useeffect` | `useeffect` | 1.0 |
| 3 | `python` | `에서` | 1.0 |

No rank 4 or 5 candidate was returned. There is no dedicated React key card, and all returned cards are false positives.

### Java equals

| Rank | Card ID | Term | Score |
| ---: | --- | --- | ---: |
| 1 | `java-2` | `반복문이` | 5.0 |
| 2 | `java-3` | `접근` | 5.0 |
| 3 | `java-4` | `함수형` | 5.0 |
| 4 | `java-5` | `특징으로` | 5.0 |
| 5 | `java-arraylist` | `arraylist` | 5.0 |

There is no dedicated Java equals card, and all returned cards are false positives.

## Priority Corrections

These cards should be corrected before approval review:

| Cards | Required correction |
| --- | --- |
| `frontend-3`, `java`, `python`, `spring` | Replace the extracted non-concept term `에서`; rebuild aliases and retrieval fields. |
| `frontend-server` | Specialize `server` to the actual category-specific concept. |
| `frontend-5`, `python-2` | Split confirmed cross-concept merges and regenerate payloads per concept. |
| `frontend-4`, `java-4` | Resolve duplicate normalized term `함수형` into category-specific concepts. |
| `frontend-useeffect` | Prevent unrelated React key retrieval; review lexical scoring and retrieval keywords. |
| `java-2`, `java-3`, `java-5`, `java-arraylist` | Review aliases and retrieval fields causing Java equals false positives. |
| Remaining weak-alias cards | Add meaningful paraphrase aliases instead of repeating the normalized term. |

The exhaustive correction-needed list contains all 128 held cards and is available in the JSON audit report.

## Verification

- Card files: 129
- Invalid JSON: 0
- Duplicate card IDs: 0
- Non-draft cards or payload statuses: 0
- Audit tests: 6 passed
- `ACTIVE_CARD_STORE`: unset

This audit does not authorize approval promotion or production activation.
