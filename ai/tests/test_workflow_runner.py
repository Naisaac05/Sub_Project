import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

from app.schemas import AiGenerateRequest
from app.scoring import ConfidenceResult
from app.rag.retriever import RetrievedContext
from app.workflow.answer_cache import clear_answer_cache
from app.workflow.graph import (
    WORKFLOW_NODE_NAMES,
    candidate_save_node,
    dead_end_state_node,
    error_state_node,
)
from app.workflow.nodes import validate_answer_node
from app.workflow.nodes import retrieve_context_node
from app.workflow.runner import run_review_workflow
from app.workflow.state import ReviewWorkflowState


class WorkflowRunnerTest(unittest.TestCase):
    def setUp(self):
        self._auto_candidate_tmp = tempfile.TemporaryDirectory()
        self.auto_candidate_path = Path(self._auto_candidate_tmp.name) / "auto_candidates.jsonl"
        self._previous_auto_candidate_path = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
        os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = str(self.auto_candidate_path)
        clear_answer_cache()

    def tearDown(self):
        if self._previous_auto_candidate_path is None:
            os.environ.pop("AI_REVIEW_AUTO_CANDIDATES_PATH", None)
        else:
            os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = self._previous_auto_candidate_path
        self._auto_candidate_tmp.cleanup()
        clear_answer_cache()

    def test_successful_generation_returns_metadata(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="N+1 문제가 왜 생겨?",
                correct_answer="fetch join",
                user_answer="지연 로딩 때문이야?",
            ),
            generator=lambda **kwargs: "N+1 문제는 지연 로딩 때문에 연관 엔티티 접근 시 추가 쿼리가 반복되는 문제입니다.",
        )

        self.assertIn("N+1", response.answer)
        self.assertFalse(response.fallback_used)
        self.assertGreaterEqual(response.confidence_score or 0, 0.6)
        self.assertIn("spring-n-plus-one", response.retrieved_concept_ids)

    def test_non_korean_generation_uses_template_fallback(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(question="N+1 문제가 왜 생겨?"),
            generator=lambda **kwargs: "This is an English answer.",
        )

        self.assertTrue(response.fallback_used)
        self.assertIn("정답", response.answer)
        self.assertLess(response.confidence_score or 1, 0.8)

    def test_generator_exception_uses_template_fallback(self):
        def failing_generator(**kwargs):
            raise RuntimeError("model unavailable")

        response = run_review_workflow(
            mode="follow-up",
            request=AiGenerateRequest(question="equals는 왜 써?"),
            generator=failing_generator,
        )

        self.assertTrue(response.fallback_used)
        self.assertEqual(response.model_used, "template")
        self.assertIn("기준", response.answer)


    def test_follow_up_accepts_answer_about_selected_and_correct_options(self):
        response = run_review_workflow(
            mode="follow-up",
            request=AiGenerateRequest(
                question=(
                    "Spring Security\uc5d0\uc11c \ud604\uc7ac \ub85c\uadf8\uc778 "
                    "\uc0ac\uc6a9\uc790\ub97c \ucee8\ud2b8\ub864\ub7ec \uba54\uc11c\ub4dc "
                    "\ud30c\ub77c\ubbf8\ud130\ub85c \uc8fc\uc785\ubc1b\uc744 \ub54c "
                    "\uc0ac\uc6a9\ud558\ub294 \uac83\uc740?"
                ),
                options=["@AuthenticationPrincipal", "@Scheduled"],
                correct_answer="@AuthenticationPrincipal",
                selected_answer="@Scheduled",
                user_answer="@Scheduled\uac00 \ubb54\uc9c0 \ubab0\ub77c\uc11c\uc694",
                evaluation="NEEDS_REVIEW",
                step=2,
            ),
            generator=lambda **kwargs: (
                "@Scheduled\ub294 \uc815\ud574\uc9c4 \uc2dc\uac04\uc5d0 \uba54\uc11c\ub4dc\ub97c "
                "\uc2e4\ud589\ud558\ub294 \uc2a4\ucf00\uc904\ub9c1\uc6a9\uc774\uace0, "
                "@AuthenticationPrincipal\uc740 \ud604\uc7ac \ub85c\uadf8\uc778 \uc0ac\uc6a9\uc790\ub97c "
                "\ucee8\ud2b8\ub864\ub7ec \ud30c\ub77c\ubbf8\ud130\ub85c \ubc1b\ub294 "
                "Spring Security \uc560\ub108\ud14c\uc774\uc158\uc785\ub2c8\ub2e4. "
                "\uc774 \ubb38\uc81c\uc5d0\uc11c\ub294 \ub85c\uadf8\uc778 \uc0ac\uc6a9\uc790 "
                "\uc8fc\uc785\uc774 \uae30\uc900\uc774\ubbc0\ub85c \ub458\uc758 \ucc45\uc784\uc744 "
                "\ud55c \ubb38\uc7a5\uc73c\ub85c \uad6c\ubd84\ud574\ubcfc\uae4c\uc694?"
            ),
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.route, "generation")
        self.assertIn("@Scheduled", response.answer)
        self.assertIn("@AuthenticationPrincipal", response.answer)

    def test_follow_up_validation_does_not_require_unrelated_context_keywords(self):
        state = ReviewWorkflowState(
            mode="follow-up",
            request=AiGenerateRequest(
                question=(
                    "Spring Security\uc5d0\uc11c \ud604\uc7ac \ub85c\uadf8\uc778 "
                    "\uc0ac\uc6a9\uc790\ub97c \ucee8\ud2b8\ub864\ub7ec \uba54\uc11c\ub4dc "
                    "\ud30c\ub77c\ubbf8\ud130\ub85c \uc8fc\uc785\ubc1b\uc744 \ub54c "
                    "\uc0ac\uc6a9\ud558\ub294 \uac83\uc740?"
                ),
                correct_answer="@AuthenticationPrincipal",
                selected_answer="@Scheduled",
                user_answer="@Scheduled\uac00 \ubb54\uc9c0 \ubab0\ub77c\uc11c\uc694",
            ),
            contexts=[
                RetrievedContext(
                    "unrelated",
                    "Unrelated",
                    "## \ud3c9\uac00 \ud0a4\uc6cc\ub4dc\n- RecyclerView\n- ViewHolder",
                    3.0,
                    {},
                )
            ],
            answer=(
                "@Scheduled\ub294 \uc815\ud574\uc9c4 \uc2dc\uac04\uc5d0 \uba54\uc11c\ub4dc\ub97c "
                "\uc2e4\ud589\ud558\ub294 \uc2a4\ucf00\uc904\ub9c1\uc6a9\uc774\uace0, "
                "@AuthenticationPrincipal\uc740 \ud604\uc7ac \ub85c\uadf8\uc778 \uc0ac\uc6a9\uc790\ub97c "
                "\ucee8\ud2b8\ub864\ub7ec \ud30c\ub77c\ubbf8\ud130\ub85c \ubc1b\ub294 "
                "Spring Security \uc560\ub108\ud14c\uc774\uc158\uc785\ub2c8\ub2e4."
            ),
        )

        validated = validate_answer_node(state)

        self.assertNotIn("missing_required_keywords", validated.quality_flags)
        self.assertTrue(validated.validation.required_keywords_ok)

    def test_follow_up_uses_fallback_model_first(self):
        calls = []

        def recording_generator(**kwargs):
            calls.append(kwargs["model"])
            return (
                "@Scheduled\ub294 \uc815\ud574\uc9c4 \uc2dc\uac04\uc5d0 \uba54\uc11c\ub4dc\ub97c "
                "\uc2e4\ud589\ud558\ub294 \uc2a4\ucf00\uc904\ub9c1\uc6a9\uc774\uace0, "
                "@AuthenticationPrincipal\uc740 \ud604\uc7ac \ub85c\uadf8\uc778 \uc0ac\uc6a9\uc790\ub97c "
                "\ucee8\ud2b8\ub864\ub7ec \ud30c\ub77c\ubbf8\ud130\ub85c \ubc1b\ub294 "
                "Spring Security \uc560\ub108\ud14c\uc774\uc158\uc785\ub2c8\ub2e4."
            )

        response = run_review_workflow(
            mode="follow-up",
            request=AiGenerateRequest(
                question=(
                    "Spring Security\uc5d0\uc11c \ud604\uc7ac \ub85c\uadf8\uc778 "
                    "\uc0ac\uc6a9\uc790\ub97c \ucee8\ud2b8\ub864\ub7ec \uba54\uc11c\ub4dc "
                    "\ud30c\ub77c\ubbf8\ud130\ub85c \uc8fc\uc785\ubc1b\uc744 \ub54c "
                    "\uc0ac\uc6a9\ud558\ub294 \uac83\uc740?"
                ),
                options=["@AuthenticationPrincipal", "@Scheduled"],
                correct_answer="@AuthenticationPrincipal",
                selected_answer="@Scheduled",
                user_answer="@Scheduled\uac00 \ubb54\uc9c0 \ubab0\ub77c\uc11c\uc694",
                evaluation="NEEDS_REVIEW",
                step=2,
            ),
            generator=recording_generator,
        )

        self.assertEqual(calls, ["qwen3:4b-q4_K_M"])
        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "qwen3:4b-q4_K_M")

    def test_follow_up_skips_rag_context_retrieval(self):
        state = ReviewWorkflowState(
            mode="follow-up",
            request=AiGenerateRequest(
                question=(
                    "Spring Security\uc5d0\uc11c \ud604\uc7ac \ub85c\uadf8\uc778 "
                    "\uc0ac\uc6a9\uc790\ub97c \ucee8\ud2b8\ub864\ub7ec \uba54\uc11c\ub4dc "
                    "\ud30c\ub77c\ubbf8\ud130\ub85c \uc8fc\uc785\ubc1b\uc744 \ub54c "
                    "\uc0ac\uc6a9\ud558\ub294 \uac83\uc740?"
                ),
                correct_answer="@AuthenticationPrincipal",
                selected_answer="@Scheduled",
                user_answer="@Scheduled\uac00 \ubb54\uc9c0 \ubab0\ub77c\uc11c\uc694",
            ),
        )

        retrieved = retrieve_context_node(state)

        self.assertEqual(retrieved.contexts, [])

    def test_free_question_answer_is_not_replaced_by_unrelated_context_fallback(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question=(
                    "JPA 엔티티는 데이터베이스 테이블과 매핑되는 Java 클래스입니다. "
                    "API 응답으로 그대로 반환하면 데이터베이스 구조와 관련된 문제들이 발생할 수 있습니다. "
                    "이 경우 불필요한 필드 노출 문제가 생길 수 있습니다."
                ),
                correct_answer="DTO",
                selected_answer="엔티티",
                user_answer="API가 뭔데?",
            ),
            generator=lambda **kwargs: (
                "API는 클라이언트와 서버가 정해진 방식으로 데이터를 주고받기 위한 약속입니다. "
                "예를 들어 화면이 사용자 정보를 요청하면 서버는 API를 통해 JSON 같은 형태로 응답합니다. "
                "이 문항에서는 엔티티를 API 응답으로 바로 내보내면 내부 DB 구조까지 노출될 수 있어서 DTO를 사용합니다."
            ),
        )

        self.assertFalse(response.fallback_used)
        self.assertIn("API", response.answer)

    def test_free_question_retrieval_uses_learner_question_before_original_question(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="N+1 문제에서 지연 로딩 때문에 추가 쿼리가 생기는 이유는 무엇인가요?",
                correct_answer="fetch join",
                selected_answer="지연 로딩",
                user_answer="API가 뭔데?",
            ),
            generator=lambda **kwargs: "API는 클라이언트와 서버가 데이터를 주고받기 위한 약속입니다.",
        )

        self.assertEqual(response.retrieved_concept_ids, [])

    def test_free_question_with_korean_technical_term_does_not_retrieve_original_context(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question=(
                    "N+1 \ubb38\uc81c\ub294 JPA\ub098 Hibernate\uc5d0\uc11c "
                    "\uc790\uc8fc \ubc1c\uc0dd\ud558\uba70 \uc9c0\uc5f0 \ub85c\ub529\uacfc \uad00\ub828\uc774 \uc788\uc2b5\ub2c8\ub2e4."
                ),
                correct_answer="fetch join",
                selected_answer="\uc9c0\uc5f0 \ub85c\ub529",
                user_answer="\ubd84\uc0b0\ud658\uacbd\uc774 \uc5b4\ub5a4 \ud658\uacbd\uc744 \uc758\ubbf8\ud558\ub294 \uac83\uc778\uac00\uc694?",
            ),
            generator=lambda **kwargs: (
                "\ubd84\uc0b0\ud658\uacbd\uc740 \ud558\ub098\uc758 \uc11c\ubc84\uac00 \uc544\ub2c8\ub77c "
                "\uc5ec\ub7ec \uc11c\ubc84\ub098 \uc2dc\uc2a4\ud15c\uc774 \ub124\ud2b8\uc6cc\ud06c\ub85c \uc5f0\uacb0\ub418\uc5b4 "
                "\ud568\uaed8 \uc791\uc5c5\ud558\ub294 \ud658\uacbd\uc785\ub2c8\ub2e4."
            ),
        )

        self.assertEqual(response.retrieved_concept_ids, [])
        self.assertIn("\ubd84\uc0b0\ud658\uacbd", response.answer)

    def test_free_question_rejects_stale_original_context_answer(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question=(
                    "N+1 \ubb38\uc81c\ub294 JPA\ub098 Hibernate\uc5d0\uc11c "
                    "\uc790\uc8fc \ubc1c\uc0dd\ud558\uba70 \uc9c0\uc5f0 \ub85c\ub529\uacfc \uad00\ub828\uc774 \uc788\uc2b5\ub2c8\ub2e4."
                ),
                correct_answer="fetch join",
                selected_answer="\uc9c0\uc5f0 \ub85c\ub529",
                user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \uc5b4\ub5a4 \uc758\ubbf8\uc778\uac00\uc694?",
            ),
            generator=lambda **kwargs: (
                "\uc9c0\uc5f0 \ub85c\ub529\uc740 \uc5f0\uad00 \uc5d4\ud2f0\ud2f0\ub97c "
                "\ud544\uc694\ud560 \ub54c \ubd88\ub7ec\uc624\ub294 \ubc29\uc2dd\uc774\uace0 N+1 \ubb38\uc81c\uc640 \uad00\ub828\uc774 \uc788\uc2b5\ub2c8\ub2e4."
            ),
        )

        self.assertTrue(response.fallback_used)
        self.assertIn("\uc11c\ud0b7\ube0c\ub808\uc774\ucee4", response.answer)
        self.assertNotIn("\uc9c0\uc5f0 \ub85c\ub529", response.answer)

    def test_vague_clarification_filters_low_score_unrelated_context(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question=(
                    "N+1 \ubb38\uc81c\ub294 JPA\ub098 Hibernate\uc5d0\uc11c "
                    "\uc790\uc8fc \ubc1c\uc0dd\ud558\uba70 \uc9c0\uc5f0 \ub85c\ub529\uacfc \uad00\ub828\uc774 \uc788\uc2b5\ub2c8\ub2e4."
                ),
                correct_answer="fetch join",
                selected_answer="\uc9c0\uc5f0 \ub85c\ub529",
                user_answer="\uc65c\uc694?",
            ),
            generator=lambda **kwargs: "\uc9c0\uc5f0 \ub85c\ub529\uc774 \uc5f0\uad00 \ub370\uc774\ud130\ub97c \ub098\uc911\uc5d0 \ubd88\ub7ec\uc640 N+1\uc774 \uc0dd\uae41\ub2c8\ub2e4.",
        )

        self.assertIn("spring-n-plus-one", response.retrieved_concept_ids)

    def test_stategraph_contract_exposes_named_nodes(self):
        self.assertIn("retrieve_context", WORKFLOW_NODE_NAMES)
        self.assertIn("candidate_save", WORKFLOW_NODE_NAMES)
        self.assertIn("error_state", WORKFLOW_NODE_NAMES)
        self.assertIn("dead_end_state", WORKFLOW_NODE_NAMES)

    def test_candidate_save_node_sets_candidate_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            previous = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
            previous_sink = os.environ.get("AI_REVIEW_CANDIDATE_SINK")
            os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = str(path)
            os.environ["AI_REVIEW_CANDIDATE_SINK"] = "jsonl"
            try:
                state = ReviewWorkflowState(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
                    answer="서킷브레이커는 장애가 난 외부 호출을 잠시 차단해 장애 전파를 줄이는 패턴입니다.",
                    route="generation",
                    fallback_used=False,
                    confidence=ConfidenceResult(
                        score=0.5,
                        band="low",
                        should_fallback=True,
                        should_save_candidate=True,
                    ),
                )

                result = candidate_save_node(state)
            finally:
                if previous is None:
                    os.environ.pop("AI_REVIEW_AUTO_CANDIDATES_PATH", None)
                else:
                    os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = previous
                if previous_sink is None:
                    os.environ.pop("AI_REVIEW_CANDIDATE_SINK", None)
                else:
                    os.environ["AI_REVIEW_CANDIDATE_SINK"] = previous_sink

            self.assertIsNotNone(result.candidate_id)
            self.assertTrue(path.exists())
            self.assertIn(result.candidate_id or "", path.read_text(encoding="utf-8"))
            rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(rows[0]["definition_draft"], state.answer)

    def test_common_mobile_terms_use_static_answer_to_avoid_slow_llm(self):
        cases = [
            ("RecyclerView가 뭔가요?", "RecyclerView"),
            ("Android가 뭔가요?", "Android"),
            ("Flutter 앱이 뭔가요?", "Flutter"),
            ("DAO가 뭔가요?", "DAO"),
        ]

        def failing_generator(**kwargs):
            raise AssertionError("known mobile/backend terms should not wait for Ollama generation")

        for user_answer, expected_term in cases:
            with self.subTest(user_answer=user_answer):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer=user_answer),
                    generator=failing_generator,
                )

                self.assertFalse(response.fallback_used)
                self.assertEqual(response.model_used, "lightweight-template")
                self.assertEqual(response.route, "static_fast_path")
                self.assertIn(expected_term, response.answer)

    def test_candidate_save_node_uses_http_sink_by_default(self):
        previous_sink = os.environ.pop("AI_REVIEW_CANDIDATE_SINK", None)
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
            route="generation",
            fallback_used=False,
            confidence=ConfidenceResult(
                score=0.5,
                band="low",
                should_fallback=True,
                should_save_candidate=True,
            ),
        )

        try:
            with patch("app.workflow.graph.save_auto_candidate", create=True, return_value=True) as save, \
                    patch("app.workflow.graph.append_auto_candidate", return_value=False) as append:
                result = candidate_save_node(state)
        finally:
            if previous_sink is not None:
                os.environ["AI_REVIEW_CANDIDATE_SINK"] = previous_sink

        self.assertIsNotNone(result.candidate_id)
        save.assert_called_once()
        append.assert_not_called()

    def test_candidate_save_node_uses_jsonl_only_when_explicitly_configured(self):
        previous_sink = os.environ.get("AI_REVIEW_CANDIDATE_SINK")
        os.environ["AI_REVIEW_CANDIDATE_SINK"] = "jsonl"
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
            route="generation",
            fallback_used=False,
            confidence=ConfidenceResult(
                score=0.5,
                band="low",
                should_fallback=True,
                should_save_candidate=True,
            ),
        )

        try:
            with patch("app.workflow.graph.save_auto_candidate", create=True, return_value=False) as save, \
                    patch("app.workflow.graph.append_auto_candidate", return_value=True) as append:
                result = candidate_save_node(state)
        finally:
            if previous_sink is None:
                os.environ.pop("AI_REVIEW_CANDIDATE_SINK", None)
            else:
                os.environ["AI_REVIEW_CANDIDATE_SINK"] = previous_sink

        self.assertIsNotNone(result.candidate_id)
        append.assert_called_once()
        save.assert_not_called()

    def test_candidate_save_node_skips_append_when_no_candidate_capture_is_enabled(self):
        previous = os.environ.get("AI_REVIEW_NO_CANDIDATE_CAPTURE")
        os.environ["AI_REVIEW_NO_CANDIDATE_CAPTURE"] = "true"
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
            route="generation",
            fallback_used=False,
            confidence=ConfidenceResult(
                score=0.5,
                band="low",
                should_fallback=True,
                should_save_candidate=True,
            ),
        )

        try:
            with patch("app.workflow.graph.append_auto_candidate") as append:
                result = candidate_save_node(state)
        finally:
            if previous is None:
                os.environ.pop("AI_REVIEW_NO_CANDIDATE_CAPTURE", None)
            else:
                os.environ["AI_REVIEW_NO_CANDIDATE_CAPTURE"] = previous

        self.assertIsNone(result.candidate_id)
        self.assertIn("candidate_capture_disabled", result.quality_flags)
        append.assert_not_called()

    def test_candidate_save_node_isolates_append_failure_from_answer_path(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
            route="generation",
            fallback_used=False,
            confidence=ConfidenceResult(
                score=0.5,
                band="low",
                should_fallback=True,
                should_save_candidate=True,
            ),
        )

        with patch("app.workflow.graph.save_auto_candidate", create=True, side_effect=OSError("sink unavailable")):
            result = candidate_save_node(state)

        self.assertIs(result, state)
        self.assertIsNone(result.candidate_id)
        self.assertIn("candidate_capture_failed", result.quality_flags)

    def test_dead_end_and_error_state_nodes_mark_graph_status(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="\uc11c\ud0b7\ube0c\ub808\uc774\ucee4\uac00 \ubb50\uc57c?"),
        )

        dead_end = dead_end_state_node(state)
        self.assertEqual(dead_end.graph_status, "dead_end")
        self.assertEqual(dead_end.route, "dead_end_state")
        self.assertTrue(dead_end.fallback_used)
        self.assertTrue(dead_end.answer)

        error_state = error_state_node(
            ReviewWorkflowState(mode="free-question", request=AiGenerateRequest())
        )
        self.assertEqual(error_state.graph_status, "error")
        self.assertEqual(error_state.route, "error_state")

    def test_known_standalone_concept_question_skips_generator(self):
        def failing_generator(**kwargs):
            raise AssertionError("generator should not be called for lightweight known answers")

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="N+1 \ubb38\uc81c\ub294 JPA\ub098 Hibernate\uc5d0\uc11c \uc790\uc8fc \ubc1c\uc0dd\ud569\ub2c8\ub2e4.",
                correct_answer="fetch join",
                selected_answer="\uc9c0\uc5f0 \ub85c\ub529",
                user_answer="API\uac00 \ubb54\ub370?",
            ),
            generator=failing_generator,
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "lightweight-template")
        self.assertIn("API", response.answer)
        self.assertNotIn("N+1", response.answer)
        self.assertEqual(response.route, "static_fast_path")

    def test_generic_ai_question_does_not_use_static_fast_path(self):
        calls = {"count": 0}

        def generator(**kwargs):
            calls["count"] += 1
            return "AI는 사람이 만든 데이터를 바탕으로 추론하거나 답을 만드는 기술입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="AI가 뭐야?"),
            generator=generator,
        )

        self.assertEqual(calls["count"], 1)
        self.assertNotEqual(response.route, "static_fast_path")
        self.assertIn("AI", response.answer)

    def test_latin_alias_does_not_match_inside_unrelated_word(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            previous = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
            os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = str(path)
            calls = {"count": 0}

            def generator(**kwargs):
                calls["count"] += 1
                return "forest는 REST API가 아니라 나무가 많은 숲을 뜻하는 일반 단어입니다."

            try:
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer="What is forest?"),
                    generator=generator,
                )
            finally:
                if previous is None:
                    os.environ.pop("AI_REVIEW_AUTO_CANDIDATES_PATH", None)
                else:
                    os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = previous

            self.assertEqual(calls["count"], 1)
            self.assertNotEqual(response.route, "static_fast_path")
            self.assertIn("forest", response.answer)

    def test_specific_ai_prompt_question_can_use_generation_with_clean_topic(self):
        calls = {"count": 0}

        def generator(**kwargs):
            calls["count"] += 1
            return "AI 프롬프트는 AI에게 원하는 답을 얻기 위해 입력하는 지시문입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="혹시 AI 프롬프트가 뭐야?"),
            generator=generator,
        )

        self.assertEqual(calls["count"], 1)
        self.assertNotEqual(response.route, "static_fast_path")
        self.assertIn("AI 프롬프트", response.answer)

        previous_sink = os.environ.get("AI_REVIEW_CANDIDATE_SINK")
        os.environ["AI_REVIEW_CANDIDATE_SINK"] = "jsonl"
        try:
            response = run_review_workflow(
                mode="free-question",
                request=AiGenerateRequest(user_answer="혹시 AI 프롬프트가 뭐야?"),
                generator=generator,
            )
            rows = [
                json.loads(line)
                for line in self.auto_candidate_path.read_text(encoding="utf-8").splitlines()
            ]
        finally:
            if previous_sink is None:
                os.environ.pop("AI_REVIEW_CANDIDATE_SINK", None)
            else:
                os.environ["AI_REVIEW_CANDIDATE_SINK"] = previous_sink
        self.assertEqual(rows[0]["term"], "AI \ud504\ub86c\ud504\ud2b8")

    def test_common_programming_concepts_skip_generator(self):
        cases = [
            ("\ub9ac\uc2a4\ud2b8 \ucef4\ud504\ub9ac\ud5e8\uc158\uc774 \ubb54\uc9c0 \ubaa8\ub974\uaca0\uc74c", "\ub9ac\uc2a4\ud2b8"),
            ("REST API\uac00 \ubb50\uc57c?", "REST"),
            ("JSON\uc774 \ubb50\uc57c?", "JSON"),
            ("Optional\uc740 \uc65c \uc368?", "Optional"),
            ("Stream map filter \ucc28\uc774\uac00 \ubb50\uc57c?", "Stream"),
            ("ORM\uc774 \ubb54\ub370?", "ORM"),
            ("JPA \uc5d4\ud2f0\ud2f0\uac00 \ubb50\uc57c?", "\uc5d4\ud2f0\ud2f0"),
            ("\ud504\ub77c\ubbf8\uc2a4\uac00 \ubb54\uc9c0 \ubaa8\ub974\uaca0\uc5b4", "Promise"),
            ("async await\uc774 \ubb50\uc57c?", "async"),
        ]

        def failing_generator(**kwargs):
            raise AssertionError("generator should not be called for common concept fast path")

        for question, expected_keyword in cases:
            with self.subTest(question=question):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(
                        question="N+1 \ubb38\uc81c\ub294 JPA\uc5d0\uc11c \uc790\uc8fc \ub098\uc635\ub2c8\ub2e4.",
                        correct_answer="fetch join",
                        selected_answer="\uc9c0\uc5f0 \ub85c\ub529",
                        user_answer=question,
                    ),
                    generator=failing_generator,
                )

                self.assertFalse(response.fallback_used)
                self.assertEqual(response.model_used, "lightweight-template")
                self.assertIn(expected_keyword, response.answer)
                self.assertEqual(response.retrieved_concept_ids, [])

    def test_contextual_json_question_uses_generator_instead_of_static_definition(self):
        calls = {"count": 0}

        def generator(**kwargs):
            calls["count"] += 1
            return (
                "\uc751\ub2f5 JSON\uc740 \uc11c\ubc84\uac00 \ud074\ub77c\uc774\uc5b8\ud2b8\uc5d0 "
                "\ubcf4\ub0b4\ub294 JSON \ud615\uc2dd\uc758 \uacb0\uacfc \ub370\uc774\ud130\uc785\ub2c8\ub2e4."
            )

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="Spring MVC\uc5d0\uc11c JSON \uc751\ub2f5\uc744 \ub9cc\ub4dc\ub294 \ubc29\uc2dd\uc740?",
                correct_answer="ResponseEntity\ub85c \uac1d\uccb4\ub97c \ubc18\ud658\ud574 JSON\uc73c\ub85c \uc9c1\ub82c\ud654\ud55c\ub2e4",
                user_answer="\uc751\ub2f5 JSON\uc758 \uc758\ubbf8",
            ),
            generator=generator,
        )

        self.assertEqual(calls["count"], 1)
        self.assertFalse(response.fallback_used)
        self.assertEqual(response.route, "generation")
        self.assertNotEqual(response.model_used, "lightweight-template")
        self.assertIn("\uc751\ub2f5 JSON", response.answer)

    def test_typo_alias_question_uses_lightweight_fast_path(self):
        def failing_generator(**kwargs):
            raise AssertionError("generator should not be called for corrected aria-label fast path")

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="접근성 측면에서 아이콘 버튼에 필요한 처리는 무엇인가요?",
                correct_answer="aria-label 또는 스크린리더가 읽을 수 있는 이름 제공",
                selected_answer="아이콘을 더 작게 만들기",
                user_answer="arila-label이 뭐야?",
            ),
            generator=failing_generator,
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "lightweight-template")
        self.assertIn("aria-label", response.answer)
        self.assertIn("스크린리더", response.answer)

    def test_typo_alias_question_uses_generated_card_fast_path(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="Spring 전역 예외 처리 문제",
                correct_answer="@ControllerAdvice",
                selected_answer="@Controller",
                user_answer="ConrollerAdvice는 실무에서 어떻게 쓰여?",
            ),
            generator=lambda **kwargs: "@ControllerAdvice는 여러 컨트롤러의 예외 응답을 한곳에서 일관되게 처리할 때 사용합니다.",
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "lightweight-template")
        self.assertIn("@ControllerAdvice", response.answer)
        self.assertIn("java-backend-controlleradvice", response.retrieved_concept_ids)

    def test_generated_concept_card_question_uses_lightweight_fast_path(self):
        def failing_generator(**kwargs):
            raise AssertionError("generator should not be called for generated concept card fast path")

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="Spring 전역 예외 처리 문제",
                correct_answer="@ControllerAdvice",
                selected_answer="@Controller",
                user_answer="ConrollerAdvice는 실무에서 어떻게 쓰여?",
            ),
            generator=failing_generator,
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.model_used, "lightweight-template")
        self.assertIn("@ControllerAdvice", response.answer)
        self.assertIn("Spring MVC", response.answer)
        self.assertIn("예외", response.answer)
        self.assertEqual(response.route, "generated_card_fast_path")
        self.assertIn("java-backend-controlleradvice", response.retrieved_concept_ids)
        self.assertEqual(response.correction_type, "typo")
        self.assertEqual(response.matched_concept_id, "java-backend-controlleradvice")
        self.assertIn("@ControllerAdvice", response.resolved_query)

    def test_generated_answer_returns_rag_generation_route(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="Java equals와 == 비교 문제입니다.",
                correct_answer="equals 재정의",
                selected_answer="== 비교",
                user_answer="equals를 짧게 설명해줘",
            ),
            generator=lambda **kwargs: "equals는 객체의 논리적 동등성을 비교하는 메서드입니다.",
        )

        self.assertFalse(response.fallback_used)
        self.assertEqual(response.route, "rag_generation")
        self.assertIsNotNone(response.resolved_query)

    def test_practical_question_returns_practical_answer_style(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="aria-label은 실무에서 어떻게 써?"),
            generator=lambda **kwargs: "generator should not be used",
        )

        self.assertEqual(response.model_used, "lightweight-template")
        self.assertEqual(response.answer_style, "practical")
        self.assertEqual(response.quality_flags, [])
        self.assertIn("aria-label", response.answer)

    def test_low_quality_generated_answer_reports_missing_topic_flag(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="서킷브레이커가 어떤 의미인가요?"),
            generator=lambda **kwargs: "이 개념은 중요합니다.",
        )

        self.assertTrue(response.fallback_used)
        self.assertIn("missing_topic", response.quality_flags)
        self.assertEqual(response.answer_style, "definition")

    def test_unapproved_free_question_keeps_llm_answer_and_saves_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "auto_candidates.jsonl"
            previous = os.environ.get("AI_REVIEW_AUTO_CANDIDATES_PATH")
            previous_sink = os.environ.get("AI_REVIEW_CANDIDATE_SINK")
            os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = str(path)
            os.environ["AI_REVIEW_CANDIDATE_SINK"] = "jsonl"
            try:
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(
                        question="\ub85c\uceec \uc800\uc7a5\uc18c\uc640 \uc11c\ubc84 \ub3d9\uae30\ud654 \uc815\ucc45\uc740?",
                        correct_answer="\ub85c\uceec \uc800\uc7a5\uc18c\uc640 \uc11c\ubc84 \ub3d9\uae30\ud654 \uc815\ucc45",
                        user_answer="Flutter \uc571\uc774 \ubb54\uac00\uc694?",
                    ),
                    generator=lambda **kwargs: (
                        "Flutter \uc571\uc740 Flutter \ud504\ub808\uc784\uc6cc\ud06c\ub85c \ub9cc\ub4e0 \uc571\uc785\ub2c8\ub2e4. "
                        "\ud558\ub098\uc758 Dart \ucf54\ub4dc\ub85c Android\uc640 iOS \ud654\uba74\uc744 \uad6c\uc131\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."
                    ),
                )
            finally:
                if previous is None:
                    os.environ.pop("AI_REVIEW_AUTO_CANDIDATES_PATH", None)
                else:
                    os.environ["AI_REVIEW_AUTO_CANDIDATES_PATH"] = previous
                if previous_sink is None:
                    os.environ.pop("AI_REVIEW_CANDIDATE_SINK", None)
                else:
                    os.environ["AI_REVIEW_CANDIDATE_SINK"] = previous_sink

            self.assertFalse(response.fallback_used)
            self.assertIn(response.route, {"generation", "static_fast_path"})
            self.assertIn("Flutter \uc571", response.answer)
            self.assertNotIn("\uc2b9\uc778\ub41c \uc9c0\uc2dd \uce74\ub4dc", response.answer)
            self.assertIsNotNone(response.candidate_id)
            self.assertTrue(path.exists())
            self.assertIn(response.candidate_id or "", path.read_text(encoding="utf-8"))

    def test_unapproved_free_question_retries_llm_fallback_model_before_template(self):
        calls: list[str] = []

        def fallback_model_generator(**kwargs):
            calls.append(kwargs["model"])
            if len(calls) == 1:
                raise RuntimeError("primary model unavailable")
            return (
                "WebSocket \ud578\ub4dc\uc170\uc774\ud06c\ub294 HTTP \uc5f0\uacb0\uc744 WebSocket \uc5f0\uacb0\ub85c "
                "\uc804\ud658\ud558\uae30 \uc704\ud574 \ud074\ub77c\uc774\uc5b8\ud2b8\uc640 \uc11c\ubc84\uac00 \ucc98\uc74c\uc5d0 \uc8fc\uace0\ubc1b\ub294 \uc57d\uc18d\uc785\ub2c8\ub2e4."
            )

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="\ub85c\uceec \uc800\uc7a5\uc18c\uc640 \uc11c\ubc84 \ub3d9\uae30\ud654 \uc815\ucc45\uc740?",
                correct_answer="\ub85c\uceec \uc800\uc7a5\uc18c\uc640 \uc11c\ubc84 \ub3d9\uae30\ud654 \uc815\ucc45",
                user_answer="WebSocket \ud578\ub4dc\uc170\uc774\ud06c\uac00 \ubb54\uac00\uc694?",
                model="qwen3:1.7b",
            ),
            generator=fallback_model_generator,
        )

        self.assertEqual(calls, ["qwen3:1.7b", "qwen3:4b-q4_K_M"])
        self.assertFalse(response.fallback_used)
        self.assertEqual(response.route, "generation")
        self.assertEqual(response.model_used, "qwen3:4b-q4_K_M")
        self.assertIn("WebSocket", response.answer)
        self.assertNotIn("\uc2b9\uc778\ub41c \uc9c0\uc2dd \uce74\ub4dc", response.answer)

    def test_non_korean_free_question_retries_fallback_model_before_template(self):
        calls: list[str] = []

        def fallback_model_generator(**kwargs):
            calls.append(kwargs["model"])
            if len(calls) == 1:
                return "train validation test are dataset splits for model development."
            return (
                "train/validation/test\ub294 \ubaa8\ub378 \uac1c\ubc1c \ub370\uc774\ud130\ub97c "
                "\uc5ed\ud560\ubcc4\ub85c \ub098\ub204\ub294 \uae30\uc900\uc785\ub2c8\ub2e4. "
                "train\uc740 \ud559\uc2b5, validation\uc740 \ud29c\ub2dd\uacfc \uac80\uc99d, "
                "test\ub294 \ucd5c\uc885 \uc77c\ubc18\ud654 \uc131\ub2a5 \ud655\uc778\uc5d0 \uc0ac\uc6a9\ud569\ub2c8\ub2e4."
            )

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question=(
                    "AI \ubaa8\ub378 \ud559\uc2b5 \ub370\uc774\ud130\ub97c \ub098\ub20c \ub54c "
                    "train/validation/test set\uc758 \uc5ed\ud560\uc740?"
                ),
                correct_answer=(
                    "train\uc740 \ud559\uc2b5, validation\uc740 \ud29c\ub2dd\uacfc \uac80\uc99d, "
                    "test\ub294 \ucd5c\uc885 \uc77c\ubc18\ud654 \uc131\ub2a5 \ud3c9\uac00"
                ),
                user_answer="train/validation/test \ub370\uc774\ud130\uac00 \uc5b4\ub5a4 \uc758\ubbf8\uc778\uc9c0 \ubab0\ub77c",
                model="qwen3:1.7b",
            ),
            generator=fallback_model_generator,
        )

        self.assertEqual(calls, ["qwen3:1.7b", "qwen3:4b-q4_K_M"])
        self.assertFalse(response.fallback_used)
        self.assertEqual(response.route, "generation")
        self.assertEqual(response.model_used, "qwen3:4b-q4_K_M")
        self.assertIn("train/validation/test", response.answer)
        self.assertNotIn("\uc2b9\uc778\ub41c \uc9c0\uc2dd \uce74\ub4dc", response.answer)

    def test_topic_specific_fallbacks_explain_common_short_questions(self):
        cases = [
            ("Git tag는 뭔데?", ["Git tag", "commit", "version"]),
            ("idempotent 설계가 뭔데?", ["Idempotent", "same result", "retry"]),
            ("그럼 네트워크를 자동으로 연결 시켜주는 것은 없나요?", ["network", "auto", "layout"]),
        ]

        for user_answer, expected_terms in cases:
            with self.subTest(user_answer=user_answer):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer=user_answer),
                    generator=lambda **kwargs: "unrelated answer",
                )

                self.assertTrue(response.fallback_used)
                for expected_term in expected_terms:
                    self.assertIn(expected_term, response.answer)
                self.assertNotIn("정의, 쓰이는 상황", response.answer)

    def test_common_backend_terms_use_static_answer_instead_of_vague_fallback(self):
        cases = [
            ("@Transactional이 뭐야?", ["@Transactional", "transaction", "rollback", "Service"]),
            ("계층은 어떻게 있나요?", ["Controller", "Service", "Repository", "Entity"]),
        ]

        for user_answer, expected_terms in cases:
            with self.subTest(user_answer=user_answer):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer=user_answer),
                    generator=lambda **kwargs: "unrelated answer",
                )

                self.assertFalse(response.fallback_used)
                self.assertIn(response.route, {"static_fast_path", "generated_card_fast_path"})
                for expected_term in expected_terms:
                    self.assertIn(expected_term, response.answer)
                self.assertNotIn("질문으로 이해했어요", response.answer)

    def test_common_course_terms_use_static_answer_instead_of_contextual_gap_fallback(self):
        cases = [
            ("N+1이 뭐야?", ["N+1", "추가 쿼리", "fetch join"]),
            ("fetch join이 뭐야?", ["fetch join", "연관 엔티티", "N+1"]),
            ("환경변수는 뭐야?", ["환경변수", "비밀번호", "API 키"]),
            ("캐시는 뭐야?", ["캐시", "재사용", "속도"]),
        ]

        for user_answer, expected_terms in cases:
            with self.subTest(user_answer=user_answer):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(user_answer=user_answer),
                    generator=lambda **kwargs: "unrelated answer",
                )

                self.assertFalse(response.fallback_used)
                self.assertEqual(response.route, "static_fast_path")
                for expected_term in expected_terms:
                    self.assertIn(expected_term, response.answer)
                self.assertNotIn("지식 카드가 아직 부족", response.answer)

    def test_http_status_code_questions_answer_the_asked_code_first(self):
        cases = [
            (
                "204 No Content는 어떤 상태 코드인가요?",
                ["204 No Content", "성공", "응답 본문", "삭제"],
            ),
            (
                "201 Created는 무슨 뜻이야?",
                ["201 Created", "생성", "Location", "POST"],
            ),
        ]

        for user_answer, expected_terms in cases:
            with self.subTest(user_answer=user_answer):
                response = run_review_workflow(
                    mode="free-question",
                    request=AiGenerateRequest(
                        question="REST API에서 리소스를 새로 생성했을 때 일반적으로 가장 적절한 HTTP 상태 코드는 무엇인가요?",
                        correct_answer="201 Created",
                        selected_answer="204 No Content",
                        user_answer=user_answer,
                    ),
                    generator=lambda **kwargs: "201 Created가 왜 맞는지와 204 No Content가 어떤 개념을 놓쳤는지를 먼저 나눠보면 좋아요.",
                )

                self.assertFalse(response.fallback_used)
                self.assertEqual(response.route, "static_fast_path")
                for expected_term in expected_terms:
                    self.assertIn(expected_term, response.answer)
                self.assertNotIn("어떤 개념을 놓쳤는지", response.answer)

    def test_free_question_token_budget_allows_complete_short_definition(self):
        from app.workflow.nodes import max_tokens_for_mode

        self.assertGreaterEqual(max_tokens_for_mode("free-question", 256), 120)
        self.assertLessEqual(max_tokens_for_mode("free-question", 256), 140)

    def test_repeated_generated_answer_uses_cache(self):
        calls = {"count": 0}

        def counting_generator(**kwargs):
            calls["count"] += 1
            return "\ubc18\ubcf5 \uc9c8\ubb38\uc5d0 \ub300\ud55c \ud55c\uad6d\uc5b4 \uc124\uba85\uc785\ub2c8\ub2e4."

        request = AiGenerateRequest(user_answer="\uc774 \uac1c\ub150\uc744 \uc9e7\uac8c \uc124\uba85\ud574\uc918")

        first = run_review_workflow("free-question", request, generator=counting_generator)
        second = run_review_workflow("free-question", request, generator=counting_generator)

        self.assertEqual(calls["count"], 1)
        self.assertEqual(first.answer, second.answer)

    def test_hallucination_suspected_generated_answer_is_not_cached(self):
        calls = {"count": 0}
        answers = [
            "Spring\uc5d0\uc11c @Transactional\uc740 \ubaa8\ub4e0 DTO \ubcc0\uacbd\uc744 \uc790\ub3d9\uc73c\ub85c DB\uc5d0 \uc800\uc7a5\ud569\ub2c8\ub2e4.",
            "Spring\uc5d0\uc11c @Transactional\uc740 \ud2b8\ub79c\uc7ad\uc158 \uacbd\uacc4\ub97c \uc9c0\uc815\ud569\ub2c8\ub2e4.",
        ]

        def generator(**kwargs):
            index = min(calls["count"], len(answers) - 1)
            calls["count"] += 1
            return answers[index]

        request = AiGenerateRequest(user_answer="\uc774 \uac1c\ub150\uc744 \uc9e7\uac8c \uc124\uba85\ud574\uc918")

        first = run_review_workflow("free-question", request, generator=generator)
        second = run_review_workflow("free-question", request, generator=generator)

        self.assertEqual(calls["count"], 2)
        self.assertIn("hallucination_suspected", first.quality_flags)
        self.assertNotEqual(first.answer, second.answer)

    def test_concurrent_identical_generated_answer_uses_single_flight(self):
        calls = {"count": 0}
        calls_lock = threading.Lock()
        start = threading.Event()

        def generator(**kwargs):
            with calls_lock:
                calls["count"] += 1
            time.sleep(0.1)
            return "AI는 사람이 만든 데이터를 바탕으로 추론하거나 답을 만드는 기술이며, single-flight로 같은 질문 생성을 공유합니다."

        request = AiGenerateRequest(user_answer="AI가 뭐야?")

        def call_workflow():
            start.wait(timeout=1)
            return run_review_workflow(
                mode="free-question",
                request=request,
                generator=generator,
            )

        with ThreadPoolExecutor(max_workers=2) as executor:
            futures = [executor.submit(call_workflow) for _ in range(2)]
            start.set()
            responses = [future.result(timeout=5) for future in futures]

        self.assertEqual(calls["count"], 1)
        self.assertEqual([response.answer for response in responses], [responses[0].answer] * 2)

    def test_workflow_response_includes_structured_observability_event(self):
        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(user_answer="API가 뭐야?"),
            generator=lambda **kwargs: "generator should not be called",
        )

        self.assertEqual(len(response.observability_events), 2)
        event = response.observability_events[0]
        self.assertEqual(event["event"], "ai_review.workflow_completed")
        self.assertEqual(event["route"], response.route)
        self.assertEqual(event["model_used"], response.model_used)
        self.assertEqual(event["fallback_used"], response.fallback_used)
        self.assertEqual(event["candidate_id"], response.candidate_id)
        self.assertIn("latency_ms", event)

        judge_event = response.observability_events[1]
        self.assertEqual(judge_event["event"], "ai_review.semantic_judge_evaluated")
        self.assertEqual(judge_event["final_quality_status"], "degraded")

    def test_regression_free_question_concept_definition_without_bias(self):
        # 6번 요구사항 회귀 테스트 케이스
        # 질문: "지연 로딩, 순환 참조, 불필요한 필드 노출 문제가 각각 어떤 의미인지 알고 싶어요"
        
        def mock_llm_generator(**kwargs):
            return (
                "지연 로딩은 필요한 시점에 데이터를 가져오는 방식이고, "
                "순환 참조는 두 엔티티가 서로를 참조하여 무한 루프가 도는 현상이며, "
                "불필요한 필드 노출은 내부 DB 구조가 API 밖으로 새는 문제입니다. "
                "Entity를 DTO로 변환하여 반환하면 이 세 가지 문제를 안전하게 피할 수 있습니다."
            )

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 엔티티 반환 시 생길 수 있는 문제와 해결방안은?",
                correct_answer="Entity를 DTO로 변환하여 반환한다.",
                selected_answer="DB 인덱스를 자동 삭제한다.",
                user_answer="지연 로딩, 순환 참조, 불필요한 필드 노출 문제가 각각 어떤 의미인지 알고 싶어요",
            ),
            generator=mock_llm_generator,
        )

        # A. fallback이 사용되지 않아야 함 (즉, validation 통과)
        self.assertFalse(response.fallback_used)
        
        # B. 기대 결과 포함 여부 검증
        self.assertIn("지연 로딩", response.answer)
        self.assertIn("순환 참조", response.answer)
        self.assertIn("필드 노출", response.answer)
        self.assertIn("DTO", response.answer)
        
        # C. 금지 결과 미포함 검증
        self.assertNotIn("자동 삭제", response.answer)
        self.assertNotIn("RecyclerView", response.answer)
        self.assertNotIn("ViewHolder", response.answer)

    def test_judge_concept_definition_bias_triggers_retry(self):
        calls = []

        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            calls.append(prompt)
            # 1. 판사의 structured JSON 평가 호출 모의
            if "precise AI Semantic Judge" in prompt:
                # 1차 생성에 대해 bias 판정
                if "오답입니다" in prompt:
                    return '{"relevance_score": 0.9, "context_bias_score": 0.8, "hallucination_risk": "low", "should_retry": true, "reason": "배경문제에 너무 매몰됨"}'
                # 2차 생성에 대해 패스 판정
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            
            # 2. 일반 답변 생성 호출 모의
            if len(calls) == 1:
                # 1차 답변 (bias 포함)
                return "DB 인덱스가 자동 삭제되는 오답입니다."
            # 2차 답변 (정상)
            return "지연 로딩은 필요한 시점에 연관 데이터를 불러오는 방식입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 관련 문제",
                correct_answer="DTO 반환",
                selected_answer="DB 인덱스 자동 삭제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=mock_generator,
        )

        # 1차 bias 발견 -> 재시도 실행하여 2차 패스 확인
        self.assertFalse(response.fallback_used)
        self.assertIn("지연 로딩은", response.answer)
        # observability_events에 retry 메트릭 포함 확인
        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertTrue(event["semantic_judge_retry"])

    def test_judge_follow_up_contamination_triggers_retry(self):
        calls = []

        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            calls.append(prompt)
            if "precise AI Semantic Judge" in prompt:
                if "ViewHolder" in prompt:
                    return '{"relevance_score": 0.3, "context_bias_score": 0.0, "hallucination_risk": "low", "should_retry": true, "reason": "엉뚱한 뷰홀더 언급함"}'
                return '{"relevance_score": 0.9, "context_bias_score": 0.0, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            
            if len(calls) == 1:
                return "ViewHolder는 뷰를 재사용하기 위한 도구입니다."
            return "DTO는 계층 간 데이터 전달용 객체입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 관련 문제",
                correct_answer="DTO",
                user_answer="DTO 개념이 뭐야?",
            ),
            generator=mock_generator,
        )

        self.assertFalse(response.fallback_used)
        self.assertIn("DTO는", response.answer)

    def test_judge_hallucinated_answer_triggers_fallback(self):
        calls = []

        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            calls.append(prompt)
            if "precise AI Semantic Judge" in prompt:
                # 환각 고위험 판결
                return '{"relevance_score": 0.8, "context_bias_score": 0.0, "hallucination_risk": "high", "should_retry": false, "reason": "환각 심각함"}'
            return "equals는 가비지 컬렉터의 동작을 수동 제어하는 함수입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="Java equals",
                correct_answer="equals 재정의",
                user_answer="equals가 뭐야?",
            ),
            generator=mock_generator,
        )

        # 환각 고위험 발견 -> 재시도 없이 즉시 fallback 작동
        self.assertTrue(response.fallback_used)
        self.assertIn("fallback_template", response.route)
        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertTrue(event["semantic_judge_fallback"])

    def test_judge_retry_uses_background_removed_prompt(self):
        prompts_sent = []

        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.5, "context_bias_score": 0.8, "hallucination_risk": "low", "should_retry": true, "reason": "재시도"}'
            prompts_sent.append(prompt)
            return "임시 답변"

        run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="원래 배경 문제 텍스트가 매우 길게 나열됨",
                correct_answer="DTO",
                selected_answer="오답",
                user_answer="이 문제에서 프록시 객체가 뭐야?",
            ),
            generator=mock_generator,
        )

        self.assertEqual(len(prompts_sent), 2)
        # 1차 프롬프트에는 배경 텍스트가 들어있음
        self.assertIn("원래 배경 문제", prompts_sent[0])
        # 2차 프롬프트(재시도)에는 context_dependent=False에 의해 원래 배경 문제가 제거되어 있음!
        self.assertNotIn("원래 배경 문제", prompts_sent[1])

    def test_judge_retry_limit_respected(self):
        calls = []

        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            calls.append(prompt)
            if "precise AI Semantic Judge" in prompt:
                # 1차, 2차 판결 모두 relevance 가 낮아 should_retry=True 임에도, retry_count에 의해 fallback되도록 유도
                return '{"relevance_score": 0.4, "context_bias_score": 0.0, "hallucination_risk": "low", "should_retry": true, "reason": "실패"}'
            return "동문서답 계속 수행"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="프록시 객체가 뭐야?",
            ),
            generator=mock_generator,
        )

        # 재시도 제한(최대 1회) 준수 -> 3차 시도 없이 최종 Fallback 연동
        self.assertTrue(response.fallback_used)
        self.assertIn("fallback_template", response.route)

    def test_metric_judge_passed(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            return "지연 로딩은 필요한 시점에 데이터를 로드하는 방식입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertEqual(event["final_quality_status"], "passed")
        self.assertTrue(event["answer_quality_passed"])
        self.assertFalse(event["answer_quality_retry_triggered"])
        self.assertFalse(event["answer_quality_fallback_triggered"])
        self.assertFalse(event["answer_quality_degraded"])
        self.assertEqual(event["answer_relevance_score"], 0.95)
        self.assertEqual(event["answer_context_bias_score"], 0.1)
        self.assertEqual(event["answer_hallucination_risk"], "low")
        self.assertEqual(event["intent"], "concept_definition")
        self.assertEqual(event["sub_intent"], "definition")

    def test_metric_low_relevance_retry(self):
        calls = []
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                if len(calls) == 1:
                    return '{"relevance_score": 0.5, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": true, "reason": "1차 낮음"}'
                return '{"relevance_score": 0.9, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "2차 통과"}'
            calls.append(prompt)
            if len(calls) == 1:
                return "엉뚱한 답변"
            return "정상 답변 지연 로딩 설명"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertEqual(event["final_quality_status"], "retried")
        self.assertTrue(event["answer_quality_retry_triggered"])
        self.assertFalse(event["answer_quality_passed"])
        self.assertFalse(event["answer_quality_fallback_triggered"])

    def test_metric_high_hallucination_fallback(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.8, "context_bias_score": 0.1, "hallucination_risk": "high", "should_retry": false, "reason": "환각"}'
            return "환각 답변"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertEqual(event["final_quality_status"], "fallback")
        self.assertTrue(event["answer_quality_fallback_triggered"])
        self.assertFalse(event["answer_quality_passed"])
        self.assertEqual(event["answer_hallucination_risk"], "high")

    def test_metric_context_bias_score_logged(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.9, "context_bias_score": 0.85, "hallucination_risk": "low", "should_retry": true, "reason": "바이어스"}'
            return "임시 답변"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertEqual(event["answer_context_bias_score"], 0.85)
        self.assertTrue(event["semantic_context_bias_detected"])

    def test_metric_judge_skipped_degraded(self):
        # We use a lambda that has no reflection compatibility matching judge key words -> skipped/unavailable -> degraded
        non_compatible_generator = lambda **kwargs: "지연 로딩은 필요한 시점에 데이터를 로드하는 방식입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 뭐야?",
            ),
            generator=non_compatible_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.semantic_judge_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertEqual(event["final_quality_status"], "degraded")
        self.assertTrue(event["answer_quality_degraded"])
        self.assertFalse(event["answer_quality_passed"])

    def test_grounding_grounded_answer_passes(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.95, "evidence_coverage": 0.9, "unsupported_claims": [], "grounded": true}'
            return "지연 로딩은 필요한 시점에 데이터를 로딩하는 방식입니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 왜 틀렸나 설명해줘.",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.grounding_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertTrue(event["grounded"])
        self.assertEqual(event["grounding_score"], 0.95)
        self.assertEqual(event["retrieval_coverage_score"], 0.9)
        self.assertFalse(event["unsupported_claim_detected"])

    def test_grounding_unsupported_hallucinated_answer_flagged(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.4, "evidence_coverage": 0.8, "unsupported_claims": ["지연 로딩 시 무조건 가비지 컬렉터가 작동하여 데이터가 자동 삭제됩니다."], "grounded": false}'
            return "지연 로딩 시 무조건 가비지 컬렉터가 작동하여 데이터가 자동 삭제됩니다."

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 왜 틀렸나 설명해줘.",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.grounding_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertFalse(event["grounded"])
        self.assertEqual(event["grounding_score"], 0.4)
        self.assertTrue(event["unsupported_claim_detected"])
        self.assertTrue(event["low_grounding_answer"])
        self.assertEqual(len(event["unsupported_claims"]), 1)
        self.assertEqual(event["unsupported_claims"][0], "지연 로딩 시 무조건 가비지 컬렉터가 작동하여 데이터가 자동 삭제됩니다.")

    def test_grounding_partial_grounding_detected(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.85, "evidence_coverage": 0.35, "unsupported_claims": [], "grounded": false}'
            return "부분 설명 답변"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 왜 틀렸나 설명해줘.",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.grounding_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertFalse(event["grounded"])
        self.assertEqual(event["retrieval_coverage_score"], 0.35)

    def test_grounding_retrieval_contamination_handled(self):
        def mock_generator(**kwargs):
            prompt = kwargs["prompt"]
            if "precise AI Semantic Judge" in prompt:
                return '{"relevance_score": 0.95, "context_bias_score": 0.1, "hallucination_risk": "low", "should_retry": false, "reason": "통과"}'
            if "precise Grounding Judge" in prompt:
                return '{"grounding_score": 0.5, "evidence_coverage": 0.7, "unsupported_claims": ["오염된 RecyclerView 뷰홀더 정보 언급"], "grounded": false}'
            return "오염된 뷰홀더 언급"

        response = run_review_workflow(
            mode="free-question",
            request=AiGenerateRequest(
                question="JPA 문제",
                user_answer="지연 로딩이 왜 틀렸나 설명해줘.",
            ),
            generator=mock_generator,
        )

        event = next((ev for ev in response.observability_events if ev.get("event") == "ai_review.grounding_evaluated"), None)
        self.assertIsNotNone(event)
        self.assertFalse(event["grounded"])
        self.assertEqual(event["grounding_score"], 0.5)
        self.assertTrue(event["low_grounding_answer"])

    def test_dashboard_payload_generation(self):
        from app.workflow.dashboard import generate_dashboard_payload
        
        events = [
            {
                "correlation_id": "session-1",
                "event": "ai_review.semantic_judge_evaluated",
                "relevance_score": 0.9,
                "context_bias_score": 0.1,
                "final_quality_status": "passed",
                "intent": "concept_definition",
                "sub_intent": "definition",
            },
            {
                "correlation_id": "session-1",
                "event": "ai_review.grounding_evaluated",
                "grounding_score": 0.8,
                "hallucination_risk": "low",
            },
            {
                "correlation_id": "session-2",
                "event": "ai_review.semantic_judge_evaluated",
                "relevance_score": 0.4,
                "context_bias_score": 0.8,
                "final_quality_status": "fallback",
                "intent": "wrong_answer_explanation",
                "sub_intent": "explanation",
            },
            {
                "correlation_id": "session-2",
                "event": "ai_review.grounding_evaluated",
                "grounding_score": 0.3,
                "hallucination_risk": "high",
            }
        ]

        payload = generate_dashboard_payload(events)
        
        self.assertEqual(len(payload["answer_relevance_trend"]), 2)
        self.assertEqual(payload["hallucination_risk_trend"]["high"], 1)
        self.assertEqual(payload["hallucination_risk_trend"]["low"], 1)
        self.assertEqual(payload["fallback_rate"], 0.5)
        self.assertEqual(payload["retry_rate"], 0.0)
        self.assertEqual(payload["low_grounding_high_hallucination_correlation"], 0.5)
        self.assertIn("concept_definition/definition", payload["intent_sub_intent_quality"])
        self.assertEqual(payload["intent_sub_intent_quality"]["concept_definition/definition"]["average_relevance"], 0.9)

    def test_alert_threshold_evaluation(self):
        from app.workflow.dashboard import evaluate_alerts
        
        # Scenario where fallback spike is triggered (e.g. 2 out of 5 fall back = 40% > 15%)
        events = [
            {"correlation_id": f"s-{i}", "event": "ai_review.semantic_judge_evaluated", "final_quality_status": "fallback" if i < 2 else "passed"}
            for i in range(5)
        ]
        
        alerts = evaluate_alerts(events)
        fallback_alert = next((a for a in alerts if a["alert"] == "fallback_spike"), None)
        self.assertIsNotNone(fallback_alert)
        self.assertEqual(fallback_alert["severity"], "critical")

        # Scenario where grounding collapse is triggered (average grounding = 0.5 < 0.75)
        events_grounding = [
            {"correlation_id": f"s-{i}", "event": "ai_review.grounding_evaluated", "grounding_score": 0.5}
            for i in range(5)
        ]
        
        alerts_g = evaluate_alerts(events_grounding)
        grounding_alert = next((a for a in alerts_g if a["alert"] == "grounding_score_collapse"), None)
        self.assertIsNotNone(grounding_alert)
        self.assertEqual(grounding_alert["severity"], "critical")

    def test_missing_metric_safety_handling(self):
        from app.workflow.dashboard import generate_dashboard_payload, evaluate_alerts
        
        # Feeds completely empty/missing metrics events
        corrupted_events = [
            {},
            {"event": "ai_review.semantic_judge_evaluated", "correlation_id": "s-1"},
            {"event": "ai_review.grounding_evaluated", "correlation_id": "s-2", "grounding_score": None, "relevance_score": "not-a-float"}
        ]
        
        # Must run successfully without exceptions and fall back to safe defaults
        try:
            payload = generate_dashboard_payload(corrupted_events)
            self.assertIsNotNone(payload)
            self.assertEqual(payload["average_relevance"], 1.0)
            self.assertEqual(payload["average_bias"], 0.0)
            
            alerts = evaluate_alerts(corrupted_events)
            self.assertEqual(alerts, [])  # Less than 5 valid total events or no alerts fired
        except Exception as exc:
            self.fail(f"Quality Dashboard Engine raised exception on missing metric events: {exc}")


if __name__ == "__main__":
    unittest.main()

