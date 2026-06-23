import json
import tempfile
import unittest
from pathlib import Path

from app.schemas import AiGenerateResponse
from scripts.evaluate_operational_shadow import (
    evaluate_operational_shadow_cases,
    load_cases_from_jsonl,
)


class OperationalShadowVerificationTest(unittest.TestCase):
    def test_loads_jsonl_cases_and_normalizes_expected_route_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "shadow.jsonl"
            path.write_text(
                "\n".join(
                    [
                        json.dumps({"id": "known", "question": "React key?", "expected": "approved"}),
                        json.dumps({"id": "missing", "question": "Unknown?", "expected": "missing"}),
                        "",
                    ]
                ),
                encoding="utf-8",
            )

            cases = load_cases_from_jsonl(path)

        self.assertEqual(cases[0]["expected_route"], "grounded_fallback_generation")
        self.assertEqual(cases[1]["expected_route"], "grounded_fallback_safe_response")

    def test_evaluates_operational_shadow_and_marks_serve_ready_when_production_validated(self):
        responses = iter(
            [
                AiGenerateResponse(
                    answer="근거 답변",
                    route="grounded_fallback_generation",
                    fallback_used=False,
                ),
                AiGenerateResponse(
                    answer="안전 응답",
                    route="grounded_fallback_safe_response",
                    fallback_used=True,
                ),
            ]
        )

        report = evaluate_operational_shadow_cases(
            [
                {
                    "id": "known",
                    "question": "React key?",
                    "expected_route": "grounded_fallback_generation",
                },
                {
                    "id": "missing",
                    "question": "Unknown?",
                    "expected_route": "grounded_fallback_safe_response",
                },
            ],
            runner=lambda _: next(responses),
            model_calls={"known": 1, "missing": 0},
            production_traffic_validated=True,
        )

        self.assertTrue(report["overall_gate_passed"])
        self.assertEqual(report["transition_readiness"]["shadow_readiness"], "READY")
        self.assertEqual(report["transition_readiness"]["serve_readiness"], "READY")


if __name__ == "__main__":
    unittest.main()
