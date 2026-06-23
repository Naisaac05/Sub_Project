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
        self.assertEqual(verifiers.verifier_readiness("spring-aop"), "ready")
        self.assertEqual(verifiers.verifier_readiness("spring-responseentity"), "ready")

    def test_java_verifier_wraps_and_executes_statement_snippet(self):
        result = verifiers.verify("java", "int value = 2 + 3;\nassert value == 5;")

        self.assertTrue(result["passed"], result)

    def test_node_verifier_executes_javascript_module(self):
        result = verifiers.verify("node", "const value = 2 + 3;\nif (value !== 5) throw new Error('bad');")

        self.assertTrue(result["passed"], result)

    def test_known_dependency_gaps_remain_backlog(self):
        self.assertEqual(verifiers.verifier_readiness("spring-circuit"), "dependency_missing")

    def test_frontend_cards_use_node_harness(self):
        self.assertEqual(verifiers.verifier_readiness("frontend-react-server-components"), "ready")
        self.assertEqual(verifiers.verifier_readiness("frontend-usecallback"), "ready")
        self.assertEqual(verifiers.verifier_readiness("frontend-useref"), "ready")
        self.assertEqual(verifiers.verifier_readiness("frontend-dom"), "ready")


if __name__ == "__main__":
    unittest.main()
