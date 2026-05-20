import unittest

from app.workflow.query_resolver import resolve_learner_query


class QueryResolverTest(unittest.TestCase):
    def test_alias_resolves_aria_label_spacing(self):
        result = resolve_learner_query("aria label이 무엇인가요?")

        self.assertEqual(result.correction_type, "alias")
        self.assertEqual(result.matched_term, "aria-label")
        self.assertEqual(result.matched_concept_id, "frontend-aria-label")
        self.assertIn("aria-label", result.resolved_query)
        self.assertGreaterEqual(result.confidence, 0.9)

    def test_known_typo_resolves_aria_label(self):
        result = resolve_learner_query("arila-label이 뭐야?")

        self.assertEqual(result.correction_type, "typo")
        self.assertEqual(result.matched_term, "aria-label")
        self.assertEqual(result.matched_concept_id, "frontend-aria-label")
        self.assertIn("aria-label", result.resolved_query)
        self.assertGreaterEqual(result.confidence, 0.85)

    def test_known_typo_resolves_pagination(self):
        result = resolve_learner_query("pagnation이 뭔지 모르겠음")

        self.assertEqual(result.correction_type, "typo")
        self.assertEqual(result.matched_term, "pagination")
        self.assertIn("pagination", result.resolved_query)

    def test_weak_short_match_keeps_original_query(self):
        result = resolve_learner_query("apii")

        self.assertEqual(result.correction_type, "none")
        self.assertEqual(result.resolved_query, "apii")
        self.assertIsNone(result.matched_concept_id)


if __name__ == "__main__":
    unittest.main()
