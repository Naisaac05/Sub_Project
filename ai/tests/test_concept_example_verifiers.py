from __future__ import annotations

import unittest

from app.scripts import concept_example_verifiers as verifiers


class ConceptExampleVerifiersTest(unittest.TestCase):
    def test_unknown_verifier_is_explicitly_unavailable(self):
        result = verifiers.verify("missing", "", card_id="sample")

        self.assertFalse(result["passed"])
        self.assertEqual(result["status"], "unavailable")

    def test_spring_cards_have_real_harness_mappings(self):
        self.assertEqual(verifiers.verifier_readiness("spring-valid"), "ready")
        self.assertEqual(verifiers.verifier_readiness("spring-profile"), "ready")

    def test_known_dependency_gaps_remain_backlog(self):
        self.assertEqual(verifiers.verifier_readiness("spring-circuit"), "dependency_missing")
        self.assertEqual(verifiers.verifier_readiness("frontend-react-server-components"), "harness_missing")


if __name__ == "__main__":
    unittest.main()
