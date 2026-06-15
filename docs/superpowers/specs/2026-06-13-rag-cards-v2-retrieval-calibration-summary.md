# RAG Cards v2 Retrieval Calibration

## Decision

**READY**

This decision applies only to retrieval-ranking readiness. No card or payload was approved, and v2 was not activated.

## Root Cause

The lexical scorer treated any shared title or concept token as an exact phrase and added the same fixed boost. This caused:

- `java-extends-keyword` and `java-extends` to tie for the query `extends`;
- `python-with-statement` and `python-with` to tie for the query `with`;
- file ordering to decide both ties;
- the Spring CacheEvict card to be unreachable from the English query `Spring cache` because it lacked English cache aliases.

## Calibration

- Replaced the token-overlap exact boost with ordered phrase matching.
- Applied differentiated ranking boosts for term phrase, alias phrase, and boost-keyword phrase.
- Added Spring cache, cache eviction, and cache evict retrieval fields to the existing `spring-spring-question-59` card.
- No threshold or question-specific ranking exception was added.

One card's retrieval fields were calibrated. The general lexical ranking rule was calibrated for all cards.

## Target Top Three

| Query | Rank 1 | Score | Rank 2 | Score | Rank 3 | Score |
| --- | --- | ---: | --- | ---: | --- | ---: |
| `Spring cache` | `spring-spring-question-59` | 7.5 | `spring-spring-bean-scope` | 4.5 | `spring-aop` | 3.0 |
| `extends` | `java-extends` | 8.5 | `java-extends-keyword` | 4.5 | - | - |
| `with` | `python-with` | 8.5 | `python-with-statement` | 4.5 | - | - |

Control queries remained Top1:

- `React key` → `frontend-react-key`, score 11.0
- `Java equals` → `java-equals`, score 9.5

## Shadow Comparison

| Metric | Before | After |
| --- | ---: | ---: |
| Fast Path success | 80.0% | 100.0% |
| Fallback | 20.0% | 0.0% |
| Expected Ollama call reduction | 80.0% | 100.0% |
| Average retrieval latency | 3.52 ms | 7.44 ms |
| Target misretrievals | 3 | 0 |
| Misretrieval removal rate | - | 100.0% |

Top1 score distribution after calibration:

- minimum: 7.5
- P25: 9.5
- median: 11.0
- P75: 11.0
- P90 / maximum: 14.5

## Safety

- All 142 cards and generated payloads remain `draft`.
- No cards were added or generated.
- `ACTIVE_CARD_STORE` was not changed.
- v1 concepts were not modified.
- No commit or push was performed.
