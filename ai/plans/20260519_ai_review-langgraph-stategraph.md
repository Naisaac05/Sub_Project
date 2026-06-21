---
type: plan
category: langgraph
status: active
updated: 2026-06-18
description: "AI 리뷰 LangGraph StateGraph 도입 및 워크플로우 제어 계획"

---

# AI Review LangGraph StateGraph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the AI review workflow through a real LangGraph `StateGraph` when optional dependencies are installed, while preserving the current public runner contract.

**Architecture:** Add a graph builder module that defines named nodes and edges around the existing workflow node functions. Keep optional dependency tolerance by importing LangGraph in the builder and falling back to the existing sequential runner when it is unavailable.

**Tech Stack:** Python `unittest`, optional `langgraph==0.4.5`, existing dataclass workflow state, existing JSONL auto-candidate queue.

---

### Task 1: Graph Builder Contract

**Files:**
- Create: `ai/app/workflow/graph.py`
- Modify: `ai/tests/test_workflow_runner.py`

- [x] **Step 1: Write failing tests for graph metadata**

Add tests that import `build_review_state_graph`, `LANGGRAPH_AVAILABLE`, and `WORKFLOW_NODE_NAMES`, then assert the node list includes `candidate_save`, `error_state`, and `dead_end_state`.

- [x] **Step 2: Run focused workflow tests**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_workflow_runner -v`

Expected: FAIL because `app.workflow.graph` does not exist.

### Task 2: StateGraph Execution Adapter

**Files:**
- Create: `ai/app/workflow/graph.py`
- Modify: `ai/app/workflow/state.py`
- Modify: `ai/app/workflow/runner.py`

- [x] **Step 1: Implement graph builder**

Create `build_review_state_graph(generator)` with real `langgraph.graph.StateGraph` usage when available. Add nodes in order: `retrieve_context`, `rule_evaluate`, `generate_answer`, `validate_answer`, `confidence_gate`, `fallback_answer`, `cache_answer`, `candidate_save`.

- [x] **Step 2: Implement fallback sequential executor**

Move the current runner sequence into `_run_sequential_workflow()` so `run_review_workflow()` can choose compiled graph execution when LangGraph is available and sequential execution otherwise.

- [x] **Step 3: Run focused workflow tests**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_workflow_runner -v`

Expected: PASS.

### Task 3: Candidate Save Node And Error State

**Files:**
- Modify: `ai/app/workflow/graph.py`
- Modify: `ai/app/workflow/state.py`
- Modify: `ai/tests/test_workflow_runner.py`

- [x] **Step 1: Write failing tests for candidate node and graph fallback metadata**

Add tests that call `candidate_save_node()` with a low-confidence free-question state and assert `candidate_id` is set when the JSONL append succeeds. Add a dead-end/error-state test that verifies fallback output and route metadata.

- [x] **Step 2: Implement candidate/dead-end helpers**

Add `cache_answer_node`, `candidate_save_node`, `dead_end_state_node`, and `error_state_node`. Store `candidate_id` and `graph_status` on `ReviewWorkflowState`.

- [x] **Step 3: Verify focused tests**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_workflow_runner -v`

Expected: PASS.

### Task 4: Regression Verification

**Files:**
- Test only

- [x] **Step 1: Run workflow/evaluator/retriever suite**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest tests.test_lightweight_evaluator tests.test_workflow_runner tests.test_rag_retriever -v`

Expected: PASS.

- [x] **Step 2: Run golden evaluator**

Run: `C:\Users\User\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe scripts\evaluate_lightweight_rag.py`

Expected: evaluator metrics remain green.
