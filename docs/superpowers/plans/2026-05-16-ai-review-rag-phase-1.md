# AI Review RAG Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 RAG foundation for the Python AI service without changing the existing FastAPI endpoint contract.

**Architecture:** Keep `service.py` as the endpoint orchestration facade, but move Ollama calls, prompt construction, text validation, and RAG loading into focused modules. The RAG layer starts with file-based Markdown concept cards and a dependency-tolerant keyword retriever, with optional hooks for LangChain/Chroma/BM25/flashrank later.

**Tech Stack:** FastAPI, Pydantic, Python standard library `unittest`, Markdown files, optional LangChain/Chroma/sentence-transformers/flashrank/kiwipiepy dependencies.

**Execution Order:** Run this plan before `docs/superpowers/plans/2026-05-16-ai-review-lightweight-phase-47.md`. Do not execute both plans concurrently because both modify `ai/app/service.py`.

---

## File Structure

- Create `ai/app/ollama/client.py`: Ollama HTTP generation and warm-up.
- Create `ai/app/prompts.py`: mode-specific Korean tutor prompts.
- Create `ai/app/validation/text.py`: answer cleanup, Korean ratio, PII masking, fallback text.
- Create `ai/app/rag/documents.py`: front matter parsing, concept card discovery, section splitting.
- Create `ai/app/rag/retriever.py`: simple local retriever and future optional dependency boundary.
- Create `ai/app/knowledge/concepts/...`: initial Java/Spring cards.
- Create `ai/app/knowledge/prompts/...`: prompt files and version manifest.
- Create `ai/app/knowledge/approved_qa/.gitkeep` and `ai/app/knowledge/candidates/README.md`.
- Create `ai/scripts/lint_knowledge_cards.py`: validates cards and approved QA references.
- Create `ai/tests/test_schemas.py`, `ai/tests/test_knowledge_lint.py`, `ai/tests/test_rag_documents.py`, `ai/tests/test_rag_retriever.py`.
- Modify `ai/app/schemas.py`: default model and response metadata.
- Modify `ai/app/service.py`: delegate to new modules.
- Modify `ai/app/main.py`: import warm-up from new Ollama module.
- Modify `ai/requirements.txt`: keep base FastAPI runtime dependencies.
- Create `ai/requirements-rag.txt`: optional RAG/LangGraph dependencies.

### Task 1: Schema Defaults And Response Metadata

**Files:**
- Modify: `ai/app/schemas.py`
- Test: `ai/tests/test_schemas.py`

- [ ] **Step 1: Write the failing schema tests**

```python
import unittest

from app.schemas import AiGenerateRequest, AiGenerateResponse


class SchemaDefaultsTest(unittest.TestCase):
    def test_default_model_is_small_qwen3_model(self):
        request = AiGenerateRequest()
        self.assertEqual(request.model, "qwen3:1.7b")

    def test_empty_model_normalizes_to_small_qwen3_model(self):
        request = AiGenerateRequest.model_validate({"model": ""})
        self.assertEqual(request.model, "qwen3:1.7b")

    def test_response_accepts_nullable_rag_metadata(self):
        response = AiGenerateResponse(answer="답변")
        self.assertIsNone(response.confidence_score)
        self.assertIsNone(response.model_used)
        self.assertIsNone(response.fallback_used)
        self.assertEqual(response.retrieved_concept_ids, [])
        self.assertIsNone(response.candidate_id)
        self.assertIsNone(response.prompt_version)
        self.assertIsNone(response.latency_ms)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd ai; python -m unittest tests.test_schemas -v`

Expected: FAIL because default model is still `qwen3:4b-q4_K_M` and response metadata fields do not exist.

- [ ] **Step 3: Implement schema changes**

Set `AiGenerateRequest.model` and `none_to_default_model()` fallback to `qwen3:1.7b`. Add nullable response fields:

```python
confidence_score: float | None = None
model_used: str | None = None
fallback_used: bool | None = None
retrieved_concept_ids: list[str] = Field(default_factory=list)
candidate_id: str | None = None
prompt_version: str | None = None
latency_ms: int | None = None
```

- [ ] **Step 4: Run the test and verify GREEN**

Run: `cd ai; python -m unittest tests.test_schemas -v`

Expected: PASS.

### Task 2: Knowledge Card Loader And Lint

**Files:**
- Create: `ai/app/rag/documents.py`
- Create: `ai/scripts/lint_knowledge_cards.py`
- Create: `ai/app/knowledge/concepts/spring/n-plus-one.md`
- Create: `ai/app/knowledge/concepts/spring/fetch-join.md`
- Create: `ai/app/knowledge/concepts/java/equals.md`
- Create: `ai/app/knowledge/approved_qa/.gitkeep`
- Create: `ai/app/knowledge/candidates/README.md`
- Test: `ai/tests/test_rag_documents.py`
- Test: `ai/tests/test_knowledge_lint.py`

- [ ] **Step 1: Write failing document loader tests**

Test that `load_concept_cards()` returns cards with `concept_id`, metadata, and sections, and that the N+1 card has a `핵심 설명` section.

- [ ] **Step 2: Write failing lint tests**

Test that duplicate concept ids fail and valid bundled cards pass.

- [ ] **Step 3: Run tests and verify RED**

Run: `cd ai; python -m unittest tests.test_rag_documents tests.test_knowledge_lint -v`

Expected: FAIL because loader, linter, and knowledge files do not exist.

- [ ] **Step 4: Implement document loader, linter, and initial cards**

Implement simple YAML-like front matter parsing, heading detection for `##`, and validation for required metadata and headings.

- [ ] **Step 5: Run tests and lint command**

Run: `cd ai; python -m unittest tests.test_rag_documents tests.test_knowledge_lint -v`

Run: `cd ai; python scripts/lint_knowledge_cards.py`

Expected: PASS and lint prints a success summary.

### Task 3: Dependency-Tolerant Retriever

**Files:**
- Create: `ai/app/rag/retriever.py`
- Test: `ai/tests/test_rag_retriever.py`

- [ ] **Step 1: Write failing retriever tests**

Test that `retrieve_context("N+1 문제", limit=2)` returns the N+1 concept first and that unknown queries return an empty list rather than raising.

- [ ] **Step 2: Run test and verify RED**

Run: `cd ai; python -m unittest tests.test_rag_retriever -v`

Expected: FAIL because retriever does not exist.

- [ ] **Step 3: Implement simple retriever**

Tokenize Korean/English text with standard library regex, score cards by query token overlap with title, section text, and evaluation keywords, and return `RetrievedContext` items.

- [ ] **Step 4: Run test and verify GREEN**

Run: `cd ai; python -m unittest tests.test_rag_retriever -v`

Expected: PASS.

### Task 4: Modularize Existing Service

**Files:**
- Create: `ai/app/ollama/client.py`
- Create: `ai/app/prompts.py`
- Create: `ai/app/validation/text.py`
- Modify: `ai/app/service.py`
- Modify: `ai/app/main.py`
- Test: `ai/tests/test_service_helpers.py`

- [ ] **Step 1: Write failing helper tests**

Test compact sentence limiting, Korean fallback behavior, and prompt version selection for the three modes.

- [ ] **Step 2: Run test and verify RED**

Run: `cd ai; python -m unittest tests.test_service_helpers -v`

Expected: FAIL because helper modules do not exist.

- [ ] **Step 3: Move helper logic into focused modules**

Move prompt building to `prompts.py`, text helpers to `validation/text.py`, and Ollama HTTP/warm-up to `ollama/client.py`. Update `service.py` to compose them and include response metadata.

- [ ] **Step 4: Run helper tests and existing schema/RAG tests**

Run: `cd ai; python -m unittest discover -s tests -v`

Expected: PASS.

### Task 5: Requirements And Prompt Version Files

**Files:**
- Modify: `ai/requirements.txt`
- Create: `ai/app/knowledge/prompts/first_question_v1.prompt`
- Create: `ai/app/knowledge/prompts/follow_up_v1.prompt`
- Create: `ai/app/knowledge/prompts/free_question_v1.prompt`
- Create: `ai/app/knowledge/prompts/validation_v1.prompt`
- Create: `ai/app/knowledge/prompts/prompt_versions.yml`

- [ ] **Step 1: Add prompt files and manifest**

Create versioned prompt files matching the current endpoint modes and a `prompt_versions.yml` manifest with `file`, `version`, `created_at`, `owner`, and `purpose`.

- [ ] **Step 2: Add dependencies**

Create `ai/requirements-rag.txt` with these optional dependencies:

```text
langchain==0.3.25
langchain-community==0.3.24
langgraph==0.4.5
chromadb==0.5.23
sentence-transformers==3.3.1
flashrank==0.2.10
kiwipiepy==0.20.4
```

- [ ] **Step 3: Run full Python tests**

Run: `cd ai; python -m unittest discover -s tests -v`

Expected: PASS.

## Verification

Run all Python tests:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests -v
```

Run knowledge lint:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/lint_knowledge_cards.py
```

## Self-Review

- Spec coverage: covers Phase 1 through 3.5 only; LangGraph, evaluator, DTO migration, and approval UI are intentionally out of scope.
- Placeholder scan: no task relies on unspecified behavior; each task has concrete files and commands.
- Type consistency: response metadata names match the final architecture DTO intent while keeping Python snake_case.
