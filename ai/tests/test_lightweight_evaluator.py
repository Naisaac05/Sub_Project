import unittest

from scripts.evaluate_lightweight_rag import evaluate_dataset, load_dataset


class LightweightEvaluatorTest(unittest.TestCase):
    def test_loads_bundled_dataset(self):
        rows = load_dataset()

        self.assertGreaterEqual(len(rows), 3)
        self.assertIn("question", rows[0])

    def test_evaluator_reports_retrieval_metrics(self):
        report = evaluate_dataset(load_dataset())

        self.assertGreaterEqual(report["total"], 3)
        self.assertGreaterEqual(report["retrieval_hit_rate"], 0.6)
        self.assertIn("expected_concept_recall", report)


if __name__ == "__main__":
    unittest.main()

