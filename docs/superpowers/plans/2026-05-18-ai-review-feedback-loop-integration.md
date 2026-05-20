# AI Review Feedback Loop Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect Python AI quality metadata to Spring persistence and the review UI so weak answers can be traced and turned into knowledge candidates.

**Architecture:** Preserve the existing answer flow and add metadata alongside AI messages. Python responses become a small typed result object in Spring, AI messages store route/style/quality/candidate metadata, and frontend message types render compact diagnostic badges for AI messages.

**Tech Stack:** Spring Boot 3, JPA/Hibernate, Java 17, Next.js/TypeScript, existing Python AI server.

---

### Task 1: Persist Python AI Metadata

**Files:**
- Create: `backend/src/main/java/com/devmatch/service/ai/AiGeneratedAnswer.java`
- Modify: `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java`
- Modify: `backend/src/main/java/com/devmatch/entity/AiReviewMessage.java`
- Modify: `backend/src/main/java/com/devmatch/dto/aireview/AiReviewMessageResponse.java`
- Modify: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`
- Test: `backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java`

- [x] Add metadata fields for route, resolved query, correction type, matched concept id, answer style, quality flags, candidate id, and latency.
- [x] Change Python AI client methods to return `AiGeneratedAnswer` while preserving rule/Ollama fallbacks.
- [x] Save metadata only on AI messages generated from Python responses.
- [x] Expose metadata through `AiReviewMessageResponse`.

### Task 2: Frontend Metadata Display

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/app/tests/results/[id]/review/page.tsx`

- [x] Add optional AI metadata fields to `AiReviewMessageResponse`.
- [x] Render compact badges for route, answer style, quality flags, candidate id, and latency on AI messages.
- [x] Keep learner-facing content dominant and diagnostics visually secondary.

### Task 3: Verification

**Files:**
- Existing backend/frontend test commands

- [x] Run focused backend service test.
- [x] Run backend compile/test target that covers changed Java classes.
- [x] Run frontend type/lint check if available.
