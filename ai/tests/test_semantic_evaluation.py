import unittest

from app.evaluation.semantic import judge_answer_semantics, should_cache_answer


class SemanticEvaluationTest(unittest.TestCase):
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
