---
type: spec
category: evaluation
status: active
updated: 2026-06-18
description: "의미론적 유사도 기반 AI 시스템 자동 평가(Semantic Evaluation) 명세서"

---

# AI Semantic Evaluation Design

## Goal

Add a deterministic semantic safety judge to flag weakly grounded, contradictory, or hallucination-suspected AI Review answers and prevent suspicious answers from entering the answer cache.

## Scope

This closes the P3 Semantic Evaluation item:

- evidence-grounding judge design
- contradiction check
- hallucination suspected answer cache ban

## Approach

Implement a Python runtime judge that runs after answer validation/confidence/fallback and before answer cache persistence.

The first implementation is deterministic and rule-based. It does not call another LLM. This keeps latency bounded and avoids introducing a second hallucination surface.

## Judge Signals

The judge receives:

- answer text
- route
- fallback status
- retrieved concept ids
- retrieved context text
- quality flags already produced by validation/confidence gates

It emits:

- `evidence_missing`
- `contradiction_suspected`
- `hallucination_suspected`

## Rules

Evidence missing:

- answer is generated through `generation` or `rag_generation`
- answer has no retrieved context
- answer contains concrete technical claims
- answer is not fallback/template/cache/lightweight

Contradiction suspected:

- answer contains known stale/wrong concept markers already tracked by evaluator rows, or
- answer includes concept ids unrelated to retrieved concepts in an explicitly grounded route

Hallucination suspected:

- `evidence_missing` is present, and
- answer includes concrete implementation claims such as exact annotations, APIs, SQL/cache behavior, or status-code assertions

## Cache Policy

`hallucination_suspected` and `contradiction_suspected` make an answer non-cacheable. Existing cache hits are still allowed; the policy prevents newly generated suspicious answers from being stored.

## Evaluator

Extend `scripts/evaluate_lightweight_rag.py` with semantic metrics derived from workflow response quality flags:

- `semantic_grounding_pass_rate`
- `contradiction_absent_rate`
- `hallucination_cache_ban_rate`

These metrics are deterministic and can run in local CI without Ollama.

## Testing

Use TDD:

- judge flags ungrounded concrete generated answer
- judge does not flag fallback/template/lightweight answer
- workflow does not cache hallucination-suspected generated answer
- evaluator reports semantic rates from quality flags
