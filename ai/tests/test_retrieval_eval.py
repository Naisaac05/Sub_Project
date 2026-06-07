import math
import os
import unittest

import retrieval_eval
from app.rag.retriever import RetrievedContext


def result(concept_id: str, score: float) -> RetrievedContext:
    return RetrievedContext(
        concept_id=concept_id,
        title=concept_id,
        content=concept_id,
        score=score,
        metadata={},
    )


class RetrievalEvalTest(unittest.TestCase):
    def test_metrics_capture_recall_precision_hit_mrr_and_ndcg(self):
        metrics = retrieval_eval.evaluate_ranking(
            expected_concepts=["a", "b"],
            ranked_concepts=["x", "b", "a"],
            k_values=[1, 2, 3],
        )

        self.assertEqual(metrics["recall@1"], 0.0)
        self.assertEqual(metrics["recall@2"], 0.5)
        self.assertEqual(metrics["recall@3"], 1.0)
        self.assertEqual(metrics["precision@2"], 0.5)
        self.assertEqual(metrics["hit@2"], 1.0)
        self.assertEqual(metrics["mrr"], 0.5)
        self.assertTrue(math.isclose(metrics["ndcg@3"], 0.693, abs_tol=0.001))

    def test_rrf_prefers_document_supported_by_multiple_rankers(self):
        fused = retrieval_eval.fuse_rrf(
            {
                "bm25": [result("a", 8.0), result("b", 7.0)],
                "dense_bge_m3": [result("b", 0.9), result("c", 0.8)],
            },
            limit=3,
            rrf_k=60,
        )

        self.assertEqual([item.concept_id for item in fused], ["b", "a", "c"])
        self.assertEqual(fused[0].metadata["retriever_sources"], "bm25,dense_bge_m3")

    def test_weighted_sum_normalizes_each_ranker_before_merging(self):
        fused = retrieval_eval.fuse_weighted_sum(
            {
                "bm25": [result("a", 100.0), result("b", 50.0)],
                "dense_bge_m3": [result("b", 0.9), result("c", 0.8)],
            },
            weights={"bm25": 0.5, "dense_bge_m3": 0.5},
            limit=3,
        )

        self.assertEqual(fused[0].concept_id, "b")
        self.assertGreater(fused[0].score, fused[1].score)

    def test_dense_bge_m3_uses_local_files_only_by_default(self):
        calls = []

        def fake_model_factory(model_name: str, **kwargs):
            calls.append((model_name, kwargs, os.environ.get("HF_HUB_OFFLINE")))
            return object()

        retriever = retrieval_eval.DenseBgeM3Retriever(model_factory=fake_model_factory)

        retriever._load_model()

        self.assertEqual(calls[0][0], "BAAI/bge-m3")
        self.assertEqual(calls[0][1]["local_files_only"], True)
        self.assertEqual(calls[0][2], "1")


if __name__ == "__main__":
    unittest.main()
