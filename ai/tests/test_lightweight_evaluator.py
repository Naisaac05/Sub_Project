import unittest
from types import SimpleNamespace

from scripts.evaluate_lightweight_rag import evaluate_dataset, load_dataset


class LightweightEvaluatorTest(unittest.TestCase):
    def test_loads_bundled_dataset(self):
        rows = load_dataset()

        self.assertGreaterEqual(len(rows), 50)
        self.assertIn("question", rows[0])

    def test_evaluator_reports_retrieval_metrics(self):
        report = evaluate_dataset(load_dataset())

        self.assertGreaterEqual(report["total"], 50)
        self.assertGreaterEqual(report["retrieval_hit_rate"], 0.6)
        self.assertIn("expected_concept_recall", report)
        self.assertIn("intent_accuracy", report)
        self.assertIn("rag_policy_accuracy", report)
        self.assertIn("stale_context_absent_rate", report)
        self.assertIn("route_accuracy", report)
        self.assertIn("quality_flag_absent_rate", report)
        self.assertIn("fallback_expectation_accuracy", report)
        self.assertIn("candidate_capture_accuracy", report)
        self.assertIn("observability_event_rate", report)
        self.assertGreaterEqual(report["rag_policy_accuracy"], 0.8)

    def test_evaluator_uses_workflow_response_for_route_keywords_quality_flags_and_ops_signals(self):
        rows = [
            {
                "id": "route-pass",
                "question": "What is N+1?",
                "expected_route": "static_fast_path",
                "required_keywords": ["N+1"],
                "forbidden_claims": ["unrelated"],
                "expected_quality_flags_absent": ["missing_topic"],
                "expected_fallback_used": False,
                "expected_candidate_captured": True,
                "expected_observability_event": True,
            },
            {
                "id": "route-fail",
                "question": "What is pagination?",
                "expected_route": "static_fast_path",
                "required_keywords": ["pagination"],
                "forbidden_claims": ["forbidden"],
                "expected_quality_flags_absent": ["missing_topic"],
                "expected_fallback_used": False,
                "expected_candidate_captured": True,
                "expected_observability_event": True,
            },
        ]

        def fake_workflow(row):
            if row["id"] == "route-pass":
                return SimpleNamespace(
                    route="static_fast_path",
                    answer="N+1 explanation",
                    quality_flags=[],
                    fallback_used=False,
                    candidate_id="auto-pass",
                    observability_events=[{"event": "ai_review.workflow_completed"}],
                )
            return SimpleNamespace(
                route="rag_generation",
                answer="forbidden answer",
                quality_flags=["missing_topic"],
                fallback_used=True,
                candidate_id="",
                observability_events=[],
            )

        report = evaluate_dataset(rows, workflow_runner=fake_workflow)

        self.assertEqual(report["workflow_rows"], 2)
        self.assertEqual(report["route_accuracy"], 0.5)
        self.assertEqual(report["answer_contains_required_keywords"], 0.5)
        self.assertEqual(report["forbidden_claims_absent"], 0.5)
        self.assertEqual(report["quality_flag_absent_rate"], 0.5)
        self.assertEqual(report["fallback_expectation_accuracy"], 0.5)
        self.assertEqual(report["candidate_capture_accuracy"], 0.5)
        self.assertEqual(report["observability_event_rate"], 0.5)


if __name__ == "__main__":
    unittest.main()
