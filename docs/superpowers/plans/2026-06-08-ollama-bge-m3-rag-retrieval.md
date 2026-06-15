# Ollama BGE-M3 RAG Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect Ollama `bge-m3` as the production-selectable, semantic-only RAG retriever with lexical fallback, card embedding cache, source-aware workflow thresholds, and repeatable verification.

**Architecture:** A focused Ollama embedding client owns `/api/embeddings` HTTP calls and vector validation. `OllamaBgeRetrieverAdapter` owns card embedding cache and cosine ranking, while workflow nodes use one shared acceptance function so BGE cosine scores are not rejected by the existing lexical threshold. Existing lexical, BM25, hybrid, Chroma, prompt, and intent-classification behavior remain compatible.

**Tech Stack:** Python 3.11+, `unittest`, standard-library `urllib`, Ollama `/api/embeddings`, existing AI Review RAG/workflow modules.

**User Constraint:** Do not create Git commits unless the user explicitly asks. Every task ends with a diff/status checkpoint instead of a commit.

---

## File Structure

### Create

- `ai/app/ollama/embeddings.py`
  - Owns Ollama embedding HTTP calls, timeout handling, response validation, vector normalization, and cosine similarity.
- `ai/tests/test_ollama_embeddings.py`
  - Unit tests for the embedding client without a real Ollama process.
- `ai/scripts/smoke_ollama_bge_retriever.py`
  - Runs a small live Ollama smoke check against the actual knowledge cards.

### Modify

- `ai/app/rag/retriever.py`
  - Adds `OllamaBgeRetrieverAdapter`, card-vector cache, fallback metadata, and `bge` selector aliases.
- `ai/app/workflow/nodes.py`
  - Adds one shared source-aware context acceptance function and replaces direct score comparisons.
- `ai/tests/test_rag_retriever.py`
  - Covers semantic ranking, cache reuse/invalidation, fallback, and selector aliases.
- `ai/tests/test_workflow_runner.py`
  - Covers BGE score acceptance and lexical threshold compatibility.
- `ai/README.md`
  - Documents activation settings, fallback behavior, and verification commands.
- `docs/ai-review-runtime-profiles.md`
  - Documents the new runtime profile and environment variables.

### Explicitly Not Modified

- `ai/app/workflow/intent.py`
- `ai/evals/intent_poc/**`
- Answer prompts and generation-model selection
- Existing candidate approval and knowledge-card promotion flow

---

### Task 1: Add A Validated Ollama Embedding Client

**Files:**
- Create: `ai/app/ollama/embeddings.py`
- Create: `ai/tests/test_ollama_embeddings.py`

- [ ] **Step 1: Write failing tests for successful embedding and normalization**

Create `ai/tests/test_ollama_embeddings.py`:

```python
import json
import math
import unittest
from unittest.mock import patch

from app.ollama.embeddings import OllamaEmbeddingClient, cosine_similarity


class FakeResponse:
    def __init__(self, payload: dict):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self):
        return json.dumps(self.payload).encode("utf-8")


class OllamaEmbeddingClientTest(unittest.TestCase):
    def test_embed_returns_normalized_vector(self):
        with patch(
            "app.ollama.embeddings.urllib.request.urlopen",
            return_value=FakeResponse({"embedding": [3.0, 4.0]}),
        ):
            vector = OllamaEmbeddingClient(
                base_url="http://ollama.test",
                model="bge-m3",
                timeout_seconds=7,
            ).embed("N+1이 뭐야?")

        self.assertAlmostEqual(vector[0], 0.6)
        self.assertAlmostEqual(vector[1], 0.8)
        self.assertAlmostEqual(math.sqrt(sum(value * value for value in vector)), 1.0)

    def test_cosine_similarity_uses_normalized_vectors(self):
        self.assertAlmostEqual(cosine_similarity([1.0, 0.0], [0.8, 0.6]), 0.8)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_ollama_embeddings -v
```

Expected: FAIL because `app.ollama.embeddings` does not exist.

- [ ] **Step 3: Implement the embedding client and vector helpers**

Create `ai/app/ollama/embeddings.py`:

```python
from __future__ import annotations

import json
import math
import os
import urllib.error
import urllib.request

from app.ollama.client import OLLAMA_BASE_URL


class EmbeddingError(RuntimeError):
    pass


def normalize_vector(values: list[float]) -> list[float]:
    if not values or any(not math.isfinite(value) for value in values):
        raise EmbeddingError("invalid_embedding_vector")
    norm = math.sqrt(sum(value * value for value in values))
    if not math.isfinite(norm) or norm <= 0:
        raise EmbeddingError("invalid_embedding_norm")
    return [value / norm for value in values]


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if len(left) != len(right):
        raise EmbeddingError("embedding_dimension_mismatch")
    return sum(a * b for a, b in zip(left, right, strict=True))


class OllamaEmbeddingClient:
    def __init__(
        self,
        base_url: str | None = None,
        model: str | None = None,
        timeout_seconds: int | None = None,
    ):
        self.base_url = (base_url or OLLAMA_BASE_URL).rstrip("/")
        self.model = model or os.getenv("AI_REVIEW_EMBEDDING_MODEL", "bge-m3")
        configured_timeout = timeout_seconds or int(
            os.getenv("AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS", "10")
        )
        self.timeout_seconds = max(1, configured_timeout)

    def embed(self, text: str) -> list[float]:
        if not text.strip():
            raise EmbeddingError("empty_embedding_input")
        body = json.dumps({"model": self.model, "prompt": text}).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/api/embeddings",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            raise EmbeddingError(type(exc).__name__) from exc

        raw = payload.get("embedding")
        if not isinstance(raw, list):
            raise EmbeddingError("missing_embedding")
        try:
            values = [float(value) for value in raw]
        except (TypeError, ValueError) as exc:
            raise EmbeddingError("invalid_embedding_value") from exc
        return normalize_vector(values)
```

- [ ] **Step 4: Add failing tests for malformed responses and dimension mismatch**

Append to `OllamaEmbeddingClientTest`:

```python
    def test_embed_rejects_missing_embedding(self):
        with patch(
            "app.ollama.embeddings.urllib.request.urlopen",
            return_value=FakeResponse({"model": "bge-m3"}),
        ):
            with self.assertRaisesRegex(RuntimeError, "missing_embedding"):
                OllamaEmbeddingClient(base_url="http://ollama.test").embed("query")

    def test_embed_rejects_zero_vector(self):
        with patch(
            "app.ollama.embeddings.urllib.request.urlopen",
            return_value=FakeResponse({"embedding": [0.0, 0.0]}),
        ):
            with self.assertRaisesRegex(RuntimeError, "invalid_embedding_norm"):
                OllamaEmbeddingClient(base_url="http://ollama.test").embed("query")

    def test_cosine_similarity_rejects_dimension_mismatch(self):
        with self.assertRaisesRegex(RuntimeError, "embedding_dimension_mismatch"):
            cosine_similarity([1.0, 0.0], [1.0])
```

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_ollama_embeddings -v
```

Expected: all embedding-client tests PASS.

- [ ] **Step 6: Checkpoint without committing**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
git status --short
```

Expected: only intended embedding files plus existing user changes are listed; no commit is created.

---

### Task 2: Add The Semantic-Only Ollama BGE Retriever

**Files:**
- Modify: `ai/app/rag/retriever.py`
- Modify: `ai/tests/test_rag_retriever.py`

- [ ] **Step 1: Write failing semantic-ranking and metadata tests**

Add `OllamaBgeRetrieverAdapter` to the imports in `ai/tests/test_rag_retriever.py`, then add:

```python
    def test_ollama_bge_retriever_ranks_semantically_nearest_card(self):
        vectors = {
            "질문": [1.0, 0.0],
            "spring-n-plus-one": [0.9, 0.1],
            "java-stream": [0.0, 1.0],
        }

        def embed(text: str) -> list[float]:
            if text == "질문":
                return vectors[text]
            return vectors["spring-n-plus-one" if "N+1" in text else "java-stream"]

        adapter = OllamaBgeRetrieverAdapter(
            embed=embed,
            card_loader=lambda: [
                card("spring-n-plus-one", "N+1", "N+1 문제 설명"),
                card("java-stream", "Stream", "Stream 설명"),
            ],
        )

        results = adapter.retrieve("질문", limit=2)

        self.assertEqual(results[0].concept_id, "spring-n-plus-one")
        self.assertEqual(results[0].metadata["retriever"], "ollama_bge_m3")
        self.assertEqual(results[0].metadata["embedding_model"], "bge-m3")
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_rag_retriever.RagRetrieverTest.test_ollama_bge_retriever_ranks_semantically_nearest_card -v
```

Expected: FAIL because `OllamaBgeRetrieverAdapter` does not exist.

- [ ] **Step 3: Implement the adapter with semantic-only ranking**

In `ai/app/rag/retriever.py`, import `hashlib`, `logging`, and the embedding helpers:

```python
import hashlib
import logging

from app.ollama.embeddings import EmbeddingError, OllamaEmbeddingClient, cosine_similarity
```

Add the adapter before `ChromaBgeRetrieverAdapter`:

```python
class OllamaBgeRetrieverAdapter(RetrieverAdapter):
    def __init__(
        self,
        embed: Callable[[str], list[float]] | None = None,
        card_loader: Callable[[], list[ConceptCard]] = load_concept_cards,
        fallback: RetrieverAdapter | None = None,
        model_name: str | None = None,
    ):
        self.model_name = model_name or os.getenv("AI_REVIEW_EMBEDDING_MODEL", "bge-m3")
        self.embed = embed or OllamaEmbeddingClient(model=self.model_name).embed
        self.card_loader = card_loader
        self.fallback = fallback
        self._card_vectors: dict[str, tuple[str, list[float]]] = {}

    def retrieve(self, query: str, limit: int = 5, reranker: Reranker | None = None) -> list[RetrievedContext]:
        if not query.strip() or limit <= 0:
            return []
        try:
            cards = self.card_loader()
            query_vector = self.embed(query)
            current_ids = {card.concept_id for card in cards}
            self._card_vectors = {
                concept_id: cached
                for concept_id, cached in self._card_vectors.items()
                if concept_id in current_ids
            }
            results = []
            for card in cards:
                content = _format_card_context(card)
                content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
                cached = self._card_vectors.get(card.concept_id)
                if cached is None or cached[0] != content_hash:
                    cached = (content_hash, self.embed(content))
                    self._card_vectors[card.concept_id] = cached
                metadata = dict(card.metadata)
                metadata["retriever"] = "ollama_bge_m3"
                metadata["embedding_model"] = self.model_name
                results.append(
                    RetrievedContext(
                        concept_id=card.concept_id,
                        title=card.title,
                        content=content,
                        score=cosine_similarity(query_vector, cached[1]),
                        metadata=metadata,
                    )
                )
        except Exception as exc:
            return self._fallback(query, limit, reranker, exc)

        results.sort(key=lambda item: item.score, reverse=True)
        limited = results[:limit]
        if reranker is not None:
            limited = reranker(limited, query)
        return limited[:limit]

    def _fallback(
        self,
        query: str,
        limit: int,
        reranker: Reranker | None,
        error: Exception,
    ) -> list[RetrievedContext]:
        logging.getLogger("ai_review.rag").warning(
            "ai_review.rag_embedding_fallback model=%s reason=%s",
            self.model_name,
            type(error).__name__,
        )
        if self.fallback is None:
            return []
        fallback_results = self.fallback.retrieve(query, limit=limit, reranker=reranker)
        enriched = []
        for item in fallback_results:
            metadata = dict(item.metadata)
            metadata["fallback_from"] = "ollama_bge_m3"
            metadata["fallback_reason"] = type(error).__name__
            enriched.append(
                RetrievedContext(item.concept_id, item.title, item.content, item.score, metadata)
            )
        return enriched
```

- [ ] **Step 4: Write failing cache reuse and invalidation tests**

Add:

```python
    def test_ollama_bge_retriever_reuses_unchanged_card_embeddings(self):
        cards = [card("spring-n-plus-one", "N+1", "N+1 문제 설명")]
        calls = []

        def embed(text: str) -> list[float]:
            calls.append(text)
            return [1.0, 0.0]

        adapter = OllamaBgeRetrieverAdapter(embed=embed, card_loader=lambda: cards)
        adapter.retrieve("첫 질문")
        adapter.retrieve("둘째 질문")

        card_calls = [text for text in calls if text.startswith("# N+1")]
        self.assertEqual(len(card_calls), 1)

    def test_ollama_bge_retriever_reembeds_changed_card(self):
        cards = [card("spring-n-plus-one", "N+1", "첫 설명")]
        calls = []

        def embed(text: str) -> list[float]:
            calls.append(text)
            return [1.0, 0.0]

        adapter = OllamaBgeRetrieverAdapter(embed=embed, card_loader=lambda: cards)
        adapter.retrieve("첫 질문")
        cards[0] = card("spring-n-plus-one", "N+1", "변경된 설명")
        adapter.retrieve("둘째 질문")

        card_calls = [text for text in calls if text.startswith("# N+1")]
        self.assertEqual(len(card_calls), 2)
```

- [ ] **Step 5: Write failing fallback metadata test**

Add:

```python
    def test_ollama_bge_retriever_falls_back_to_lexical_with_metadata(self):
        fallback = LexicalRetrieverAdapter(
            card_loader=lambda: [card("spring-n-plus-one", "N+1", "N+1 설명", "n+1")]
        )
        adapter = OllamaBgeRetrieverAdapter(
            embed=lambda text: (_ for _ in ()).throw(RuntimeError("offline")),
            card_loader=lambda: [],
            fallback=fallback,
        )

        results = adapter.retrieve("N+1", limit=1)

        self.assertEqual(results[0].concept_id, "spring-n-plus-one")
        self.assertEqual(results[0].metadata["fallback_from"], "ollama_bge_m3")
        self.assertEqual(results[0].metadata["fallback_reason"], "RuntimeError")
```

- [ ] **Step 6: Run retriever tests and verify GREEN**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_rag_retriever -v
```

Expected: all RAG retriever tests PASS.

- [ ] **Step 7: Checkpoint without committing**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
git status --short
```

Expected: adapter changes are visible and no commit is created.

---

### Task 3: Connect The BGE Retriever Selector Without Changing Defaults

**Files:**
- Modify: `ai/app/rag/retriever.py`
- Modify: `ai/tests/test_rag_retriever.py`

- [ ] **Step 1: Write failing selector-alias tests**

Add:

```python
    def test_bge_selector_aliases_use_ollama_bge_with_lexical_fallback(self):
        for selected in ("bge", "bge_m3", "semantic"):
            with self.subTest(selected=selected):
                adapter = select_retriever_adapter(selected)
                self.assertIsInstance(adapter, OllamaBgeRetrieverAdapter)
                self.assertIsInstance(adapter.fallback, LexicalRetrieverAdapter)

    def test_default_selector_remains_lexical(self):
        self.assertIsInstance(select_retriever_adapter("lexical"), LexicalRetrieverAdapter)
```

- [ ] **Step 2: Run selector tests and verify RED**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_rag_retriever.RagRetrieverTest.test_bge_selector_aliases_use_ollama_bge_with_lexical_fallback tests.test_rag_retriever.RagRetrieverTest.test_default_selector_remains_lexical -v
```

Expected: BGE selector test FAILS because the selector currently returns lexical.

- [ ] **Step 3: Implement selector aliases**

Update `select_retriever_adapter()` before the hybrid branch:

```python
def select_retriever_adapter(kind: str | None = None) -> RetrieverAdapter:
    selected = (kind or os.getenv("AI_REVIEW_RAG_RETRIEVER", "lexical")).lower()
    if selected in {"bge", "bge_m3", "semantic"}:
        return OllamaBgeRetrieverAdapter(fallback=_LEXICAL_ADAPTER)
    if selected.startswith("hybrid"):
        ...
    return _LEXICAL_ADAPTER
```

Do not change the default from `lexical`. Do not add BGE to the hybrid list.

- [ ] **Step 4: Run selector and full retriever tests**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_rag_retriever -v
```

Expected: all tests PASS.

- [ ] **Step 5: Checkpoint without committing**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
git status --short
```

---

### Task 4: Apply Source-Aware Workflow Context Thresholds

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/tests/test_workflow_runner.py`

- [ ] **Step 1: Write failing tests for BGE and lexical score acceptance**

Add imports in `ai/tests/test_workflow_runner.py`:

```python
from app.rag.retriever import RetrievedContext
from app.workflow.nodes import should_use_workflow_context
```

Add tests:

```python
    def test_workflow_accepts_bge_cosine_score_above_semantic_threshold(self):
        context = RetrievedContext(
            "spring-n-plus-one",
            "N+1",
            "context",
            0.75,
            {"retriever": "ollama_bge_m3"},
        )
        self.assertTrue(should_use_workflow_context(context))

    def test_workflow_rejects_bge_cosine_score_below_semantic_threshold(self):
        context = RetrievedContext(
            "spring-n-plus-one",
            "N+1",
            "context",
            0.49,
            {"retriever": "ollama_bge_m3"},
        )
        self.assertFalse(should_use_workflow_context(context))

    def test_workflow_keeps_existing_lexical_threshold(self):
        weak = RetrievedContext("a", "A", "context", 4.99, {})
        strong = RetrievedContext("b", "B", "context", 5.0, {})
        self.assertFalse(should_use_workflow_context(weak))
        self.assertTrue(should_use_workflow_context(strong))
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_workflow_accepts_bge_cosine_score_above_semantic_threshold tests.test_workflow_runner.WorkflowRunnerTest.test_workflow_rejects_bge_cosine_score_below_semantic_threshold tests.test_workflow_runner.WorkflowRunnerTest.test_workflow_keeps_existing_lexical_threshold -v
```

Expected: FAIL because `should_use_workflow_context` does not exist.

- [ ] **Step 3: Implement one shared acceptance function**

In `ai/app/workflow/nodes.py`, add `import os`, then add:

```python
def should_use_workflow_context(context) -> bool:
    if context.metadata.get("retriever") == "ollama_bge_m3":
        minimum = float(os.getenv("AI_REVIEW_BGE_MIN_SCORE", "0.50"))
        return context.score >= minimum
    return context.score >= MIN_WORKFLOW_CONTEXT_SCORE
```

Replace all three direct comparisons:

```python
if context.score >= MIN_WORKFLOW_CONTEXT_SCORE
```

with:

```python
if should_use_workflow_context(context)
```

The replacements must cover:

- `retrieve_context_node()`
- `_matched_concept_id_for_lightweight()`
- `_required_keywords_ok()` fallback retrieval

- [ ] **Step 4: Run focused workflow tests and verify GREEN**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_workflow_runner -v
```

Expected: all workflow runner tests PASS.

- [ ] **Step 5: Prove no direct workflow score comparisons remain**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
rg -n "context\.score >= MIN_WORKFLOW_CONTEXT_SCORE" ai/app/workflow
```

Expected: no matches.

- [ ] **Step 6: Checkpoint without committing**

Run:

```powershell
git diff --check
git status --short
```

---

### Task 5: Add A Live Ollama BGE Smoke Script

**Files:**
- Create: `ai/scripts/smoke_ollama_bge_retriever.py`
- Test: `ai/tests/test_rag_retriever.py`

- [ ] **Step 1: Add a deterministic selector smoke test**

Add:

```python
    def test_bge_retriever_can_be_injected_into_retrieve_context(self):
        adapter = OllamaBgeRetrieverAdapter(
            embed=lambda text: [1.0, 0.0],
            card_loader=lambda: [card("spring-n-plus-one", "N+1", "N+1 문제 설명")],
        )

        results = retrieve_context("의미 질문", limit=1, adapter=adapter)

        self.assertEqual(results[0].concept_id, "spring-n-plus-one")
        self.assertEqual(results[0].metadata["retriever"], "ollama_bge_m3")
```

- [ ] **Step 2: Run the deterministic smoke test**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_rag_retriever.RagRetrieverTest.test_bge_retriever_can_be_injected_into_retrieve_context -v
```

Expected: PASS.

- [ ] **Step 3: Create the live smoke script**

Create `ai/scripts/smoke_ollama_bge_retriever.py`:

```python
from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.rag.retriever import select_retriever_adapter


CASES = [
    ("N+1 문제를 어떻게 해결해?", "spring-n-plus-one"),
    ("aria lable 접근성 설명", "frontend-aria-label"),
    ("ConrollerAdvice 예외 처리", "java-backend-controlleradvice"),
]


def main() -> int:
    adapter = select_retriever_adapter("bge")
    rows = []
    for query, expected in CASES:
        results = adapter.retrieve(query, limit=3)
        top_id = results[0].concept_id if results else ""
        rows.append({"query": query, "expected": expected, "top_id": top_id, "passed": top_id == expected})
    print(json.dumps(rows, ensure_ascii=False, indent=2))
    return 0 if all(row["passed"] for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 4: Run the live smoke script with Ollama**

Prerequisite: Ollama is running and `bge-m3` is installed.

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/smoke_ollama_bge_retriever.py
```

Expected: exit code `0`, and each case has `"passed": true`.

If the command fails due to Ollama/model availability, record the exact failure and do not claim live verification passed.

- [ ] **Step 5: Verify lexical fallback with a deliberately unavailable model**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
$env:AI_REVIEW_EMBEDDING_MODEL="missing-bge-model"
python -c "from app.rag.retriever import select_retriever_adapter; r=select_retriever_adapter('bge').retrieve('N+1', 1); print(r[0].metadata if r else {})"
Remove-Item Env:AI_REVIEW_EMBEDDING_MODEL
```

Expected: returned metadata includes `fallback_from: ollama_bge_m3`.

If this exposes a real implementation bug and it is fixed, add the required `error/YYYY-MM-DD-*.md` record and index entry in the same turn.

---

### Task 6: Document Activation And Runtime Behavior

**Files:**
- Modify: `ai/README.md`
- Modify: `docs/ai-review-runtime-profiles.md`

- [ ] **Step 1: Add the BGE runtime configuration section**

Document this exact activation block:

```powershell
$env:AI_REVIEW_RAG_RETRIEVER="bge"
$env:AI_REVIEW_EMBEDDING_MODEL="bge-m3"
$env:AI_REVIEW_EMBEDDING_TIMEOUT_SECONDS="10"
$env:AI_REVIEW_BGE_MIN_SCORE="0.50"
```

Document these guarantees:

```text
- Normal path: Ollama bge-m3 semantic-only ranking.
- BM25 and lexical scores are not fused into BGE ranking.
- Failure path: lexical fallback with fallback metadata and warning log.
- Default remains lexical unless AI_REVIEW_RAG_RETRIEVER=bge is set.
- Approved/generated knowledge cards remain the retrieval corpus.
```

- [ ] **Step 2: Add verification commands**

Document:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_ollama_embeddings tests.test_rag_retriever tests.test_workflow_runner -v
python scripts/lint_knowledge_cards.py
python scripts/smoke_ollama_bge_retriever.py
python evals/retrieval_poc/evaluate.py --dataset evals/retrieval_poc/golden_dataset.jsonl
```

- [ ] **Step 3: Check documentation diff**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
git status --short
```

Expected: documentation changes are visible and no commit is created.

---

### Task 7: Run Full Regression And Retrieval Quality Verification

**Files:**
- Verify only; modify files only when a real failure is diagnosed and fixed.

- [ ] **Step 1: Run focused tests**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest tests.test_ollama_embeddings tests.test_rag_retriever tests.test_workflow_runner -v
```

Expected: all focused tests PASS.

- [ ] **Step 2: Run the entire AI test suite**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python -m unittest discover -s tests -v
```

Expected: all AI tests PASS.

- [ ] **Step 3: Run knowledge-card lint**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/lint_knowledge_cards.py
```

Expected: exit code `0`, no card validation errors.

- [ ] **Step 4: Run the live Ollama smoke check**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python scripts/smoke_ollama_bge_retriever.py
```

Expected: exit code `0`.

- [ ] **Step 5: Re-run the retrieval PoC golden evaluation**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project\ai
python evals/retrieval_poc/evaluate.py --dataset evals/retrieval_poc/golden_dataset.jsonl
```

Expected: BGE recall@1 remains close to the existing report baseline and materially above BM25/hybrid.

- [ ] **Step 6: Compare implementation behavior with the design acceptance criteria**

Verify each statement with test output or command evidence:

```text
- AI_REVIEW_RAG_RETRIEVER=bge selects OllamaBgeRetrieverAdapter.
- Normal BGE results are not fused with lexical or BM25.
- Unchanged card embeddings are reused.
- Changed card content is re-embedded.
- Ollama/model failure returns lexical fallback metadata.
- BGE score >= 0.50 enters workflow context.
- BGE score < 0.50 is rejected.
- Existing lexical score threshold remains 5.0.
- Intent classification files are unchanged by this implementation.
```

- [ ] **Step 7: Final no-commit checkpoint**

Run:

```powershell
cd C:\Users\User\Desktop\Sub_Project
git diff --check
git status --short --branch
```

Expected: all implementation changes remain uncommitted for user review.

---

## Execution Notes

- Use `superpowers:test-driven-development` during implementation.
- Use `superpowers:systematic-debugging` for any unexpected test or live Ollama failure.
- Use `superpowers:verification-before-completion` before reporting completion.
- Do not edit or discard the existing uncommitted intent PoC work.
- Do not start No-RAG evaluation or BGE intent-classification implementation until this RAG plan is implemented and verified.
