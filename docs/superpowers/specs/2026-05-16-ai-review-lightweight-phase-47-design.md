# AI Review Lightweight Phase 4.7 Design

## Goal

Implement a laptop-friendly version of Phase 4 through 4.7: a LangGraph-compatible workflow shape, lightweight validation, confidence scoring, fallback routing, and a small keyword evaluator without requiring GPU-heavy RAG dependencies.

## Execution Order

This phase must run after `2026-05-16-ai-review-rag-phase-1-design.md`.

Fixed order:

```text
Phase 1: rag-phase-1
→ Phase 4.7: lightweight-phase-47
```

Do not execute both phases concurrently. Both phases modify `ai/app/service.py`, so this phase assumes Phase 1 has already established the modular AI service foundation.

## Constraints

- Target machine has 16GB RAM, no discrete GPU, and limited free memory during normal development.
- Default runtime must work with only `requirements.txt`.
- `requirements-rag.txt` remains optional and must not be needed for server startup or tests.
- No Chroma, sentence-transformers, torch, or flashrank execution in this phase.

## Scope

- Add a small workflow package with state, nodes, and runner.
- Keep node names compatible with later LangGraph migration: `retrieve_context`, `rule_evaluate`, `generate_answer`, `validate_answer`, `confidence_gate`, `fallback_answer`.
- Add normalized confidence scoring:
  - retrieval score
  - rule match score
  - answer validation score
  - model self check score
- Add lightweight validation:
  - Korean output check
  - required keyword match from retrieved concept cards
  - forbidden claim check
- Add fallback routing:
  - template fallback for Ollama failure
  - template fallback for non-Korean or low-confidence answers
- Add a small golden dataset and evaluator script based on keyword retrieval and validation checks.
- Remove heavy RAG dependencies from base `requirements.txt`.

## Non-Goals

- No real `langgraph` package requirement.
- No Chroma vector store.
- No embedding model loading.
- No reranker model loading.
- No Spring Boot DTO or candidate approval API.
- No admin UI.

## Architecture

`service.py` delegates each request to `workflow.runner.run_review_workflow()`. The runner applies deterministic nodes in order and accepts a generator function so tests can run without Ollama.

The workflow returns an `AiGenerateResponse` with answer, retrieved concept ids, confidence score, fallback usage, model used, prompt version, and latency. This preserves the existing endpoint contract while making the internal flow ready for a future LangGraph StateGraph.

## Files

- `ai/app/workflow/state.py`: dataclasses for workflow state and validation result.
- `ai/app/workflow/nodes.py`: workflow node functions.
- `ai/app/workflow/runner.py`: sequential runner.
- `ai/app/scoring.py`: normalized confidence score.
- `ai/evals/golden_dataset.jsonl`: small evaluator dataset.
- `ai/scripts/evaluate_lightweight_rag.py`: keyword evaluator.
- `ai/tests/test_scoring.py`: scoring tests.
- `ai/tests/test_workflow_runner.py`: workflow routing tests.
- `ai/tests/test_lightweight_evaluator.py`: evaluator tests.
- `ai/app/service.py`: delegate to runner.
- `ai/requirements.txt`: base runtime only.
- `ai/requirements-rag.txt`: optional heavy dependencies.

## Testing

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests -v
python scripts\lint_knowledge_cards.py
python scripts\evaluate_lightweight_rag.py
```

## Rollout Notes

This phase favors reliable local execution over maximal RAG accuracy. The fallback retriever and evaluator are intentionally simple, but their interfaces make later Chroma/BM25/LangGraph upgrades incremental instead of disruptive.

