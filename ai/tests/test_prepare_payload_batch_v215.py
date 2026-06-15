from __future__ import annotations

import unittest

from app.scripts import prepare_payload_batch_v215 as prepare


class PreparePayloadBatchV215Test(unittest.TestCase):
    def test_repeated_template_phrase_has_diminishing_return(self):
        once = prepare.diminishing_count("정답은", ("정답은",))
        repeated = prepare.diminishing_count("정답은 정답은 정답은", ("정답은",))

        self.assertEqual(once, 1.0)
        self.assertLess(repeated, 3.0)
        self.assertGreater(repeated, once)

    def test_same_reason_ratio_detects_repeated_option_reasons(self):
        reasons = ["조건 불충족", "조건 불충족", "조건 불충족"]

        self.assertGreater(prepare.same_reason_ratio(reasons), 0.25)

    def test_example_quality_rejects_print_answer(self):
        score = prepare.example_metrics('print("정답")')

        self.assertEqual(score["print_answer"], 1.0)
        self.assertEqual(score["example_quality"], 0.0)
        self.assertEqual(score["fake_example_score"], 1.0)

    def test_example_quality_rejects_indirect_answer_printer(self):
        code = 'def check_concept():\n    return "큐"\n\nprint(check_concept())'

        score = prepare.example_metrics(code)

        self.assertEqual(score["print_answer"], 1.0)
        self.assertEqual(score["example_quality"], 0.0)

    def test_missing_and_null_payloads_are_counted_separately(self):
        metrics = prepare.payload_presence({"CONCEPT_DEFINITION": None})

        self.assertEqual(metrics["null_payload_count"], 1)
        self.assertGreater(metrics["missing_payload_count"], 0)

    def test_ready_rate_uses_preparation_target_not_discovery_count(self):
        result = prepare.separate_preparation(
            [{"card_id": f"card-{index}"} for index in range(30)],
            {"card-0": {"payloads": {}}},
            limit=10,
        )

        self.assertEqual(result["ready_count"], 1)
        self.assertEqual(result["backlog_count"], 9)
        self.assertEqual(result["ready_rate"], 0.1)


if __name__ == "__main__":
    unittest.main()
