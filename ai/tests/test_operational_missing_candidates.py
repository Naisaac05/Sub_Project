import json
import tempfile
import unittest
from pathlib import Path

from scripts.extract_operational_missing_candidates import (
    extract_missing_candidates,
    load_operational_shadow_report,
)


class OperationalMissingCandidatesTest(unittest.TestCase):
    def test_extracts_only_repeated_production_missing_questions(self):
        report = {
            "production_traffic_validated": True,
            "rows": [
                {
                    "id": "copy-1",
                    "question": "Java CopyOnWriteArrayList overview",
                    "route": "grounded_fallback_safe_response",
                    "expected_route": "grounded_fallback_safe_response",
                },
                {
                    "id": "copy-2",
                    "question": "Java CopyOnWriteArrayList behavior",
                    "route": "grounded_fallback_safe_response",
                    "expected_route": "grounded_fallback_safe_response",
                },
                {
                    "id": "next-1",
                    "question": "Next.js Server Action security concerns",
                    "route": "grounded_fallback_safe_response",
                    "expected_route": "grounded_fallback_safe_response",
                },
            ],
        }

        candidates = extract_missing_candidates(report, min_occurrences=2)

        self.assertEqual(len(candidates), 1)
        self.assertEqual(candidates[0]["term"], "CopyOnWriteArrayList")
        self.assertEqual(candidates[0]["category"], "java")
        self.assertEqual(candidates[0]["occurrences"], 2)
        self.assertFalse(candidates[0]["approved"])

    def test_requires_production_validation_by_default(self):
        report = {
            "production_traffic_validated": False,
            "rows": [
                {
                    "id": "copy-1",
                    "question": "Java CopyOnWriteArrayList overview",
                    "route": "grounded_fallback_safe_response",
                    "expected_route": "grounded_fallback_safe_response",
                },
                {
                    "id": "copy-2",
                    "question": "Java CopyOnWriteArrayList behavior",
                    "route": "grounded_fallback_safe_response",
                    "expected_route": "grounded_fallback_safe_response",
                },
            ],
        }

        self.assertEqual(extract_missing_candidates(report, min_occurrences=2), [])

    def test_loads_report_from_json_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "report.json"
            path.write_text(json.dumps({"rows": []}), encoding="utf-8")

            loaded = load_operational_shadow_report(path)

        self.assertEqual(loaded["rows"], [])


if __name__ == "__main__":
    unittest.main()
