from __future__ import annotations

import unittest

from app.scripts import apply_payload_batch_v217 as apply
from app.scripts.migrate_rag_cards import RetrievalMetrics


class ApplyPayloadBatchV217Test(unittest.TestCase):
    def test_acceptance_rejects_any_production_change(self):
        before = RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        after = RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 4.9, 0.8)

        reasons = apply.card_acceptance_reasons(before, before, after, before)

        self.assertIn("production_loo_score_changed", reasons)

    def test_acceptance_rejects_any_content_hit_decline(self):
        before = RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        content_after = RetrievalMetrics(0.899, 0.9, 0.9, 1.0, 5.0, 0.8)

        reasons = apply.card_acceptance_reasons(before, before, before, content_after)

        self.assertIn("content_hit_declined", reasons)

    def test_acceptance_allows_content_loo_only_change(self):
        before = RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 5.0, 0.8)
        content_after = RetrievalMetrics(0.9, 0.9, 0.9, 1.0, 4.0, 0.8)

        reasons = apply.card_acceptance_reasons(before, before, before, content_after)

        self.assertEqual(reasons, [])


if __name__ == "__main__":
    unittest.main()
