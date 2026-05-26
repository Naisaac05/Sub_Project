import json
import tempfile
import unittest
from pathlib import Path

from scripts.run_release_gate import (
    Check,
    evaluate_release_gate,
    main,
    run_release_gate,
)


class ReleaseGateTest(unittest.TestCase):
    def test_release_gate_returns_go_when_all_required_checks_pass(self):
        checks = [
            Check(name="unit", command=["python", "-m", "unittest"], cwd=Path(".")),
            Check(name="eval", command=["python", "eval.py"], cwd=Path("."), kind="rag_eval"),
        ]

        def runner(check):
            if check.name == "eval":
                return 0, json.dumps(
                    {
                        "total": 200,
                        "retrieval_hit_rate": 0.99,
                        "rag_policy_accuracy": 0.98,
                        "workflow_context_accuracy": 1.0,
                        "answer_grounding_rate": 0.9,
                    }
                ), ""
            return 0, "OK", ""

        report = run_release_gate(checks, runner=runner)

        self.assertEqual(report["decision"], "GO")
        self.assertEqual(report["failed_required_checks"], 0)
        self.assertTrue(all(check["passed"] for check in report["checks"]))

    def test_release_gate_returns_no_go_when_required_command_fails(self):
        checks = [Check(name="unit", command=["python", "-m", "unittest"], cwd=Path("."))]

        report = run_release_gate(checks, runner=lambda check: (1, "", "boom"))

        self.assertEqual(report["decision"], "NO-GO")
        self.assertEqual(report["failed_required_checks"], 1)
        self.assertFalse(report["checks"][0]["passed"])

    def test_release_gate_returns_no_go_when_rag_eval_threshold_fails(self):
        checks = [Check(name="eval", command=["python", "eval.py"], cwd=Path("."), kind="rag_eval")]

        report = run_release_gate(
            checks,
            runner=lambda check: (
                0,
                json.dumps(
                    {
                        "total": 120,
                        "retrieval_hit_rate": 0.99,
                        "rag_policy_accuracy": 0.98,
                        "workflow_context_accuracy": 1.0,
                    }
                ),
                "",
            ),
        )

        self.assertEqual(report["decision"], "NO-GO")
        self.assertIn("total >= 200", report["checks"][0]["failure_reason"])

    def test_main_writes_json_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "release-gate.json"
            exit_code = main(["--output", str(output), "--dry-run"])

            self.assertEqual(exit_code, 0)
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(payload["decision"], "GO")
            self.assertTrue(payload["checks"])

    def test_evaluate_release_gate_marks_optional_failure_as_conditional_go(self):
        checks = [
            {"name": "required", "required": True, "passed": True},
            {"name": "optional", "required": False, "passed": False},
        ]

        decision = evaluate_release_gate(checks)

        self.assertEqual(decision, "CONDITIONAL-GO")


if __name__ == "__main__":
    unittest.main()
