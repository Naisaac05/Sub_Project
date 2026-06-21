---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "Lightweight 자유 질문 의도 라우팅(Intent Routing) 구현 계획"

---

# Lightweight Free-Question Intent Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve Phase 4.7 lightweight workflow so free-question answers follow the learner's latest question even when the question branches away from the original diagnostic problem.

**Architecture:** Add a small deterministic intent router before retrieval. The router classifies free questions, chooses a RAG policy, and gives validation enough metadata to reject answers that drift back to stale original context.

**Tech Stack:** Python 3.11, FastAPI AI service, unittest, existing lightweight RAG/retriever modules, local Ollama.

---

### Task 1: Add Intent Routing Data Model

**Files:**
- Modify: `ai/app/workflow/state.py`
- Create: `ai/app/workflow/intent.py`
- Test: `ai/tests/test_intent_routing.py`

- [ ] **Step 1: Write failing tests**

```python
def test_korean_concept_definition_uses_latest_question_only():
    result = classify_free_question("분산환경이 어떤 환경을 의미하는 것인가요?")
    self.assertEqual(result.intent, "concept_definition")
    self.assertEqual(result.rag_policy, "latest_question_only")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_intent_routing -v`
Expected: import failure because `app.workflow.intent` does not exist yet.

- [ ] **Step 3: Implement minimal classifier**

Create `FreeQuestionIntent` with fields `intent`, `rag_policy`, and `requires_direct_answer_to`. Implement deterministic rules for concept definitions, comparisons, practical application, original-problem reasoning, and vague clarifications.

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_intent_routing -v`
Expected: OK.

### Task 2: Use Intent Routing in Retrieval

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] **Step 1: Write failing tests**

Add tests proving Korean concept questions such as `분산환경...`, comparison questions such as `페이지네이션이랑 무한스크롤 차이가 뭐야?`, and practical questions such as `실무에서는 어떻게 처리해?` do not blindly retrieve N+1 cards from the original problem.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_workflow_runner.WorkflowRunnerTest -v`
Expected: retrieval contains stale original concepts before implementation.

- [ ] **Step 3: Implement retrieval policy**

In `retrieve_context_node`, classify the request first. Use latest-question-only for standalone concept/comparison/practical questions. Use original-context-mixed only for vague clarifications and original-problem reasoning.

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_workflow_runner.WorkflowRunnerTest -v`
Expected: OK.

### Task 3: Add Answer Relevance Validation

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/state.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] **Step 1: Write failing tests**

Add a test where the generator answers `지연 로딩은...` to `분산환경이 뭐야?`; validation should reject it and use a direct-answer fallback or second-pass prompt path.

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_free_question_rejects_stale_original_context_answer -v`
Expected: FAIL before implementation.

- [ ] **Step 3: Implement minimal relevance check**

For standalone free questions, require the answer to include the normalized topic keyword or a known synonym from the intent classifier. Penalize answers that mention stale original concepts such as `지연 로딩`, `N+1`, or `fetch join` when the latest question topic is unrelated.

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_free_question_rejects_stale_original_context_answer -v`
Expected: OK.

### Task 4: Verify Live Server Behavior

**Files:**
- Modify only if verification reveals a defect.

- [ ] **Step 1: Run unit, lint, evaluator**

Run:
`python -m unittest discover -s tests -v`
`python scripts\lint_knowledge_cards.py`
`python scripts\evaluate_lightweight_rag.py`
Expected: all pass.

- [ ] **Step 2: Restart Python AI server**

Restart `uvicorn app.main:app --host 127.0.0.1 --port 8001 --reload`.

- [ ] **Step 3: Verify live endpoint**

POST `/api/review/free-question` with:
`user_answer="분산환경이 어떤 환경을 의미하는 것인가요?"`
Expected metadata: `fallback_used=false`, `retrieved_concept_ids=[]`, answer directly explains `분산환경`.

