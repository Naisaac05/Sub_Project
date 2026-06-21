---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 답변 후보 승인 프로세스 확장 플랜"

---

# AI Review Candidate Approval Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Phase 4.8 into an AI-assisted review, duplicate detection, human approval, and admin UI workflow.

**Architecture:** Keep JSONL candidates as the source of truth for this phase. Python owns candidate enrichment, duplicate checks, CLI review, and promotion; Spring Boot exposes JSONL candidates through admin DTO/API; Next.js provides a focused admin review screen.

**Tech Stack:** Python 3.11 unittest, Spring Boot 3.5 Java 17, Next.js 14 TypeScript, JSONL.

---

### Task 1: Phase 4.8b AI Draft And Critic Metadata

**Files:**
- Create: `ai/app/knowledge/review.py`
- Create: `ai/scripts/draft_candidate_reviews.py`
- Modify: `ai/tests/test_course_concept_extraction.py`

- [x] Add RED tests for draft and critic metadata.
- [x] Add template review provider with `definition_draft`, `draft_*`, `critic_*`, `sources`, and `rejected_reason`.
- [x] Add batch CLI that enriches unapproved candidates without approving them.

### Task 2: Phase 4.8c Duplicate And Global Registry Check

**Files:**
- Create: `ai/app/knowledge/registry.py`
- Create: `ai/scripts/check_candidate_duplicates.py`
- Modify: `ai/tests/test_course_concept_extraction.py`

- [x] Add RED test for detecting candidate overlap with existing concept cards.
- [x] Mark `duplicate_status`, `duplicate_concept_ids`, and `duplicate_reason`.
- [x] Add CLI that writes duplicate metadata back into `course_concepts.jsonl`.

### Task 3: Phase 4.8d Human Review CLI

**Files:**
- Create: `ai/app/knowledge/human_review.py`
- Create: `ai/scripts/review_concept_candidate.py`
- Modify: `ai/tests/test_course_concept_extraction.py`

- [x] Add RED tests for approve and reject transitions.
- [x] Implement approve/reject/hold state changes.
- [x] Add CLI for reviewing by `term` and optional `category`.

### Task 4: Phase 5 Spring Boot DTO/API

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateResponse.java`
- Create: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateReviewRequest.java`
- Create: `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java`
- Create: `backend/src/main/java/com/devmatch/controller/AdminAiReviewCandidateController.java`
- Create: `backend/src/test/java/com/devmatch/service/AiReviewCandidateAdminServiceTest.java`
- Modify: `backend/src/main/resources/application.yml`

- [x] Add RED service tests for JSONL list and approval persistence.
- [x] Expose `GET /api/admin/ai-review/candidates`.
- [x] Expose `POST /api/admin/ai-review/candidates/review`.
- [x] Add configurable `app.ai-review.candidates-path`.

### Task 5: Phase 5 Admin UI

**Files:**
- Create: `frontend/src/lib/admin/aiReviewCandidates.ts`
- Create: `frontend/src/app/admin/ai-review-candidates/page.tsx`
- Modify: `frontend/src/components/admin/AdminSidebar.tsx`

- [x] Add API client types and functions.
- [x] Add admin review page with risk filters, draft editor, critic feedback, duplicate warning, and review actions.
- [x] Add sidebar navigation entry.

### Task 6: Verification

- [x] Run `python -m unittest discover -s tests -v`.
- [x] Run `python scripts\draft_candidate_reviews.py --limit 10`.
- [x] Run `python scripts\check_candidate_duplicates.py`.
- [x] Run `python scripts\promote_concept_candidates.py`.
- [x] Run `python scripts\lint_knowledge_cards.py`.
- [x] Run `python scripts\evaluate_lightweight_rag.py`.
- [x] Run `python -m compileall app scripts tests`.
- [x] Run `.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest`.
- [x] Run `npm.cmd run build`.
