---
type: plan
category: evaluation
status: active
updated: 2026-06-18
description: "의도 분류(Intent) PoC 3단계 파이프라인 평가 도입 플랜"

---

# Intent PoC Three-Stage Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit dev-trained holdout evaluation and golden final evaluation for the intent PoC.

**Architecture:** Keep centroid fitting inside `classifiers.py` unchanged because it already reads only `dataset.jsonl` rows where `split == "dev"`. Extend evaluation with split filtering and add a focused golden evaluator that maps golden 4-intent/sub-intent labels into the PoC 10-class taxonomy before scoring.

**Tech Stack:** Python standard library, pytest, JSONL fixtures, existing `ai/evals/intent_poc` classifier registry.

---

### Task 1: Split-Filtered Dataset Evaluation

**Files:**
- Modify: `ai/evals/intent_poc/evaluate.py`
- Test: `ai/tests/test_intent_poc_evaluation.py`

- [ ] **Step 1: Write the failing test**

```python
def test_filter_rows_by_split_keeps_only_requested_split():
    from evals.intent_poc.evaluate import filter_rows_by_split

    rows = [
        {"id": "a", "split": "dev"},
        {"id": "b", "split": "holdout"},
        {"id": "c", "split": "holdout"},
    ]

    assert [row["id"] for row in filter_rows_by_split(rows, "holdout")] == ["b", "c"]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai/tests/test_intent_poc_evaluation.py::test_filter_rows_by_split_keeps_only_requested_split -q`
Expected: FAIL because `filter_rows_by_split` does not exist.

- [ ] **Step 3: Write minimal implementation**

```python
def filter_rows_by_split(rows, split):
    if split is None:
        return list(rows)
    return [row for row in rows if row.get("split") == split]
```

- [ ] **Step 4: Wire CLI**

Add `--split {dev,holdout}` to `evaluate.py`, filter loaded rows, and include the selected split in report metadata.

- [ ] **Step 5: Run test to verify it passes**

Run: `python -m pytest ai/tests/test_intent_poc_evaluation.py::test_filter_rows_by_split_keeps_only_requested_split -q`
Expected: PASS.

### Task 2: Golden Intent Label Normalization

**Files:**
- Create: `ai/evals/intent_poc/evaluate_golden.py`
- Test: `ai/tests/test_intent_poc_evaluation.py`

- [ ] **Step 1: Write the failing test**

```python
def test_golden_expected_label_uses_sub_intent_when_needed():
    from evals.intent_poc.evaluate_golden import expected_label_from_golden

    assert expected_label_from_golden({"expected_intent": "concept_definition"}) == "CONCEPT_DEFINITION"
    assert expected_label_from_golden({"expected_intent": "concept_definition", "expected_sub_intent": "comparison"}) == "COMPARISON"
    assert expected_label_from_golden({"expected_intent": "follow_up", "expected_sub_intent": "follow_up"}) == "FOLLOW_UP"
    assert expected_label_from_golden({"expected_intent": "wrong_answer_explanation", "expected_sub_intent": "explanation"}) == "WRONG_ANSWER_REASON"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest ai/tests/test_intent_poc_evaluation.py::test_golden_expected_label_uses_sub_intent_when_needed -q`
Expected: FAIL because `evaluate_golden.py` does not exist.

- [ ] **Step 3: Write minimal implementation**

```python
GOLDEN_SUB_INTENT_MAP = {
    ("concept_definition", "comparison"): "COMPARISON",
    ("concept_definition", "practical"): "PRACTICAL_USAGE",
    ("concept_definition", "definition"): "CONCEPT_DEFINITION",
    ("concept_definition", "related"): "CONCEPT_DEFINITION",
    ("wrong_answer_explanation", "explanation"): "WRONG_ANSWER_REASON",
    ("follow_up", "follow_up"): "FOLLOW_UP",
}

GOLDEN_INTENT_DEFAULT = {
    "concept_definition": "CONCEPT_DEFINITION",
    "wrong_answer_explanation": "WRONG_ANSWER_REASON",
    "follow_up": "FOLLOW_UP",
    "general_question": "UNKNOWN",
}

def expected_label_from_golden(row):
    key = (row.get("expected_intent"), row.get("expected_sub_intent"))
    return GOLDEN_SUB_INTENT_MAP.get(key, GOLDEN_INTENT_DEFAULT.get(row.get("expected_intent"), "UNKNOWN"))
```

- [ ] **Step 4: Add golden evaluator CLI**

Load `ai/evals/golden_dataset.jsonl`, score rows with `expected_intent`, write `REPORT_golden_<classifier>.md`, and print `classifier`, `total`, `acc`, and report path.

- [ ] **Step 5: Run tests**

Run: `python -m pytest ai/tests/test_intent_poc_evaluation.py -q`
Expected: PASS.

### Task 3: Documentation and Verification

**Files:**
- Modify: `ai/evals/intent_poc/README.md`

- [ ] **Step 1: Document the three commands**

```bash
python evals/intent_poc/evaluate.py --classifier embed --split dev --out evals/intent_poc/REPORT_embed_dev.md
python evals/intent_poc/evaluate.py --classifier embed --split holdout --out evals/intent_poc/REPORT_embed_holdout.md
python evals/intent_poc/evaluate_golden.py --classifier embed
```

- [ ] **Step 2: Run non-embedding tests**

Run: `python -m pytest ai/tests/test_intent_poc_evaluation.py -q`
Expected: PASS.

- [ ] **Step 3: Run lightweight classifier smoke**

Run: `python evals/intent_poc/evaluate.py --classifier rule10 --split holdout --out evals/intent_poc/REPORT_rule10_holdout.md`
Expected: command exits 0 and prints holdout-only totals.

- [ ] **Step 4: Run golden smoke**

Run: `python evals/intent_poc/evaluate_golden.py --classifier rule10 --out evals/intent_poc/REPORT_golden_rule10.md`
Expected: command exits 0 and prints golden totals.
