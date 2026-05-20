import unittest

from app.scoring import ConfidenceInputs, calculate_confidence


class ConfidenceScoringTest(unittest.TestCase):
    def test_calculates_weighted_normalized_score(self):
        result = calculate_confidence(
            ConfidenceInputs(
                retrieval_score=0.5,
                rule_match_score=1.0,
                answer_validation_score=0.5,
                model_self_check_score=1.0,
            )
        )

        self.assertAlmostEqual(result.score, 0.65)
        self.assertEqual(result.band, "medium")
        self.assertTrue(result.should_save_candidate)
        self.assertFalse(result.should_fallback)

    def test_low_score_requires_fallback(self):
        result = calculate_confidence(
            ConfidenceInputs(
                retrieval_score=0.0,
                rule_match_score=0.0,
                answer_validation_score=0.4,
                model_self_check_score=0.0,
            )
        )

        self.assertEqual(result.band, "low")
        self.assertTrue(result.should_fallback)
        self.assertTrue(result.should_save_candidate)

    def test_score_is_clamped_to_unit_range(self):
        result = calculate_confidence(
            ConfidenceInputs(
                retrieval_score=2.0,
                rule_match_score=2.0,
                answer_validation_score=2.0,
                model_self_check_score=2.0,
            )
        )

        self.assertEqual(result.score, 1.0)
        self.assertEqual(result.band, "high")


if __name__ == "__main__":
    unittest.main()

