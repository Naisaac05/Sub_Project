import unittest
from unittest.mock import patch

from app.schemas import AiGenerateRequest
from app.workflow.nodes import generate_answer_node
from app.workflow.state import ReviewWorkflowState
from app.workflow.v2_approved_fast_path import V2FastPathDecision


class V1GeneratedCardRemovalTest(unittest.TestCase):
    def test_v2_miss_calls_generator_without_v1_lightweight_lookup(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="circuit breaker가 뭐야?"),
        )
        decision = V2FastPathDecision(mode="serve", hit=False, reason="retrieval_miss")

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision), patch(
            "app.workflow.nodes.resolve_lightweight_answer",
            side_effect=AssertionError("v1 lightweight lookup must not run"),
        ):
            result = generate_answer_node(
                state,
                generator=lambda **_: "circuit breaker는 장애 전파를 줄이는 패턴입니다.",
            )

        self.assertEqual(result.route, "generation")
        self.assertIn("circuit breaker", result.answer)

    def test_v2_hit_skips_generator(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="ArrayList가 뭐야?"),
        )
        decision = V2FastPathDecision(
            mode="serve",
            hit=True,
            reason="hit",
            card_id="java-arraylist",
            payload_intent="CONCEPT_DEFINITION",
            answer="ArrayList는 인덱스 조회가 빠른 가변 길이 목록입니다.",
            score=10.0,
        )

        with patch("app.workflow.nodes.resolve_v2_approved_fast_path", return_value=decision):
            result = generate_answer_node(
                state,
                generator=lambda **_: (_ for _ in ()).throw(AssertionError("generator called")),
            )

        self.assertEqual(result.route, "v2_approved_fast_path")
        self.assertEqual(result.model_used, "v2-approved-payload")


if __name__ == "__main__":
    unittest.main()
