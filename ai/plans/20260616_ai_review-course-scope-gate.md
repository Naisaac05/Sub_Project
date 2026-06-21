---
type: plan
category: inference
status: active
updated: 2026-06-18
description: "AI 리뷰 특정 코스 답변 범위를 제한하는 Scope Gate 구현 계획"

---

# AI Review Course Scope Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep AI review free-question answers inside the current course/test scope, blocking out-of-course technical questions.

**Architecture:** Backend sends course/test/question metadata to Python. Python computes a deterministic course scope before v2 fast path or Ollama generation, restricts approved v2 card search to the current course, and returns redirect routes for out-of-course questions. Backend follow-up support skips learning follow-ups for out-of-course redirects.

**Tech Stack:** Java Spring Boot, Python Pydantic workflow, unittest, Gradle/JUnit.

---

## File Structure

- Modify `ai/app/schemas/__init__.py`: add request metadata fields.
- Create `ai/app/workflow/course_scope.py`: normalize course ids, inspect approved v2 cards, classify current-course vs out-of-course scope.
- Modify `ai/app/workflow/nodes.py`: call scope gate before v2 fast path and before generation.
- Modify `ai/app/workflow/runner.py`: apply the same scope gate in streaming path.
- Modify `ai/app/workflow/v2_approved_fast_path.py`: accept an optional card id allowlist so current-course cards can be searched only.
- Modify `ai/app/knowledge/auto_candidates.py`: block candidate capture for out-of-course and scope-unknown routes.
- Modify `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java`: include course/test/question metadata in `PythonAiRequest`.
- Modify `backend/src/main/java/com/devmatch/service/ai/AiReviewFollowUpSupport.java`: skip follow-up for `out_of_course_redirect`.
- Test `ai/tests/test_course_scope_gate.py`: unit tests for normalization and scope decisions.
- Test `ai/tests/test_workflow_runner.py`: workflow tests for out-of-course blocking and current-course allowance.
- Test `backend/src/test/java/com/devmatch/service/ai/PythonAiReviewClientTest.java` or existing service tests: request metadata is sent.
- Test `backend/src/test/java/com/devmatch/service/ai/AiReviewFollowUpSupportTest.java`: out-of-course answer does not append follow-up.

---

### Task 1: Python Request Metadata

**Files:**
- Modify: `ai/app/schemas/__init__.py`
- Test: `ai/tests/test_course_scope_gate.py`

- [ ] **Step 1: Write failing schema test**

Create `ai/tests/test_course_scope_gate.py` with:

```python
import unittest

from app.schemas import AiGenerateRequest


class CourseScopeGateTest(unittest.TestCase):
    def test_ai_generate_request_accepts_course_scope_metadata(self):
        request = AiGenerateRequest(
            user_answer="@Transactional이 뭐야?",
            course_id="frontend",
            test_id="10",
            question_id="4",
            source_question_id="frontend:4",
        )

        self.assertEqual(request.course_id, "frontend")
        self.assertEqual(request.test_id, "10")
        self.assertEqual(request.question_id, "4")
        self.assertEqual(request.source_question_id, "frontend:4")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run RED**

Run: `cd ai; .\.venv\Scripts\python.exe -m unittest tests.test_course_scope_gate.CourseScopeGateTest.test_ai_generate_request_accepts_course_scope_metadata`

Expected: FAIL because `AiGenerateRequest` has no metadata fields.

- [ ] **Step 3: Add metadata fields**

In `AiGenerateRequest`, add:

```python
    course_id: str = ""
    test_id: str = ""
    question_id: str = ""
    source_question_id: str = ""
```

Add those four field names to the existing `none_to_empty_string` validator list.

- [ ] **Step 4: Run GREEN**

Run the same command. Expected: PASS.

---

### Task 2: Course Scope Gate Unit

**Files:**
- Create: `ai/app/workflow/course_scope.py`
- Test: `ai/tests/test_course_scope_gate.py`

- [ ] **Step 1: Add failing scope tests**

Append to `CourseScopeGateTest`:

```python
from app.schemas.rag_card import CardStatus, PayloadStatus, RagCard, RagReview
from app.workflow.course_scope import (
    CourseScopeDecision,
    cards_for_course,
    normalize_course_id,
    resolve_course_scope,
)
from app.workflow.intent import FreeQuestionIntent


def _card(card_id, category, source_ids):
    return RagCard(
        card_id=card_id,
        category=category,
        term=card_id,
        source_question_ids=source_ids,
        review=RagReview(
            card_status=CardStatus.APPROVED,
            payload_status={"CONCEPT_DEFINITION": PayloadStatus.APPROVED},
        ),
    )


class CourseScopeGateTest(unittest.TestCase):
    ...

    def test_normalize_course_id_maps_existing_course_names(self):
        self.assertEqual(normalize_course_id("JAVA-BASIC"), "java")
        self.assertEqual(normalize_course_id("spring-backend"), "spring")
        self.assertEqual(normalize_course_id("react"), "frontend")
        self.assertEqual(normalize_course_id("coding-test"), "algorithm")

    def test_cards_for_course_uses_category_and_source_question_prefix(self):
        cards = [
            _card("frontend-useeffect", "frontend", ["frontend:4"]),
            _card("spring-transactional", "spring", ["spring:2"]),
        ]

        self.assertEqual(
            [card.card_id for card in cards_for_course(cards, "frontend")],
            ["frontend-useeffect"],
        )

    def test_out_of_course_technical_card_match_is_blocked(self):
        cards = [
            _card("spring-transactional", "spring", ["spring:2"]),
        ]
        decision = resolve_course_scope(
            query="@Transactional이 뭐야?",
            course_id="frontend",
            intent=FreeQuestionIntent("concept_definition", "latest_question_only", "@Transactional", 0.95, False, "definition"),
            approved_cards=cards,
        )

        self.assertEqual(decision.scope, "out_of_course_tech")
        self.assertEqual(decision.matched_card_id, "spring-transactional")
```

- [ ] **Step 2: Run RED**

Run: `cd ai; .\.venv\Scripts\python.exe -m unittest tests.test_course_scope_gate`

Expected: FAIL because `app.workflow.course_scope` does not exist.

- [ ] **Step 3: Implement course scope module**

Create `ai/app/workflow/course_scope.py`:

```python
from __future__ import annotations

from dataclasses import dataclass
import re

from app.rag.retriever import LexicalRetrieverAdapter
from app.schemas.rag_card import RagCard
from app.workflow.intent import FreeQuestionIntent


@dataclass(frozen=True)
class CourseScopeDecision:
    scope: str
    current_course: str
    allowed_card_ids: frozenset[str]
    matched_card_id: str | None = None
    reason: str = ""


COURSE_ALIASES = {
    "java": "java",
    "java-basic": "java",
    "java-backend": "java",
    "spring": "spring",
    "spring-backend": "spring",
    "frontend": "frontend",
    "react": "frontend",
    "nextjs": "frontend",
    "next-js": "frontend",
    "python": "python",
    "python-backend": "python",
    "algorithm": "algorithm",
    "coding-test": "algorithm",
}


def normalize_course_id(value: str | None) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", (value or "").strip().lower()).strip("-")
    return COURSE_ALIASES.get(normalized, normalized)


def card_courses(card: RagCard) -> set[str]:
    courses = {normalize_course_id(card.category)}
    for source_id in card.source_question_ids:
        prefix = str(source_id).split(":", 1)[0]
        normalized = normalize_course_id(prefix)
        if normalized:
            courses.add(normalized)
    return {course for course in courses if course}


def cards_for_course(cards: list[RagCard], course_id: str | None) -> list[RagCard]:
    current = normalize_course_id(course_id)
    if not current:
        return []
    return [card for card in cards if current in card_courses(card)]


def resolve_course_scope(
    *,
    query: str,
    course_id: str | None,
    intent: FreeQuestionIntent | None,
    approved_cards: list[RagCard],
) -> CourseScopeDecision:
    current = normalize_course_id(course_id)
    if not current:
        return CourseScopeDecision("scope_unknown", "", frozenset(), reason="missing_course_id")
    allowed = cards_for_course(approved_cards, current)
    allowed_ids = frozenset(card.card_id for card in allowed)
    if intent is None or intent.intent in {"off_topic", "unknown"}:
        return CourseScopeDecision("not_applicable", current, allowed_ids, reason="unsupported_intent")
    if intent.intent not in {"concept_definition", "wrong_answer_explanation", "follow_up"}:
        return CourseScopeDecision("not_applicable", current, allowed_ids, reason="unsupported_intent")
    all_hits = LexicalRetrieverAdapter(card_loader=lambda: approved_cards).retrieve(query, limit=1)
    if not all_hits:
        return CourseScopeDecision("course_card_miss", current, allowed_ids, reason="no_card_hit")
    top_card_id = all_hits[0].concept_id
    if top_card_id in allowed_ids:
        return CourseScopeDecision("course_card_hit", current, allowed_ids, top_card_id, "current_course_card")
    return CourseScopeDecision("out_of_course_tech", current, allowed_ids, top_card_id, "matched_other_course_card")
```

- [ ] **Step 4: Run GREEN**

Run: `cd ai; .\.venv\Scripts\python.exe -m unittest tests.test_course_scope_gate`

Expected: PASS.

---

### Task 3: Apply Scope Gate in Python Workflow

**Files:**
- Modify: `ai/app/workflow/nodes.py`
- Modify: `ai/app/workflow/runner.py`
- Modify: `ai/app/workflow/v2_approved_fast_path.py`
- Test: `ai/tests/test_workflow_runner.py`

- [ ] **Step 1: Add failing workflow tests**

Add tests to `WorkflowRunnerTest`:

```python
    def test_out_of_course_technical_question_redirects_without_generation(self):
        def forbidden_generator(**kwargs):
            raise AssertionError("out-of-course technical question must not call Ollama")

        with patch(
            "app.workflow.nodes.classify_free_question_with_embeddings",
            return_value=intent_from_label("CONCEPT_DEFINITION", "@Transactional이 뭐야?", confidence=0.95),
        ), patch(
            "app.workflow.course_scope.resolve_course_scope",
            return_value=__import__("app.workflow.course_scope", fromlist=["CourseScopeDecision"]).CourseScopeDecision(
                "out_of_course_tech",
                "frontend",
                frozenset({"frontend-useeffect"}),
                "spring-transactional",
                "matched_other_course_card",
            ),
        ):
            response = run_review_workflow(
                mode="free-question",
                request=AiGenerateRequest(
                    user_answer="@Transactional이 뭐야?",
                    course_id="frontend",
                    question="useEffect dependency problem",
                ),
                generator=forbidden_generator,
            )

        self.assertEqual(response.route, "out_of_course_redirect")
        self.assertEqual(response.model_used, "template")
        self.assertIn("out_of_course", response.quality_flags)
        self.assertEqual(response.matched_concept_id, "spring-transactional")

    def test_scope_unknown_keeps_answer_path_but_reports_flag(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="새 frontend 개념이 뭐야?"),
            generator=lambda **kwargs: "새 frontend 개념은 현재 코스에서 확인할 수 있는 기술 주제입니다.",
        )

        self.assertIn("scope_unknown", response.quality_flags)
        self.assertNotEqual(response.route, "out_of_course_redirect")
```

- [ ] **Step 2: Run RED**

Run: `cd ai; .\.venv\Scripts\python.exe -m unittest tests.test_workflow_runner.WorkflowRunnerTest.test_out_of_course_technical_question_redirects_without_generation tests.test_workflow_runner.WorkflowRunnerTest.test_scope_unknown_keeps_answer_path_but_reports_flag`

Expected: FAIL because workflow does not call the course scope gate.

- [ ] **Step 3: Extend v2 fast path signature**

In `resolve_v2_approved_fast_path`, add parameter:

```python
    allowed_card_ids: Iterable[str] | None = None,
```

After `eligible_ids = _runtime_allowlist()`, add:

```python
    if allowed_card_ids is not None:
        eligible_ids = frozenset(eligible_ids & set(allowed_card_ids))
```

- [ ] **Step 4: Add redirect helper in nodes**

In `nodes.py`, import:

```python
from app.rag.documents import load_concept_cards
from app.schemas.rag_card import CardStatus
from app.workflow.course_scope import resolve_course_scope
```

Add helper:

```python
def _approved_cards_for_scope():
    return [
        card for card in load_concept_cards()
        if getattr(card.review, "card_status", None) == CardStatus.APPROVED
    ]


def _course_scope_for_state(state: ReviewWorkflowState):
    return resolve_course_scope(
        query=_learner_query_for_state(state),
        course_id=state.request.course_id,
        intent=state.free_question_intent,
        approved_cards=_approved_cards_for_scope(),
    )


def _out_of_course_redirect_state(state: ReviewWorkflowState, matched_card_id: str | None) -> ReviewWorkflowState:
    state.answer = (
        "이 질문은 현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요. "
        "현재 문제의 개념, 정답 근거, 오답 이유를 질문해 주세요."
    )
    state.prompt_version = prompt_version_for_mode(state.mode, state.free_question_intent)
    state.prompt_strategy = prompt_strategy_for_mode(state.mode, state.free_question_intent)
    state.model_used = "template"
    state.fallback_used = False
    state.route = "out_of_course_redirect"
    state.answer_style = "out_of_course"
    state.quality_flags = ["out_of_course"]
    state.contexts = []
    if matched_card_id:
        state.resolved_query = state.resolved_query
    state.v2_fast_path_decision = {"mode": "skipped", "hit": False, "reason": "out_of_course", "card_id": matched_card_id}
    return state
```

- [ ] **Step 5: Call scope gate in `generate_answer_node`**

After off-topic check:

```python
    scope_decision = None
    if state.mode == "free-question":
        scope_decision = _course_scope_for_state(state)
        if scope_decision.scope == "out_of_course_tech":
            return _out_of_course_redirect_state(state, scope_decision.matched_card_id)
        if scope_decision.scope == "scope_unknown":
            state.quality_flags.append("scope_unknown")
```

Pass allowlist to v2 fast path:

```python
        allowed_card_ids=scope_decision.allowed_card_ids if scope_decision and scope_decision.allowed_card_ids else None,
```

- [ ] **Step 6: Preserve redirect in validation**

Update validation redirect guard:

```python
    if state.route in {"off_topic_redirect", "out_of_course_redirect"}:
```

- [ ] **Step 7: Apply same gate in streaming runner**

In `run_review_workflow_stream`, after off-topic check, call `_course_scope_for_state` and `_out_of_course_redirect_state` with the same control flow as `generate_answer_node`.

- [ ] **Step 8: Run GREEN**

Run the two workflow tests. Expected: PASS.

---

### Task 4: Backend Metadata and Follow-Up

**Files:**
- Modify: `backend/src/main/java/com/devmatch/service/ai/PythonAiReviewClient.java`
- Modify: `backend/src/main/java/com/devmatch/service/ai/AiReviewFollowUpSupport.java`
- Test: `backend/src/test/java/com/devmatch/service/ai/AiReviewFollowUpSupportTest.java`

- [ ] **Step 1: Extend follow-up test**

Add:

```java
    @Test
    void outOfCourseRedirectDoesNotAppendLearningFollowUp() {
        AiGeneratedAnswer answer = new AiGeneratedAnswer(
                "이 질문은 현재 코스 복습 범위 밖의 기술 주제라 여기서는 답변하지 않을게요.",
                "out_of_course_redirect",
                "@Transactional이 뭐야?",
                null,
                "spring-transactional",
                "out_of_course",
                List.of("out_of_course"),
                null,
                0,
                List.of()
        );

        String followUp = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(null, "@Transactional이 뭐야?", answer);
        String appended = AiReviewFollowUpSupport.appendFollowUp(answer.answer(), followUp);

        assertThat(followUp).isBlank();
        assertThat(appended).isEqualTo(answer.answer());
    }
```

- [ ] **Step 2: Run RED**

Run: `cd backend; .\gradlew.bat test --tests com.devmatch.service.ai.AiReviewFollowUpSupportTest`

Expected: FAIL until follow-up skip recognizes out-of-course.

- [ ] **Step 3: Skip out-of-course follow-up**

In `shouldSkipFollowUp`, update:

```java
        if ("off_topic_redirect".equals(answer.route())
                || "out_of_course_redirect".equals(answer.route())
                || "off_topic".equals(answer.answerStyle())
                || "out_of_course".equals(answer.answerStyle())) {
            return true;
        }
        return answer.qualityFlags() != null
                && (answer.qualityFlags().contains("off_topic")
                || answer.qualityFlags().contains("out_of_course"));
```

- [ ] **Step 4: Add request metadata fields**

Extend `PythonAiRequest` record with:

```java
String course_id,
String test_id,
String question_id,
String source_question_id,
```

Update constructors to default these to `""`.

- [ ] **Step 5: Fill metadata for free-question**

In `answerFreeQuestion`, derive:

```java
String courseId = question.getTest() == null ? "" : question.getTest().getCategory();
String testId = question.getTest() == null || question.getTest().getId() == null ? "" : String.valueOf(question.getTest().getId());
String questionId = question.getId() == null ? "" : String.valueOf(question.getId());
String sourceQuestionId = courseId.isBlank() ? "" : courseId + ":" + question.getOrderIndex();
```

Pass those values into `PythonAiRequest`.

- [ ] **Step 6: Run GREEN**

Run: `cd backend; .\gradlew.bat test --tests com.devmatch.service.ai.AiReviewFollowUpSupportTest --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest --tests com.devmatch.service.ai.AiReviewStreamingServiceTest`

Expected: BUILD SUCCESSFUL.

---

### Task 5: Candidate Capture Guard

**Files:**
- Modify: `ai/app/knowledge/auto_candidates.py`
- Test: `ai/tests/test_auto_candidates.py`

- [ ] **Step 1: Add failing tests**

Add tests:

```python
    def test_out_of_course_and_scope_unknown_do_not_create_candidates(self):
        self.assertIsNone(
            should_capture_auto_candidate("free-question", "out_of_course_redirect", 0.9, [], False)
        )
        self.assertIsNone(
            should_capture_auto_candidate("free-question", "scope_unknown", 0.9, [], False)
        )
```

- [ ] **Step 2: Run RED**

Run: `cd ai; .\.venv\Scripts\python.exe -m unittest tests.test_auto_candidates`

Expected: FAIL for `scope_unknown` if not blocked.

- [ ] **Step 3: Block routes**

Update:

```python
    if route in {"off_topic_redirect", "out_of_course_redirect", "scope_unknown"}:
        return None
```

- [ ] **Step 4: Run GREEN**

Run the same test. Expected: PASS.

---

### Task 6: Final Verification

**Files:**
- No additional files.

- [ ] **Step 1: Run Python relevant suite**

Run:

```powershell
cd ai
.\.venv\Scripts\python.exe -m unittest tests.test_course_scope_gate tests.test_workflow_runner tests.test_auto_candidates tests.test_embedding_intent
```

Expected: OK.

- [ ] **Step 2: Run Backend AI tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.devmatch.service.ai.RuleBasedAiReviewServiceTest --tests com.devmatch.service.ai.AiReviewStreamingServiceTest --tests com.devmatch.service.ai.AiReviewFollowUpSupportTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual classification sanity**

Run:

```powershell
cd ai
.\.venv\Scripts\python.exe -c "from app.workflow.embedding_intent import classify_free_question_with_embeddings; qs=['점심 뭐 먹을까?','useEffect가 뭐야?','@Transactional이 뭐야?']; [print(q, classify_free_question_with_embeddings(q).intent) for q in qs]"
```

Expected:

```text
점심 뭐 먹을까? off_topic
useEffect가 뭐야? concept_definition
@Transactional이 뭐야? concept_definition
```

- [ ] **Step 4: Report**

Report:

- changed files
- routes added: `out_of_course_redirect`
- candidate capture blocked for out-of-course
- test command outputs
- any skipped tests or remaining risk
