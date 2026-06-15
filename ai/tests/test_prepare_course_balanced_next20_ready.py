from __future__ import annotations

import unittest

from app.scripts import prepare_course_balanced_next20_ready as prepare


class PrepareCourseBalancedNext20ReadyTest(unittest.TestCase):
    def test_build_report_prepares_all_course_balanced_specs_without_applying(self):
        report = prepare.build_report(prepare.load_source_packets())

        self.assertEqual(report["candidate_count"], 20)
        self.assertEqual(report["ready_count"] + report["backlog_count"], 20)
        self.assertFalse(report["execution_performed"])
        self.assertFalse(report["card_files_modified"])

    def test_specs_have_distinct_wrong_reasons_and_behavior_examples(self):
        for card_id, spec in prepare.SPECS.items():
            with self.subTest(card_id=card_id):
                self.assertEqual(len(spec["wrong"]), 3)
                self.assertEqual(len(set(spec["wrong"])), 3)
                self.assertGreaterEqual(len(spec["code"].splitlines()), 3)
                self.assertNotIn("print(check_concept())", spec["code"])


if __name__ == "__main__":
    unittest.main()
