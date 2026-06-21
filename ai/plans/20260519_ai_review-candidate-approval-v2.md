---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 후보 승인 파이프라인 v2 전면 개편 계획"

---

# AI Review Candidate Approval V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add database-backed AI review candidate approval v2 with explicit statuses, reviewer edits, audit history, and retention metadata.

**Architecture:** Keep the existing JSONL v1 service and routes. Add JPA entities/repositories, a v2 service, v2 DTOs, and v2 admin routes under `/api/admin/ai-review/candidates/v2`.

**Tech Stack:** Spring Boot 3, Java 17, Spring Data JPA, JUnit 5, Mockito, existing JSONL candidate service.

---

### Task 1: V2 Domain And Service Contract

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java`
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateAudit.java`
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateStatus.java`
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateSource.java`
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateReviewAction.java`
- Create: `backend/src/main/java/com/devmatch/repository/AiReviewCandidateRepository.java`
- Create: `backend/src/main/java/com/devmatch/repository/AiReviewCandidateAuditRepository.java`
- Create: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`
- Create: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [x] **Step 1: Write failing service tests**

Add tests for `EDIT_AND_APPROVE`, `REJECT`, `MERGE`, and JSONL import dedupe. Run:

`.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`

Expected: FAIL because v2 classes do not exist.

- [x] **Step 2: Implement entities, repositories, and service**

Implement the minimal code to pass the v2 service tests.

- [x] **Step 3: Run focused service tests**

Run:

`.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`

Expected: PASS.

### Task 2: V2 API DTOs And Controller

**Files:**
- Create: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateV2Response.java`
- Create: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateReviewV2Request.java`
- Create: `backend/src/main/java/com/devmatch/controller/AdminAiReviewCandidateV2Controller.java`

- [x] **Step 1: Add DTO and controller tests if existing controller patterns require them**

Use service-focused tests if controller tests are not established for this admin feature.

- [x] **Step 2: Implement v2 DTOs and controller**

Expose list, import-jsonl, and review endpoints.

- [x] **Step 3: Run focused service tests again**

Run:

`.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`

Expected: PASS.

### Task 3: Regression Verification

**Files:**
- Test only

- [x] **Step 1: Run v1 and v2 candidate tests**

Run:

`.\gradlew.bat test --tests com.devmatch.service.AiReviewCandidateAdminServiceTest --tests com.devmatch.service.AiReviewCandidateApprovalV2ServiceTest`

Expected: PASS.
