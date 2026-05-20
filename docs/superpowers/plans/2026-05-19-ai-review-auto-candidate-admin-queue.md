# AI Review Auto Candidate Admin Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the AI review auto candidate JSONL queue into the existing admin candidate approval UI.

**Architecture:** Keep JSONL files as the source of truth. Spring Boot reads the curated concept candidate file and the auto candidate queue, tags each response with source metadata, and writes review decisions back to the file that owns the candidate. Next.js keeps the current approval screen but shows auto-candidate context so admins can close the feedback loop from captured weak answers.

**Tech Stack:** Spring Boot 3.5 Java 17, Jackson JSONL parsing, JUnit 5 AssertJ, Next.js 14 TypeScript.

---

### Task 1: Backend Multi-Queue Candidate Service

**Files:**
- Modify: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateResponse.java`
- Modify: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateReviewRequest.java`
- Modify: `backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/devmatch/service/AiReviewCandidateAdminServiceTest.java`

- [x] **Step 1: Write failing service tests**

Add tests that create `course_concepts.jsonl` and `auto_candidates.jsonl`, assert `listCandidates()` returns both with source metadata, then approve the auto candidate by `candidateId` and assert only the auto queue file changes.

- [x] **Step 2: Run backend test to verify RED**

Run: `cd backend; .\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest`

Expected: compilation or assertion failure because the DTO/request/service do not expose auto queue metadata yet.

- [x] **Step 3: Implement minimal backend support**

Add `candidateId`, `source`, `needsReviewReason`, `sourceQuestion`, `resolvedQuery`, `route`, `confidenceScore`, and `createdAt` response fields. Add optional `candidateId` to the review request. Inject `app.ai-review.auto-candidates-path`, read both paths, and route review persistence to the owning path.

- [x] **Step 4: Run backend test to verify GREEN**

Run: `cd backend; .\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest`

Expected: test passes.

### Task 2: Admin UI Auto Candidate Context

**Files:**
- Modify: `frontend/src/lib/admin/aiReviewCandidates.ts`
- Modify: `frontend/src/app/admin/ai-review-candidates/page.tsx`

- [x] **Step 1: Update frontend types**

Add the new response fields and include `candidateId` in review requests.

- [x] **Step 2: Render source context**

Show an `Auto`/`Concept` source badge in the table, send `candidateId` when reviewing, and show auto candidate context fields in the detail panel when present.

- [x] **Step 3: Run frontend verification**

Run: `cd frontend; npm.cmd run lint` if available, otherwise `cd frontend; npm.cmd run build`.

Expected: command succeeds or reports only pre-existing unrelated issues.

### Task 3: Final Verification

**Files:**
- Review: changed backend and frontend files.

- [x] **Step 1: Run focused backend tests**

Run: `cd backend; .\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest`

- [x] **Step 2: Run focused frontend verification**

Run the available frontend verification command from Task 2.

- [x] **Step 3: Inspect git diff**

Run: `git diff -- backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateResponse.java backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateReviewRequest.java backend/src/main/java/com/devmatch/service/AiReviewCandidateAdminService.java backend/src/main/resources/application.yml backend/src/test/java/com/devmatch/service/AiReviewCandidateAdminServiceTest.java frontend/src/lib/admin/aiReviewCandidates.ts frontend/src/app/admin/ai-review-candidates/page.tsx docs/superpowers/plans/2026-05-19-ai-review-auto-candidate-admin-queue.md`

Expected: diff is scoped to Phase 15b.
