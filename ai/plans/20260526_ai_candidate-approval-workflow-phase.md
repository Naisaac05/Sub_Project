---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 답변 후보 승인 워크플로우 페이즈(Phase) 연동 계획"

---

# AI Candidate Approval Workflow Phase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit candidate workflow phase axis for captured, drafted, human-review, approved, rejected, and merged states.

**Architecture:** Keep existing `AiReviewCandidateStatus` as the final decision state and add `AiReviewCandidateWorkflowPhase` as review lifecycle state. Update capture and review transitions in `AiReviewCandidateApprovalV2Service`, expose the phase in V2 responses, and document the retrieval exclusion rule.

**Tech Stack:** Spring Boot, JPA, JUnit/Mockito.

---

### Task 1: Phase Model

**Files:**
- Create: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateWorkflowPhase.java`
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java`
- Modify: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateV2Response.java`
- Test: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [ ] Add failing tests for capture without draft → `CAPTURED` and capture with draft → `DRAFTED`.
- [ ] Add enum and entity field.
- [ ] Include `workflowPhase` in response.
- [ ] Set phase during capture.

### Task 2: Human Review Phase

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidateReviewAction.java`
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java`
- Modify: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`
- Test: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [ ] Add failing test for `START_REVIEW` setting `workflowPhase=HUMAN_REVIEW`, reviewer, and audit.
- [ ] Add action and entity transition method.
- [ ] Implement service branch without changing final `status=PENDING`.

### Task 3: Final Phase Transitions and Docs

**Files:**
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewCandidate.java`
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`
- Create: `docs/2026-05-26-ai-review-candidate-approval-workflow.md`

- [ ] Assert approve/reject/merge set phase to matching final phase.
- [ ] Document reviewer/evidence fields and retrieval exclusion.
- [ ] Mark P2 Candidate Approval Workflow TODO complete.
