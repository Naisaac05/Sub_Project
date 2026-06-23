import unittest

from app.schemas import AiGenerateResponse
from scripts.evaluate_grounded_fallback_live import assess_transition_readiness, evaluate_cases


class GroundedFallbackLiveEvaluationTest(unittest.TestCase):
    def test_accepts_grounded_generation_and_safe_response_routes(self):
        responses = iter([
            AiGenerateResponse(answer="근거 답변", route="grounded_fallback_generation", fallback_used=False),
            AiGenerateResponse(answer="안전 응답", route="grounded_fallback_safe_response", fallback_used=True),
        ])

        report = evaluate_cases(
            [{"id": "grounded", "question": "known"}, {"id": "missing", "question": "unknown"}],
            runner=lambda _: next(responses),
        )

        self.assertEqual(report["passed_count"], 2)
        self.assertTrue(report["gate_passed"])

    def test_rejects_unvalidated_generation_route(self):
        report = evaluate_cases(
            [{"id": "unsafe", "question": "unknown"}],
            runner=lambda _: AiGenerateResponse(answer="모델 원문", route="generation", fallback_used=False),
        )

        self.assertEqual(report["passed_count"], 0)
        self.assertFalse(report["gate_passed"])

    def test_expected_generation_case_rejects_safe_response(self):
        report = evaluate_cases(
            [{"id": "grounded", "question": "known", "expected_route": "grounded_fallback_generation"}],
            runner=lambda _: AiGenerateResponse(
                answer="안전 응답",
                route="grounded_fallback_safe_response",
                fallback_used=True,
            ),
        )

        self.assertEqual(report["passed_count"], 0)
        self.assertFalse(report["gate_passed"])

    def test_summarizes_operational_shadow_route_counts(self):
        responses = iter([
            AiGenerateResponse(answer="근거 답변", route="grounded_fallback_generation", fallback_used=False),
            AiGenerateResponse(answer="근거 답변", route="grounded_fallback_generation", fallback_used=False),
            AiGenerateResponse(answer="안전 응답", route="grounded_fallback_safe_response", fallback_used=True),
        ])

        report = evaluate_cases(
            [
                {"id": "known-1", "question": "known 1", "expected_route": "grounded_fallback_generation"},
                {"id": "known-2", "question": "known 2", "expected_route": "grounded_fallback_generation"},
                {"id": "missing", "question": "unknown", "expected_route": "grounded_fallback_safe_response"},
            ],
            runner=lambda _: next(responses),
        )

        self.assertEqual(report["route_counts"]["grounded_fallback_generation"], 2)
        self.assertEqual(report["route_counts"]["grounded_fallback_safe_response"], 1)
        self.assertEqual(report["approved_generation_rate"], 1.0)
        self.assertEqual(report["safe_response_rate"], 1 / 3)

    def test_transition_readiness_allows_shadow_but_blocks_serve_without_production_traffic(self):
        report = {
            "case_count": 6,
            "passed_count": 6,
            "gate_passed": True,
            "approved_generation_rate": 1.0,
            "missing_evidence_skipped_model": True,
            "actual_model_call_verified": True,
            "unsafe_route_count": 0,
            "production_traffic_validated": False,
        }

        readiness = assess_transition_readiness(report)

        self.assertEqual(readiness["shadow_readiness"], "READY")
        self.assertEqual(readiness["serve_readiness"], "NOT_READY")
        self.assertIn("production_shadow_not_validated", readiness["serve_blockers"])


if __name__ == "__main__":
    unittest.main()
