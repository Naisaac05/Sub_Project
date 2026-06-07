# Free Question Follow-Up Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Return a learning follow-up question after `FREE_QUESTION` answers.

**Architecture:** Keep answer generation unchanged and add a deterministic follow-up builder in `RuleBasedAiReviewService`. The builder uses `AiGeneratedAnswer.answerStyle()` and current question context to produce one short prompt for `AiReviewSubmitResponse.nextQuestion`.

**Tech Stack:** Spring Boot service layer, JUnit 5, Mockito, AssertJ.

---

### Task 1: Backend Free-Question Follow-Up

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/ai/RuleBasedAiReviewService.java`
- Modify: `backend/src/test/java/com/devmatch/service/ai/RuleBasedAiReviewServiceTest.java`

**Step 1: Write the failing test**

Add a test that submits `FREE_QUESTION`, stubs `pythonAiReviewClient.answerFreeQuestion`, and asserts `AiReviewSubmitResponse.getNextQuestion()` is not blank.

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest`

Expected: FAIL because free-question responses currently return `nextQuestion == null`.

**Step 3: Write minimal implementation**

In `answerFreeQuestion()`, call `buildFreeQuestionFollowUp(currentQuestion, questionText, generatedAnswer)` and return that value as `nextQuestion`.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest`

Expected: PASS.

**Step 5: Regression check**

Run the AI Python tests already touched by this thread:
`.\.venv\Scripts\python.exe -m unittest tests.test_intent_routing tests.test_ai_intent_classifier`
