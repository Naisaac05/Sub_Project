import unittest

from app.schemas import AiGenerateRequest
from app.workflow.runner import run_review_workflow


class WorkflowRunnerTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()

