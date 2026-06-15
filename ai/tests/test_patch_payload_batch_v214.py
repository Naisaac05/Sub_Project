from __future__ import annotations

import unittest

from app.scripts import patch_payload_batch_v214 as patch


class PayloadBatchV214Test(unittest.TestCase):
    def test_eligible_statuses_exclude_only_rejected(self):
        self.assertTrue(patch.is_eligible({"review": {"card_status": "draft"}}))
        self.assertTrue(patch.is_eligible({"review": {"card_status": "approved"}}))
        self.assertTrue(patch.is_eligible({"review": {"card_status": "approved_locked"}}))
        self.assertTrue(patch.is_eligible({"review": {"card_status": "needs_revision"}}))
        self.assertFalse(patch.is_eligible({"review": {"card_status": "rejected"}}))

    def test_batch_sizes_insert_second_forty_before_remaining(self):
        self.assertEqual(patch.batch_sizes(142), [10, 20, 40, 40, 32])

    def test_stop_expansion_checks_all_override_conditions(self):
        clean = {
            "patch_rate": 0.2,
            "production_hit_diff": 0.0,
            "production_loo_diff": 0.0,
            "json_failed": [],
            "content_hit_diff": -0.005,
        }
        self.assertEqual(patch.expansion_stop_reasons(clean), [])
        for key, value in (
            ("patch_rate", 0.149),
            ("production_hit_diff", -0.001),
            ("production_loo_diff", -0.001),
            ("json_failed", ["bad-card"]),
            ("content_hit_diff", -0.011),
        ):
            report = dict(clean)
            report[key] = value
            self.assertTrue(patch.expansion_stop_reasons(report), key)

    def test_candidate_score_prioritizes_template_phrase_count(self):
        generic = {
            "card_id": "generic",
            "review": {"card_status": "draft"},
            "payloads": {"ANSWER_REASON": {"why_correct": "정답은 질문이 요구한 실행 결과를 보장한다."}},
        }
        concise = {
            "card_id": "concise",
            "review": {"card_status": "draft"},
            "payloads": {"ANSWER_REASON": {"why_correct": "스택은 최근 정점을 먼저 방문한다."}},
        }

        ranked = patch.rank_candidates([concise, generic])

        self.assertEqual(ranked[0]["card_id"], "generic")
        self.assertGreater(ranked[0]["score"]["template_phrase_count"], 0)

    def test_candidate_score_accepts_null_optional_payload(self):
        card = {
            "card_id": "null-example",
            "review": {"card_status": "draft"},
            "payloads": {"EXAMPLE_REQUEST": None},
        }

        score = patch.candidate_score(card)

        self.assertEqual(score["example_quality"], 0.0)


if __name__ == "__main__":
    unittest.main()
