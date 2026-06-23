import unittest

from scripts.evaluate_synthetic_shadow_traffic import evaluate_shadow_cases


class SyntheticShadowTrafficTest(unittest.TestCase):
    def test_aggregates_shadow_hit_miss_and_irrelevant_hit(self):
        decisions = {
            "known": {"mode": "shadow", "hit": True, "card_id": "java-equals", "reason": "hit"},
            "unknown": {"mode": "shadow", "hit": False, "card_id": None, "reason": "retrieval_miss"},
        }
        report = evaluate_shadow_cases(
            [
                {"id": "known", "question": "known", "expected_card_id": "java-equals"},
                {"id": "unknown", "question": "unknown", "expected_card_id": None},
            ],
            resolver=lambda question: decisions[question],
        )

        self.assertEqual(report["shadow_mode_rate"], 1.0)
        self.assertEqual(report["top1_relevance_rate"], 1.0)
        self.assertEqual(report["fast_path_hit_rate"], 0.5)
        self.assertEqual(report["fallback_rate"], 0.5)
        self.assertEqual(report["irrelevant_hit_count"], 0)

    def test_wrong_card_is_counted_as_irrelevant_hit(self):
        report = evaluate_shadow_cases(
            [{"id": "wrong", "question": "wrong", "expected_card_id": "java-equals"}],
            resolver=lambda _: {"mode": "shadow", "hit": True, "card_id": "java-arraylist", "reason": "hit"},
        )

        self.assertEqual(report["irrelevant_hit_count"], 1)
        self.assertEqual(report["top1_relevance_rate"], 0.0)


if __name__ == "__main__":
    unittest.main()
