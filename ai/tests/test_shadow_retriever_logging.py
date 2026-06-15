import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from app.rag.retriever import RetrievedContext
from app.rag.retriever import retrieve_context
from app.rag.shadow import log_shadow_retrieval


class ShadowRetrieverLoggingTest(unittest.TestCase):
    def test_retrieve_context_returns_v1_result_while_shadow_runs(self):
        v1 = RetrievedContext("v1-card", "v1", "content", 9.0, {})

        with patch("app.rag.retriever.select_retriever_adapter") as select, \
                patch("app.rag.retriever._run_v2_shadow") as shadow:
            select.return_value.retrieve.return_value = [v1]
            result = retrieve_context("question", limit=1, reranker=lambda items, _: items)

        self.assertEqual(result, [v1])
        shadow.assert_called_once_with("question")

    def test_writes_requested_shadow_fields(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "shadow.log"
            log_shadow_retrieval(
                path,
                query="What is React key?",
                intent="CONCEPT_DEFINITION",
                results=[
                    RetrievedContext("frontend-react-key", "react-key", "content", 10.0, {}),
                    RetrievedContext("frontend-useeffect", "useeffect", "content", 2.0, {}),
                ],
                payload_hit=True,
                fast_path_hit=True,
                latency_ms=1.25,
            )
            row = json.loads(path.read_text(encoding="utf-8").strip())

        self.assertEqual(row["retrieved_card_id"], "frontend-react-key")
        self.assertEqual(row["intent"], "CONCEPT_DEFINITION")
        self.assertTrue(row["payload_hit"])
        self.assertTrue(row["fast_path_hit"])
        self.assertEqual(len(row["top_3"]), 2)


if __name__ == "__main__":
    unittest.main()
