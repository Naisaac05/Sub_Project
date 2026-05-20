# Phase 4.8 Candidate Approval Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract technical concept candidates from course diagnostic questions, store them as review-only knowledge candidates, and provide a safe approval-to-concept-card promotion path.

**Architecture:** Parse `backend/src/main/java/com/devmatch/config/CourseSkillTestInitializer.java` statically, extract `sq(...)` question and option text, match curated technical terms, and write JSONL records under `ai/app/knowledge/candidates/`. Candidates remain unapproved and are not used by fast path/RAG until a human sets `approved: true` and fills a definition, then the promotion script writes valid concept cards under `ai/app/knowledge/concepts/generated/`.

**Tech Stack:** Python 3.11, unittest, JSONL files, existing AI knowledge directory.

---

### Task 1: Course Question Parser And Extractor

**Files:**
- Create: `ai/app/knowledge/extraction.py`
- Test: `ai/tests/test_course_concept_extraction.py`

- [x] Add tests for parsing `seedCourse(...)` and `sq(...)` snippets.
- [x] Extract category, question order, question text, options, and source path.
- [x] Match curated concept terms and aliases without generating unapproved definitions.

### Task 2: Candidate Writer Script

**Files:**
- Create: `ai/scripts/extract_course_concepts.py`
- Create/Update generated output: `ai/app/knowledge/candidates/course_concepts.jsonl`
- Test: `ai/tests/test_course_concept_extraction.py`

- [x] Add a script that reads `CourseSkillTestInitializer.java`.
- [x] Write deterministic JSONL candidates with `approved: false`.
- [x] Keep duplicate terms merged by term and category.

### Task 3: Approval Promotion Pipeline

**Files:**
- Create: `ai/app/knowledge/approval.py`
- Create: `ai/scripts/promote_concept_candidates.py`
- Test: `ai/tests/test_course_concept_extraction.py`

- [x] Load JSONL candidates deterministically.
- [x] Promote only candidates with `approved: true` and a non-empty `definition`.
- [x] Render promoted candidates as valid concept cards with required metadata and sections.
- [x] Keep unapproved candidates out of active RAG knowledge.

### Task 4: Verification

**Files:**
- Verify only unless tests expose a bug.

- [x] Run `python -m unittest discover -s tests -v` from `ai`.
- [x] Run `python scripts\extract_course_concepts.py` from `ai`.
- [x] Run `python scripts\promote_concept_candidates.py` from `ai`.
- [x] Run `python scripts\lint_knowledge_cards.py` from `ai`.
- [x] Run `python scripts\evaluate_lightweight_rag.py` from `ai`.
- [x] Run `python -m compileall app scripts tests` from `ai`.
