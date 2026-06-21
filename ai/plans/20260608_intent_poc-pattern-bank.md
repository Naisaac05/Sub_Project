---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "의도 분류(Intent PoC) 패턴 뱅크 및 룰셋 구축 계획"

---

# Intent PoC Pattern Bank Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Broaden intent dataset generation with taxonomy-level question patterns without copying golden questions or golden concepts into centroid fitting data.

**Architecture:** Keep the existing seed/manual augmentation path intact. Add a pattern-bank generator in `ai/evals/intent_poc/augment.py` that combines intent-specific pattern archetypes with a small non-golden topic pool, then appends those rows to `dataset.jsonl` with stable ids and balanced dev/holdout splits.

**Tech Stack:** Python standard library, pytest, JSONL fixtures, existing `ai/evals/intent_poc` scripts.

---

### Task 1: Pattern Bank Tests

**Files:**
- Modify: `ai/tests/test_intent_poc_evaluation.py`
- Modify: `ai/evals/intent_poc/augment.py`

- [ ] **Step 1: Write failing tests**

Add tests that import `augment.py` and verify:

```python
def test_pattern_rows_include_definition_question_archetypes():
    rows = augment.build_pattern_rows()
    definition_archetypes = {
        row["pattern_archetype"]
        for row in rows
        if row["expected_intent"] == "CONCEPT_DEFINITION"
    }
    assert {"pure_definition", "purpose", "cause", "mechanism", "relationship", "beginner_vague"} <= definition_archetypes
```

```python
def test_pattern_rows_do_not_copy_known_golden_questions_or_concepts():
    rows = augment.build_pattern_rows()
    questions = {row["question"] for row in rows}
    assert "N+1 문제는 뭐야?" not in questions
    assert "REST API가 뭐야?" not in questions
    assert all("N+1" not in row["question"] for row in rows)
    assert all("REST API" not in row["question"] for row in rows)
```

```python
def test_pattern_rows_have_balanced_splits():
    rows = augment.build_pattern_rows()
    assert {row["split"] for row in rows} == {"dev", "holdout"}
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `.\.venv\Scripts\python.exe -m pytest tests\test_intent_poc_evaluation.py -q`
Expected: FAIL because `build_pattern_rows` does not exist.

- [ ] **Step 3: Implement pattern bank**

Add `PATTERN_TOPICS`, `INTENT_PATTERNS`, and `build_pattern_rows()` to `augment.py`. Use non-golden topics such as `트랜잭션`, `인덱스`, `캐시`, `스레드`, `커넥션 풀`, and `동시성 제어`.

- [ ] **Step 4: Append pattern rows in `main()`**

After existing seed rows are generated, append `build_pattern_rows()` before writing `dataset.jsonl`.

- [ ] **Step 5: Verify**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_intent_poc_evaluation.py -q
.\.venv\Scripts\python.exe evals\intent_poc\augment.py
.\.venv\Scripts\python.exe evals\intent_poc\evaluate.py --classifier embed --split holdout --out evals\intent_poc\REPORT_embed_holdout.md
.\.venv\Scripts\python.exe evals\intent_poc\evaluate_golden.py --classifier embed --out evals\intent_poc\REPORT_golden_embed.md
```
