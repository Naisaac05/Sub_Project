# AI Full RAG Evaluation Pipeline Design

## Goal

Close the P3 Full RAG Evaluation Pipeline item by expanding the deterministic golden dataset, adding answer-grounding metrics, and making candidate promotion run before/after evaluation automatically.

## Scope

- Expand default golden dataset coverage to 200-500 rows.
- Keep existing retrieval recall and stale-context metrics.
- Add an answer-grounding metric that combines workflow semantic flags, required keywords, and forbidden claims.
- Add promotion before/after evaluation and delta reporting.

## Approach

The default `load_dataset()` will load the existing curated JSONL rows and generate deterministic coverage variants from those rows until the default evaluation set reaches 200 rows. The original JSONL remains the hand-curated seed corpus; generated rows are marked with `generated_variant: true`.

The evaluator will add `answer_grounding_rate`. A workflow answer is grounded when it has no semantic risk flags (`evidence_missing`, `hallucination_suspected`, `contradiction_suspected`), includes all configured required keywords, and avoids configured forbidden claims.

The promotion workflow will evaluate before promotion and after reindex. The report will include `before_evaluation`, `evaluation`, and `evaluation_delta` for key numeric metrics.

## Failure Policy

Evaluation remains deterministic and local by default. The promotion workflow still fails on lint, reindex, or below-threshold after-promotion retrieval hit rate.

## Testing

- Dataset default load returns at least 200 unique rows.
- Evaluator reports answer-grounding metrics.
- Promotion workflow calls before-eval, promote, lint, reindex, after-eval in order and reports metric deltas.
