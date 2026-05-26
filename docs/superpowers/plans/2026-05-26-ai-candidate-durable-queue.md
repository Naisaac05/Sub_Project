# AI Candidate Durable Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route runtime AI review candidate capture into the existing Spring DB-backed candidate queue and make JSONL capture explicit dev-only fallback.

**Architecture:** Spring exposes an internal capture endpoint backed by `AiReviewCandidateApprovalV2Service`. Python sends the existing auto-candidate payload to this endpoint by default, while `AI_REVIEW_CANDIDATE_SINK=jsonl` preserves local JSONL behavior for development.

**Tech Stack:** Spring Boot, JPA, JUnit/Mockito, FastAPI-side Python workflow, pytest.

---

### Task 1: Spring DB Capture Service

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/AiReviewCandidateApprovalV2Service.java`
- Create: `backend/src/main/java/com/devmatch/dto/aireview/candidate/AiReviewCandidateCaptureRequest.java`
- Test: `backend/src/test/java/com/devmatch/service/AiReviewCandidateApprovalV2ServiceTest.java`

- [ ] Add a failing test that calls `captureCandidate()` with an auto-candidate payload and expects `status=PENDING`, `source=AUTO`, and dedupe by `externalCandidateId`.
- [ ] Implement `AiReviewCandidateCaptureRequest`.
- [ ] Implement `captureCandidate()` using existing duplicate and draft-update rules.
- [ ] Verify the service test passes.

### Task 2: Spring Internal Capture Endpoint

**Files:**
- Create: `backend/src/main/java/com/devmatch/controller/InternalAiReviewCandidateCaptureController.java`
- Test: service-level coverage from Task 1 is primary; controller remains thin.

- [ ] Add `POST /api/internal/ai-review/candidates/capture`.
- [ ] Return the V2 candidate response from `captureCandidate()`.
- [ ] Leave authentication hardening for the existing production config hardening TODO unless an existing internal-token filter is present.

### Task 3: Python Candidate Sink

**Files:**
- Create: `ai/app/knowledge/candidate_sink.py`
- Modify: `ai/app/workflow/graph.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] Add a failing test proving default capture calls HTTP sink and does not append JSONL.
- [ ] Add a failing test proving `AI_REVIEW_CANDIDATE_SINK=jsonl` keeps JSONL behavior.
- [ ] Add a failing test proving HTTP failure adds `candidate_capture_failed`.
- [ ] Implement `save_auto_candidate()` with `http`, `jsonl`, and `off` modes.
- [ ] Wire `candidate_save_node()` to `save_auto_candidate()`.

### Task 4: Docs and TODO

**Files:**
- Modify: `docs/2026-05-26-ai-inference-orchestration-todolist.md`
- Create: `docs/2026-05-26-ai-review-candidate-durable-queue.md`

- [ ] Mark `JSONL runtime queue 제거 또는 dev-only로 제한`.
- [ ] Mark `candidate status lifecycle 정의` using existing `PENDING`, `APPROVED`, `REJECTED`, `MERGED` plus pending capture docs.
- [ ] Mark `audit field 추가` if capture/update/review metadata is documented and existing audit table remains review-transition audit.
- [ ] Document env vars and verification commands.
