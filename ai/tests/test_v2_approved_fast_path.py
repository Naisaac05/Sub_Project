import os
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from app.schemas import AiGenerateRequest
from app.schemas.rag_card import RagCard
from app.workflow.embedding_intent import intent_from_label
from app.rag.documents import ConceptCard
from app.workflow.answer_cache import clear_answer_cache
from app.workflow.nodes import _approved_cards_for_scope, fallback_answer_node, generate_answer_node
from app.workflow.grounded_fallback import SAFE_GROUNDED_FALLBACK_ANSWER
from app.workflow.runner import _build_response_from_state, run_review_workflow, run_review_workflow_stream
from app.workflow.state import ReviewWorkflowState
from app.workflow.v2_approved_fast_path import (
    APPROVED_V2_CARD_IDS,
    V2FastPathDecision,
    _load_allowlisted_v2_cards_cached,
    approved_v2_card_ids,
    load_allowlisted_v2_cards,
    resolve_v2_approved_fast_path,
)
from app.rag.parallel_config import ParallelRagConfig


def _card(
    card_id: str = "frontend-react-key",
    *,
    card_status: str = "approved",
    payload_status: str = "approved",
) -> RagCard:
    category, term = card_id.split("-", 1)
    return RagCard.model_validate(
        {
            "card_id": card_id,
            "category": category,
            "term": term,
            "aliases": [term.replace("-", " ")],
            "retrieval": {
                "embedding_text": f"{term} {category}",
                "boost_keywords": [term, category],
            },
            "payloads": {
                "CONCEPT_DEFINITION": {"content": f"{term} definition"},
                "ANSWER_REASON": {"why_correct": f"{term} is correct", "key_points": [term]},
                "WRONG_ANSWER_REASON": {
                    "common_mistakes": [f"{term} mistake"],
                    "per_option": {},
                },
            },
            "review": {
                "card_status": card_status,
                "payload_status": {
                    "CONCEPT_DEFINITION": payload_status,
                    "ANSWER_REASON": payload_status,
                    "WRONG_ANSWER_REASON": payload_status,
                },
            },
        }
    )


class V2ApprovedFastPathPolicyTest(unittest.TestCase):
    def tearDown(self):
        _load_allowlisted_v2_cards_cached.cache_clear()

    def test_comparison_intent_uses_approved_concept_definition_payload(self):
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=True, v2_percentage=10),
        ):
            decision = resolve_v2_approved_fast_path(
                "Java equals와 ==의 차이는 무엇인가요?",
                intent_from_label("COMPARISON", "Java equals와 ==의 차이는 무엇인가요?", 0.99),
                card_loader=lambda _: [_card("java-equals")],
            )

        self.assertTrue(decision.hit)
        self.assertEqual(decision.card_id, "java-equals")
        self.assertEqual(decision.payload_intent, "CONCEPT_DEFINITION")

    def test_disabled_reason_has_clear_message(self):
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "false"}):
            decision = resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
            )

        self.assertEqual(decision.reason_message, "Fast Path 기능이 비활성화되어 있습니다")

    def test_disabled_mode_does_not_call_v2_loader(self):
        loader = Mock(side_effect=AssertionError("disabled mode must not read v2"))

        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "false"}):
            decision = resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                card_loader=loader,
            )

        loader.assert_not_called()
        self.assertFalse(decision.hit)
        self.assertEqual(decision.reason, "disabled")

    def test_allowlisted_cards_are_loaded_once_for_repeated_shadow_lookups(self):
        with patch("app.workflow.v2_approved_fast_path.parse_concept_card", return_value=_card()) as parse:
            load_allowlisted_v2_cards(["frontend-react-key"])
            load_allowlisted_v2_cards(["frontend-react-key"])

        parse.assert_called_once()

    def test_loader_receives_only_immutable_approved_card_allowlist(self):
        loaded_ids = []

        def loader(card_ids):
            loaded_ids.extend(card_ids)
            return [_card()]

        with patch.dict(
            os.environ,
            {
                "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true",
                "AI_REVIEW_V2_APPROVED_FAST_PATH_MODE": "shadow",
                "AI_REVIEW_V2_APPROVED_FAST_PATH_CARD_IDS": (
                        "frontend-react-key,java-equals,spring-spring-question-59,"
                    "java-extends,python-with,java-primitive,frontend-useeffect"
                    ",frontend-suspense"
                ),
            },
        ):
            resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                card_loader=loader,
            )

        self.assertEqual(
            set(loaded_ids),
            {"frontend-react-key", "java-equals", "spring-spring-question-59", "java-extends", "python-with", "java-primitive", "frontend-useeffect"},
        )
        self.assertNotIn("frontend-suspense", loaded_ids)
        self.assertIn("java-primitive", loaded_ids)

    def test_runtime_approved_ids_match_actual_v2_approved_cards(self):
        actual = {
            path.stem
            for path in (__import__("pathlib").Path(__file__).resolve().parents[1] / "app" / "knowledge" / "concepts_v2").rglob("*.json")
            if __import__("json").loads(path.read_text(encoding="utf-8-sig")).get("review", {}).get("card_status") == "approved"
        }

        self.assertEqual(approved_v2_card_ids(), frozenset(actual))
        self.assertEqual(APPROVED_V2_CARD_IDS, frozenset(actual))

    def test_draft_card_or_payload_is_never_a_hit(self):
        for card in (_card(card_status="draft"), _card(payload_status="draft")):
            with self.subTest(card_status=card.review.card_status, payload_status=card.review.payload_status):
                with patch.dict(
                    os.environ,
                    {
                        "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true",
                        "AI_REVIEW_V2_APPROVED_FAST_PATH_MODE": "shadow",
                    },
                ):
                    decision = resolve_v2_approved_fast_path(
                        "What is React key?",
                        intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                        card_loader=lambda _: [card],
                    )

                self.assertFalse(decision.hit)

    def test_non_approved_card_is_not_fast_path_eligible(self):
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=True, v2_percentage=10),
        ):
            decision = resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                card_loader=lambda _: [_card(card_status="approved_locked")],
            )

        self.assertFalse(decision.hit)

    def test_generic_overlap_without_term_or_specific_phrase_is_not_a_hit(self):
        unrelated = _card("java-int-variable")
        unrelated.aliases = ["Java", "integer variable"]
        unrelated.retrieval.boost_keywords = ["java", "variable", "declaration"]

        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=True, v2_percentage=10),
        ):
            decision = resolve_v2_approved_fast_path(
                "Java에서 문자열을 비교하는 방법은?",
                intent_from_label("CONCEPT_DEFINITION", "Java에서 문자열을 비교하는 방법은?", 0.99),
                card_loader=lambda _: [unrelated],
            )

        self.assertFalse(decision.hit)
        self.assertEqual(decision.reason, "anchor_miss")

    def test_enabled_default_mode_is_shadow(self):
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}, clear=True), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=True, v2_percentage=10),
        ):
            decision = resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                card_loader=lambda _: [_card()],
            )

        self.assertTrue(decision.hit)
        self.assertEqual(decision.mode, "shadow")

    def test_explicit_serve_mode_can_return_approved_payload(self):
        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}, clear=True), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=False, v2_percentage=10),
        ):
            decision = resolve_v2_approved_fast_path(
                "What is React key?",
                intent_from_label("CONCEPT_DEFINITION", "What is React key?", 0.99),
                card_loader=lambda _: [_card()],
                random_value=0.05,
            )

        self.assertTrue(decision.hit)
        self.assertEqual(decision.mode, "serve")
    def test_close_runner_up_score_is_rejected_as_margin_gate(self):
        from app.rag.retriever import RetrievedContext

        close_results = [
            RetrievedContext("python-fstring", "fstring", "", 9.0, {}),
            RetrievedContext("java-string", "string", "", 8.5, {}),
        ]

        class _StubRetriever:
            def __init__(self, *args, **kwargs):
                pass

            def retrieve(self, *args, **kwargs):
                return close_results

        with patch.dict(os.environ, {"AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true"}), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=False, v2_percentage=10),
        ), patch(
            "app.workflow.v2_approved_fast_path.LexicalRetrieverAdapter", _StubRetriever
        ):
            decision = resolve_v2_approved_fast_path(
                "fstring 이란 무엇인가요?",
                intent_from_label("CONCEPT_DEFINITION", "fstring 이란 무엇인가요?", 0.99),
                card_loader=lambda _: [_card("python-fstring"), _card("java-string")],
                random_value=0.05,
            )

        self.assertFalse(decision.hit)
        self.assertEqual(decision.reason, "margin_gate")
        self.assertEqual(decision.card_id, "python-fstring")
        self.assertEqual(decision.reason_message, "후보 카드 간 점수 차이가 부족합니다")

    def test_margin_gate_is_disabled_when_min_margin_is_zero(self):
        from app.rag.retriever import RetrievedContext

        close_results = [
            RetrievedContext("python-fstring", "fstring", "", 9.0, {}),
            RetrievedContext("java-string", "string", "", 8.5, {}),
        ]

        class _StubRetriever:
            def __init__(self, *args, **kwargs):
                pass

            def retrieve(self, *args, **kwargs):
                return close_results

        with patch.dict(
            os.environ,
            {
                "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true",
                "AI_REVIEW_V2_APPROVED_FAST_PATH_MIN_MARGIN": "0",
            },
        ), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(shadow_mode=False, v2_percentage=10),
        ), patch(
            "app.workflow.v2_approved_fast_path.LexicalRetrieverAdapter", _StubRetriever
        ):
            decision = resolve_v2_approved_fast_path(
                "fstring 이란 무엇인가요?",
                intent_from_label("CONCEPT_DEFINITION", "fstring 이란 무엇인가요?", 0.99),
                card_loader=lambda _: [_card("python-fstring"), _card("java-string")],
                random_value=0.05,
            )

        self.assertTrue(decision.hit)
        self.assertEqual(decision.card_id, "python-fstring")


class V2ApprovedFastPathWorkflowTest(unittest.TestCase):
    def setUp(self):
        clear_answer_cache()

    def tearDown(self):
        clear_answer_cache()

    def test_course_scope_ignores_markdown_cards_from_mixed_loader(self):
        markdown_card = ConceptCard(
            path=Path("index.md"),
            concept_id="index",
            metadata={},
            title="index",
            sections={},
        )
        approved_card = _card("java-equals")

        with patch(
            "app.workflow.nodes.load_concept_cards",
            return_value=[markdown_card, approved_card],
        ):
            cards = _approved_cards_for_scope()

        self.assertEqual(cards, [approved_card])

    def _state(self) -> ReviewWorkflowState:
        return ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="What is React key?"),
            free_question_intent=intent_from_label(
                "CONCEPT_DEFINITION",
                "What is React key?",
                0.99,
            ),
        )

    def test_shadow_mode_records_hit_but_preserves_existing_answer_flow(self):
        decision = V2FastPathDecision(
            mode="shadow",
            hit=True,
            reason="hit",
            card_id="frontend-react-key",
            payload_intent="CONCEPT_DEFINITION",
            answer="v2 shadow answer",
            score=10.0,
        )

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision), patch(
            "app.workflow.nodes.resolve_lightweight_answer",
            return_value=None,
        ):
            state = generate_answer_node(
                self._state(),
                generator=lambda **_: "React key는 리스트 요소를 안정적으로 식별해 렌더링 사이에 컴포넌트 상태를 유지합니다.",
            )

        self.assertIn("React key", state.answer)
        self.assertEqual(state.route, "grounded_fallback_generation")
        self.assertEqual(state.v2_fast_path_decision["mode"], "shadow")
        self.assertTrue(state.v2_fast_path_decision["hit"])

    def test_serve_mode_returns_approved_payload_without_generator(self):
        decision = V2FastPathDecision(
            mode="serve",
            hit=True,
            reason="hit",
            card_id="frontend-react-key",
            payload_intent="CONCEPT_DEFINITION",
            answer="v2 approved answer",
            score=10.0,
        )

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            state = generate_answer_node(
                self._state(),
                generator=lambda **_: (_ for _ in ()).throw(AssertionError("generator called")),
            )

        self.assertEqual(state.answer, "v2 approved answer")
        self.assertEqual(state.route, "v2_approved_fast_path")
        self.assertEqual(state.model_used, "v2-approved-payload")
        self.assertFalse(state.fallback_used)

    def test_exact_approved_hit_bypasses_course_scope_limit_in_serve_mode(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(
                user_answer="What is Java equals?",
                course_id="frontend",
            ),
            free_question_intent=intent_from_label(
                "CONCEPT_DEFINITION",
                "What is Java equals?",
                0.99,
            ),
        )

        def parse_by_path(path):
            return _card(Path(path).stem)

        with patch.dict(
            os.environ,
            {
                "AI_REVIEW_V2_APPROVED_FAST_PATH_ENABLED": "true",
                "AI_REVIEW_V2_APPROVED_FAST_PATH_CARD_IDS": "frontend-react-key,java-equals",
            },
        ), patch(
            "app.workflow.nodes.load_concept_cards",
            return_value=[_card("frontend-react-key"), _card("java-equals")],
        ), patch(
            "app.workflow.v2_approved_fast_path.load_parallel_rag_config",
            return_value=ParallelRagConfig(enabled=True, shadow_mode=False, v2_percentage=100),
        ), patch(
            "app.workflow.v2_approved_fast_path.parse_concept_card",
            side_effect=parse_by_path,
        ):
            state = generate_answer_node(
                state,
                generator=lambda **_: (_ for _ in ()).throw(AssertionError("generator called")),
            )

        self.assertEqual(state.route, "v2_approved_fast_path")
        self.assertEqual(state.answer, "equals definition")
        self.assertEqual(state.v2_fast_path_decision["card_id"], "java-equals")
        self.assertIn("course_scope_applied", state.quality_flags)

    def test_serve_mode_miss_without_approved_evidence_skips_ollama(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="retrieval_miss")
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="Java CopyOnWriteArrayList가 뭐야?"),
            free_question_intent=intent_from_label(
                "CONCEPT_DEFINITION", "Java CopyOnWriteArrayList가 뭐야?", 0.99
            ),
        )

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            state = generate_answer_node(
                state,
                generator=lambda **_: (_ for _ in ()).throw(AssertionError("generator called")),
            )

        self.assertEqual(state.answer, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertEqual(state.route, "grounded_fallback_safe_response")
        self.assertTrue(state.fallback_used)
        self.assertIn("missing_approved_evidence", state.quality_flags)

    def test_serve_mode_miss_uses_approved_evidence_in_prompt(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")
        prompts = []
        state = self._state()

        def generator(**kwargs):
            prompts.append(kwargs["prompt"])
            return "React key는 리스트 요소를 안정적으로 식별해 렌더링 사이에 컴포넌트 상태를 유지합니다."

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            state = generate_answer_node(state, generator=generator)

        self.assertEqual(state.route, "grounded_fallback_generation")
        self.assertFalse(state.fallback_used)
        self.assertIn("승인된 근거", prompts[0])
        self.assertIn("frontend-react-key", prompts[0])

    def test_serve_mode_miss_recovers_low_quality_generation_with_grounded_answer(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            state = generate_answer_node(
                self._state(),
                generator=lambda **_: "React key는 Spring 트랜잭션 기능으로",
            )

        self.assertNotEqual(state.answer, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertNotIn("Spring", state.answer)
        self.assertIn("React key", state.answer)
        self.assertEqual(state.route, "grounded_fallback_generation")
        self.assertFalse(state.fallback_used)
        self.assertNotIn("insufficient_evidence_overlap", state.quality_flags)

    def test_grounded_retry_also_passes_quality_gate(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")
        attempts = 0

        def generator(**_):
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise TimeoutError("primary timeout")
            return "React key는 Spring 트랜잭션 기능으로"

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            state = generate_answer_node(self._state(), generator=generator)

        self.assertEqual(attempts, 2)
        self.assertNotEqual(state.answer, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertNotIn("Spring", state.answer)
        self.assertIn("React key", state.answer)
        self.assertEqual(state.route, "grounded_fallback_generation")

    def test_cached_fallback_answer_must_pass_grounded_quality_gate(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision), patch(
            "app.workflow.nodes.get_cached_answer",
            return_value="React key는 Spring 트랜잭션 기능으로",
        ):
            state = generate_answer_node(
                self._state(),
                generator=lambda **_: (_ for _ in ()).throw(AssertionError("generator called")),
            )

        self.assertEqual(state.answer, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertEqual(state.route, "grounded_fallback_safe_response")

    def test_state_graph_recovers_low_quality_generation_with_grounded_answer(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")
        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            response = run_review_workflow(
                "free-question",
                AiGenerateRequest(user_answer="What is React key?"),
                generator=lambda **_: "React key는 Spring 트랜잭션 기능으로",
            )

        self.assertNotEqual(response.answer, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertNotIn("Spring", response.answer)
        self.assertEqual(response.route, "grounded_fallback_generation")
        self.assertFalse(response.fallback_used)

    def test_fallback_node_preserves_grounded_generation_route(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="Python asyncio를 비동기 작업에서 사용할 때 핵심을 설명해줘."),
        )
        state.answer = "asyncio는 승인된 근거로 생성된 답변입니다."
        state.route = "grounded_fallback_generation"
        state.quality_flags = ["missing_topic"]

        result = fallback_answer_node(state)

        self.assertEqual(result.answer, "asyncio는 승인된 근거로 생성된 답변입니다.")
        self.assertEqual(result.route, "grounded_fallback_generation")
        self.assertFalse(result.fallback_used)

    def test_shadow_decision_is_recorded_in_workflow_event(self):
        state = self._state()
        state.answer = "existing answer"
        state.route = "generation"
        state.model_used = "test-model"
        state.v2_fast_path_decision = {
            "mode": "shadow",
            "hit": True,
            "reason": "hit",
            "card_id": "frontend-react-key",
            "payload_intent": "CONCEPT_DEFINITION",
            "score": 10.0,
        }

        response = _build_response_from_state(state, latency_ms=1)
        completed = next(
            event for event in response.observability_events
            if event["event"] == "ai_review.workflow_completed"
        )

        self.assertEqual(completed["v2_fast_path"]["mode"], "shadow")
        self.assertTrue(completed["v2_fast_path"]["hit"])


class V2ApprovedFastPathStreamingTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        clear_answer_cache()

    def tearDown(self):
        clear_answer_cache()

    async def test_stream_miss_without_evidence_skips_generator(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="retrieval_miss")

        async def generator(**_):
            raise AssertionError("generator called")
            yield "unreachable"

        request = AiGenerateRequest(user_answer="Java CopyOnWriteArrayList가 뭐야?", stream=True)
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        chunks = "".join(event["chunk"] for event in events if event["type"] == "chunk")
        response = next(event["response"] for event in events if event["type"] == "done")
        self.assertEqual(chunks, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertEqual(response.route, "grounded_fallback_safe_response")

    async def test_stream_recovers_low_quality_generation_with_grounded_answer(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="unsupported_intent")

        async def generator(**_):
            yield "React key는 Spring 트랜잭션 기능으로"

        request = AiGenerateRequest(user_answer="What is React key?", stream=True)
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        chunks = "".join(event["chunk"] for event in events if event["type"] == "chunk")
        response = next(event["response"] for event in events if event["type"] == "done")
        self.assertNotEqual(chunks, SAFE_GROUNDED_FALLBACK_ANSWER)
        self.assertNotIn("Spring", chunks)
        self.assertIn("React key", chunks)
        self.assertEqual(response.route, "grounded_fallback_generation")

    async def test_fast_path_miss_retries_ollama_once_before_fallback(self):
        decision = V2FastPathDecision(mode="serve", hit=False, reason="retrieval_miss")
        attempts = 0

        async def generator(**_):
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise TimeoutError("first Ollama call timed out")
            yield "equals는 객체의 논리적 동등성을 비교하고 ==는 참조를 비교합니다."

        request = AiGenerateRequest(
            user_answer="Java에서 equals와 ==는 무엇이 다른가요?",
            stream=True,
        )
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision), patch(
            "app.workflow.semantic_gate.judge_answer_semantics",
            return_value=[],
        ):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        response = events[-1]["response"]
        self.assertEqual(attempts, 2)
        self.assertFalse(response.fallback_used)
        self.assertIn("논리적 동등성", response.answer)

    async def test_shadow_stream_records_hit_and_keeps_existing_stream(self):
        decision = V2FastPathDecision(
            mode="shadow", hit=True, reason="hit", card_id="java-equals",
            payload_intent="CONCEPT_DEFINITION", answer="v2 shadow answer", score=9.5,
        )

        generator_called = False

        async def generator(**_):
            nonlocal generator_called
            generator_called = True
            yield "기존 Ollama 스트리밍 응답"

        request = AiGenerateRequest(user_answer="Java equals와 ==의 차이는 무엇인가요?", stream=True)
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        response = events[-1]["response"]
        completed = next(event for event in response.observability_events if event["event"] == "ai_review.workflow_completed")
        self.assertTrue(generator_called)
        self.assertEqual(completed["v2_fast_path"]["mode"], "shadow")
        self.assertTrue(completed["v2_fast_path"]["hit"])

    async def test_serve_stream_returns_approved_payload_without_generator(self):
        decision = V2FastPathDecision(
            mode="serve", hit=True, reason="hit", card_id="java-equals",
            payload_intent="CONCEPT_DEFINITION", answer="Java equals 승인 payload", score=9.5,
        )

        async def generator(**_):
            raise AssertionError("generator must not be called")
            yield ""

        request = AiGenerateRequest(user_answer="Java equals와 ==의 차이는 무엇인가요?", stream=True)
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        self.assertEqual([event["type"] for event in events], ["start", "chunk", "done"])
        self.assertEqual(events[1]["chunk"], "Java equals 승인 payload")
        self.assertEqual(events[-1]["response"].route, "v2_approved_fast_path")
        self.assertEqual(events[-1]["response"].model_used, "v2-approved-payload")

    async def test_draft_payload_stream_decision_never_skips_generator(self):
        decision = V2FastPathDecision(
            mode="serve", hit=False, reason="payload_not_approved", card_id="java-equals",
            payload_intent="CONCEPT_DEFINITION", score=9.5,
        )

        generator_called = False

        async def generator(**_):
            nonlocal generator_called
            generator_called = True
            yield "기존 생성 응답"

        request = AiGenerateRequest(user_answer="Java equals가 무엇인가요?", stream=True)
        with patch("app.workflow.runner.resolve_v2_approved_fast_path", return_value=decision):
            events = [event async for event in run_review_workflow_stream("free-question", request, generator)]

        self.assertTrue(generator_called)
        self.assertNotEqual(events[-1]["response"].route, "v2_approved_fast_path")


if __name__ == "__main__":
    unittest.main()
