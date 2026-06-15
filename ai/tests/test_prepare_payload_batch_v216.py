from __future__ import annotations

import unittest

from app.scripts import prepare_payload_batch_v216 as prepare


class PreparePayloadBatchV216Test(unittest.TestCase):
    def test_ready_patch_requires_fact_check_notes_and_patch_reason(self):
        result = prepare.validate_ready_patch({
            "payloads": {},
            "fact_check_notes": [],
            "patch_reason": "",
        })

        self.assertIn("missing_fact_check_notes", result)
        self.assertIn("missing_patch_reason", result)

    def test_ready_patch_rejects_repeated_wrong_reasons(self):
        patch = {
            "payloads": {
                "WRONG_ANSWER_REASON": {
                    "per_option": {
                        "a": {"text": "A", "reason": "조건 불충족"},
                        "b": {"text": "B", "reason": "조건 불충족"},
                        "c": {"text": "C", "reason": "조건 불충족"},
                    }
                },
                "EXAMPLE_REQUEST": {"code_example": "values = [1]\nvalues.append(2)\nlast = values[-1]"},
            },
            "fact_check_notes": ["검증"],
            "patch_reason": "개선",
        }

        self.assertIn("same_reason_ratio_over_25_percent", prepare.validate_ready_patch(patch))

    def test_ready_patch_rejects_answer_print_example(self):
        patch = {
            "payloads": {
                "WRONG_ANSWER_REASON": {"per_option": {}},
                "EXAMPLE_REQUEST": {"code_example": 'def check_concept():\n return "정답"\nprint(check_concept())'},
            },
            "fact_check_notes": ["검증"],
            "patch_reason": "개선",
        }

        self.assertIn("fake_or_print_answer_example", prepare.validate_ready_patch(patch))


if __name__ == "__main__":
    unittest.main()
