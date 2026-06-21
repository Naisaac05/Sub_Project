---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 하이브리드 리트리버 어댑터 패턴 구현 계획"

---

# AI Review Hybrid Retriever Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardwired lightweight retriever path with an adapter boundary that can host a real hybrid retriever without changing workflow callers.

**Architecture:** Keep `retrieve_context()` as the stable public API, but delegate to a retriever adapter. The default adapter preserves the current lexical/card behavior; the hybrid adapter combines weighted result providers, deduplicates by concept id, and leaves room for BM25/vector/reranker providers to be plugged in later.

**Tech Stack:** Python `unittest`, dataclasses, protocol-like adapter methods, existing concept card JSONL loader.

---

### Task 1: Lock The Adapter Seam With Tests

**Files:**
- Modify: `ai/tests/test_rag_retriever.py`

- [x] **Step 1: Add an injected adapter test**

```python
class FakeAdapter:
    def __init__(self):
        self.calls = []

    def retrieve(self, query: str, limit: int = 5, reranker=None):
        self.calls.append((query, limit, reranker))
        return [
            RetrievedContext(
                concept_id="fake-concept",
                title="Fake",
                content="Fake context",
                score=42.0,
                metadata={"source": "fake"},
            )
        ]


def test_retrieve_context_can_use_injected_adapter(self):
    adapter = FakeAdapter()

    results = retrieve_context("hybrid query", limit=1, adapter=adapter)

    self.assertEqual(results[0].concept_id, "fake-concept")
    self.assertEqual(adapter.calls[0][0], "hybrid query")
    self.assertEqual(adapter.calls[0][1], 1)
```

- [x] **Step 2: Add a hybrid merge/dedupe test**

```python
def test_hybrid_adapter_merges_weighted_sources_and_deduplicates(self):
    lexical = StaticRetriever([
        RetrievedContext("shared", "Lexical", "lexical", 2.0, {"source": "lexical"}),
        RetrievedContext("lexical-only", "Lexical Only", "lexical only", 3.0, {}),
    ])
    semantic = StaticRetriever([
        RetrievedContext("shared", "Semantic", "semantic", 6.0, {"source": "semantic"}),
        RetrievedContext("semantic-only", "Semantic Only", "semantic only", 4.0, {}),
    ])
    adapter = HybridRetrieverAdapter(
        [
            WeightedRetriever("lexical", 1.0, lexical.retrieve),
            WeightedRetriever("semantic", 0.5, semantic.retrieve),
        ]
    )

    results = adapter.retrieve("query", limit=3)

    self.assertEqual([item.concept_id for item in results], ["shared", "lexical-only", "semantic-only"])
    self.assertEqual(results[0].score, 5.0)
    self.assertEqual(results[0].metadata["retriever_sources"], "lexical,semantic")
```

- [x] **Step 3: Run the focused test and confirm it fails**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_rag_retriever -v`

Expected: FAIL because adapter parameters and hybrid classes do not exist yet.

### Task 2: Add Lexical And Hybrid Retriever Adapters

**Files:**
- Modify: `ai/app/rag/retriever.py`

- [x] **Step 1: Add adapter classes**

Implement:
- `RetrieverAdapter`
- `LexicalRetrieverAdapter`
- `WeightedRetriever`
- `HybridRetrieverAdapter`
- `select_retriever_adapter()`

- [x] **Step 2: Delegate `retrieve_context()`**

Update `retrieve_context()` so callers can inject an adapter for tests or let the default selector choose the lexical adapter.

- [x] **Step 3: Run focused tests**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_rag_retriever -v`

Expected: PASS.

### Task 3: Verify Workflow Compatibility

**Files:**
- Test only

- [x] **Step 1: Run retriever-adjacent suites**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_lightweight_evaluator tests.test_workflow_runner tests.test_rag_retriever -v`

Expected: PASS.

- [x] **Step 2: Run golden evaluator**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts\evaluate_lightweight_rag.py`

Expected: metrics remain green, including `workflow_rows`.
