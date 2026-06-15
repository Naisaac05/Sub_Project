import unittest
from unittest.mock import patch

from app.schemas import AiGenerateRequest
from app.workflow.semantic_gate import semantic_evaluate_node
from app.workflow.embedding_intent import intent_from_label
from app.workflow.state import ReviewWorkflowState
from app.evaluation.semantic import judge_answer_semantics, should_cache_answer


class SemanticEvaluationTest(unittest.TestCase):
    def test_contradiction_suspected_answer_becomes_quality_fallback(self):
        state = ReviewWorkflowState(
            mode="free-question",
            request=AiGenerateRequest(user_answer="Java에서 equals와 ==는 무엇이 다른가요?"),
            answer="equals는 메모리 주소만 비교합니다.",
            route="generation",
            model_used="exaone3.5:2.4b",
            free_question_intent=intent_from_label(
                "COMPARISON",
                "Java에서 equals와 ==는 무엇이 다른가요?",
                confidence=0.99,
            ),
        )

        with patch(
            "app.workflow.semantic_gate.judge_answer_semantics",
            return_value=["contradiction_suspected"],
        ):
            result = semantic_evaluate_node(state)

        self.assertTrue(result.fallback_used)
        self.assertEqual(result.fallback_reason, "quality_validation")
        self.assertIn("더 정확한 답변을 준비하고 있습니다", result.answer)
        self.assertIn("논리적 동등성", result.answer)
    def test_flags_ungrounded_concrete_generated_answer_as_hallucination_suspected(self):
        flags = judge_answer_semantics(
            answer="Spring에서 @Transactional은 모든 DTO 변경을 자동으로 DB에 저장합니다.",
            route="generation",
            fallback_used=False,
            retrieved_concept_ids=[],
            context_text="",
            existing_quality_flags=[],
        )

        self.assertIn("evidence_missing", flags)
        self.assertIn("hallucination_suspected", flags)
        self.assertFalse(should_cache_answer(flags))

    def test_does_not_flag_template_or_lightweight_answer_without_context(self):
        for route in ("fallback_template", "static_fast_path", "generated_card_fast_path", "cache"):
            flags = judge_answer_semantics(
                answer="간단한 설명입니다.",
                route=route,
                fallback_used=(route == "fallback_template"),
                retrieved_concept_ids=[],
                context_text="",
                existing_quality_flags=[],
            )

            self.assertNotIn("hallucination_suspected", flags)
            self.assertTrue(should_cache_answer(flags))

    def test_flags_answer_that_mentions_unretrieved_stale_concept(self):
        flags = judge_answer_semantics(
            answer="이 문제는 N+1과 spring-n-plus-one이 핵심입니다.",
            route="rag_generation",
            fallback_used=False,
            retrieved_concept_ids=["java-equals"],
            context_text="equals와 hashCode 설명",
            existing_quality_flags=[],
        )

        self.assertIn("contradiction_suspected", flags)
        self.assertFalse(should_cache_answer(flags))


if __name__ == "__main__":
    unittest.main()
