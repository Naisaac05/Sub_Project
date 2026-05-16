# AI Review RAG Phase 1 Design

## Goal

Implement the first foundation slice of the final AI Review Local RAG + LangGraph architecture: Python AI server modularization, file-based knowledge structure, concept card linting, and a dependency-tolerant RAG retrieval skeleton.

## Scope

This phase covers implementation steps 1 through 3.5 from the final architecture document:

- Split the current Python AI service into focused modules.
- Change the default small model to `qwen3:1.7b` and keep `qwen3:4b-q4_K_M` as fallback.
- Add the `ai/app/knowledge/` folder layout for concepts, approved QA, candidates, and prompts.
- Add initial Java/Spring concept cards.
- Add a lint script that validates concept card metadata and required sections.
- Add a RAG retrieval package that can load concept cards, split them by Markdown sections, tokenize Korean text, and perform local keyword fallback retrieval.
- Add optional integration points for LangChain, Chroma, BM25, `kiwipiepy`, and `flashrank` without making server startup fail when those packages are not installed yet.

## Non-Goals

- No LangGraph workflow execution in this phase.
- No Spring Boot DTO migration in this phase.
- No candidate approval DB/API or admin UI in this phase.
- No evaluator golden set in this phase.
- No Redis, Prometheus, Langfuse, or PGVector integration.

## Architecture

The existing FastAPI endpoints keep their request and response contract. `service.py` remains the orchestration entrypoint for `/api/review/*`, but Ollama-specific code moves to `app/ollama/client.py`, prompt construction moves to `app/prompts.py`, answer cleanup/fallback helpers move to `app/validation/text.py`, and RAG helpers live under `app/rag/`.

The RAG layer is intentionally dependency-tolerant. It exposes a small `retrieve_context(query, limit)` interface backed by Markdown concept cards and a built-in tokenizer/search implementation. When LangChain or other heavier packages are installed later, the adapter can upgrade behavior without changing endpoint code.

## Files

- `ai/app/schemas.py`: update default model and response metadata fields.
- `ai/app/service.py`: keep endpoint orchestration, delegate prompt, Ollama, and validation helpers.
- `ai/app/ollama/client.py`: Ollama request and warm-up client.
- `ai/app/prompts.py`: compact prompt builders.
- `ai/app/validation/text.py`: Korean ratio, sentence limiting, PII masking, fallback responses.
- `ai/app/rag/documents.py`: concept card loader and section splitter.
- `ai/app/rag/retriever.py`: dependency-tolerant retrieval interface.
- `ai/scripts/lint_knowledge_cards.py`: concept/approved QA lint command.
- `ai/tests/`: unit tests for linting, RAG loading, retrieval, and schema defaults.
- `ai/app/knowledge/`: initial knowledge files and prompts.
- `ai/requirements.txt`: keep only base FastAPI runtime dependencies.
- `ai/requirements-rag.txt`: keep optional RAG/LangGraph dependencies for later phases.

## Testing

Use Python `unittest` so the project can verify behavior before new test dependencies are added.

Primary command:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests
```

The lint script should also pass:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/lint_knowledge_cards.py
```

## Rollout Notes

This phase prepares the codebase for LangGraph and evaluator work while preserving the existing API. If the optional RAG dependencies are not installed yet, the server still starts and the fallback retriever remains usable for tests and local development. Install `requirements-rag.txt` only when enabling Chroma/LangChain-backed retrieval.
