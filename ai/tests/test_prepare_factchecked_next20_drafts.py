from __future__ import annotations

import unittest

from app.scripts.prepare_factchecked_next20_drafts import SPECS, build_drafts
from app.scripts.prepare_payload_batch_v215 import example_metrics


class PrepareFactcheckedNext20DraftsTest(unittest.TestCase):
    def test_builds_twenty_drafts_without_promoting_or_applying(self):
        report = build_drafts()

        self.assertEqual(report["candidate_count"], 20)
        self.assertEqual(report["factcheck_draft_count"], 20)
        self.assertEqual(report["skipped_count"], 0)
        self.assertNotIn("PATCHES_READY", report)
        self.assertFalse(report["execution_performed"])
        self.assertFalse(report["card_files_modified"])
        self.assertEqual(report["cards_by_course"], {"java": 8, "python": 6, "algorithm": 6})

    def test_each_draft_has_distinct_wrong_reasons_and_fact_notes(self):
        report = build_drafts()

        for draft in report["FACTCHECK_DRAFTS"].values():
            reasons = [item["reason"] for item in draft["payloads"]["WRONG_ANSWER_REASON"]["per_option"].values()]
            self.assertEqual(len(reasons), len(set(reasons)))
            self.assertGreaterEqual(len(draft["fact_check_notes"]), 2)
            self.assertTrue(draft["patch_reason"])
            self.assertNotEqual(
                draft["payloads"]["CONCEPT_DEFINITION"]["examples"][0],
                draft["payloads"]["PRACTICAL_USAGE"]["real_world"],
            )

    def test_repaired_java_examples_meet_quality_gate(self):
        held = {"java-loop-control", "java-functional-interface", "java-stream", "java-reflection"}

        for card_id in held:
            metrics = example_metrics(SPECS[card_id][2])
            self.assertGreaterEqual(metrics["example_quality"], 0.7, card_id)
            self.assertLessEqual(metrics["fake_example_score"], 0.3, card_id)


if __name__ == "__main__":
    unittest.main()
