---
type: spec
category: rag
status: active
updated: 2026-06-18
description: "프로덕션 반영 전 RAG Cards v2 Shadow Fast Path 검증 명세서"

---

# RAG Cards v2 Shadow Fast Path Validation

## Decision

**NOT_READY**

No card or payload status was changed, and v2 was not activated.

## Approval-Review Candidate Selection

| Metric | Result |
| --- | ---: |
| Draft cards evaluated | 142 |
| Approval-review candidates | 140 |
| Candidate rate | 98.59% |
| Candidate failures | 2 |

`approved_candidate=true` exists only in the shadow JSON report. It was not written to card files.

Candidate conditions included the existing quality audit, complete generated payloads, and a self-term retrieval smoke where the card must be Top1.

Candidate failures:

| Card | Reason | Actual Top1 |
| --- | --- | --- |
| `java-extends` | Self-term Top1 mismatch | `java-extends-keyword` |
| `python-with` | Self-term Top1 mismatch | `python-with-statement` |

## Shadow User Flow

The read-only simulation followed:

`Question → Intent → Retriever(v2) → Payload selection → Fast Path or fallback`

The sample set contains five questions for each generated intent:

- `CONCEPT_DEFINITION`: 5
- `ANSWER_REASON`: 5
- `WRONG_ANSWER_REASON`: 5

React key, Java equals, Spring cache, BFS, and JSX were tested for every intent.

| Metric | Result |
| --- | ---: |
| Samples | 15 |
| Card hits / Fast Path successes | 12 |
| Shadow Fast Path success rate | 80.0% |
| Fallback rate | 20.0% |
| Expected Ollama call reduction | 80.0% |
| Average lexical retrieval latency | 3.52 ms |

The evaluator records `llm_called=false` only when intent, Top1 card, and selected payload all match. It records `llm_called=true` for fallback cases.

## Top1 Score Distribution

| Percentile | Score |
| --- | ---: |
| Minimum | 6.5 |
| P25 | 7.5 |
| Median | 9.0 |
| P75 | 9.0 |
| P90 | 12.5 |
| Maximum | 12.5 |

## Problem Cards

| Card | Problem |
| --- | --- |
| `spring-spring-cache` | Dedicated card is missing. All three Spring cache shadow questions retrieve `spring-spring-bean-scope` at Top1. |
| `java-extends` | Generic card loses its self-term query to the more specific `java-extends-keyword` card. |
| `python-with` | Generic card loses its self-term query to the more specific `python-with-statement` card. |

## Readiness Rationale

The shadow Fast Path potential is substantial, but production transition is not ready because:

- not all draft cards meet the automatic approval-review candidate conditions;
- Spring cache is a required sample concept but has no dedicated v2 card;
- Spring cache falls back for all three generated intents.

The full candidate map, sample-level Top5 results, LLM-call flags, latency values, and problem cases are recorded in `2026-06-12-rag-cards-v2-shadow-validation.json`.

## Verification

- Shadow, migration, and audit tests: 24 passed
- v2 lint: 0 errors
- Workflow regression: 79 passed, 6 existing v1-corpus-missing failures
- v2 files: 142, invalid JSON: 0, duplicate IDs: 0, non-draft: 0
- `ACTIVE_CARD_STORE`: unset
- v1 concepts content hash: unchanged
- Scoped `git diff --check`: passed
