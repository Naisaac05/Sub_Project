import unittest

from app.rag.retriever import (
    BM25RetrieverAdapter,
    ChromaBgeRetrieverAdapter,
    FlashrankReranker,
    HybridRetrieverAdapter,
    LexicalRetrieverAdapter,
    RetrievedContext,
    WeightedRetriever,
    build_optional_reranker,
    retrieve_context,
    select_retriever_adapter,
    select_tokenizer,
)
from app.rag.documents import ConceptCard


class FakeAdapter:
    def __init__(self):
        self.calls = []

    def retrieve(self, query: str, limit: int = 5, reranker=None):
        self.calls.append((query, limit, reranker))
        return [
            RetrievedContext(
                concept_id="fake-concept",
                title="Fake",
                content="Fake context",
                score=42.0,
                metadata={"source": "fake"},
            )
        ]


class StaticRetriever:
    def __init__(self, results: list[RetrievedContext]):
        self.results = results
        self.calls = []

    def retrieve(self, query: str, limit: int = 5) -> list[RetrievedContext]:
        self.calls.append((query, limit))
        return self.results[:limit]


def card(concept_id: str, title: str, body: str, keywords: str = "") -> ConceptCard:
    return ConceptCard(
        path=None,
        concept_id=concept_id,
        metadata={"id": concept_id},
        title=title,
        sections={
            "핵심 설명": body,
            "평가 키워드": keywords,
        },
    )


class RagRetrieverTest(unittest.TestCase):
    def test_retrieve_context_can_use_injected_adapter(self):
        adapter = FakeAdapter()

        results = retrieve_context("hybrid query", limit=1, adapter=adapter)

        self.assertEqual(results[0].concept_id, "fake-concept")
        self.assertEqual(adapter.calls[0][0], "hybrid query")
        self.assertEqual(adapter.calls[0][1], 1)

    def test_hybrid_adapter_merges_weighted_sources_and_deduplicates(self):
        lexical = StaticRetriever(
            [
                RetrievedContext("shared", "Lexical", "lexical", 2.0, {"source": "lexical"}),
                RetrievedContext("lexical-only", "Lexical Only", "lexical only", 3.0, {}),
            ]
        )
        semantic = StaticRetriever(
            [
                RetrievedContext("shared", "Semantic", "semantic", 6.0, {"source": "semantic"}),
                RetrievedContext("semantic-only", "Semantic Only", "semantic only", 4.0, {}),
            ]
        )
        adapter = HybridRetrieverAdapter(
            [
                WeightedRetriever("lexical", 1.0, lexical.retrieve),
                WeightedRetriever("semantic", 0.5, semantic.retrieve),
            ]
        )

        results = adapter.retrieve("query", limit=3)

        self.assertEqual(
            [item.concept_id for item in results],
            ["shared", "lexical-only", "semantic-only"],
        )
        self.assertEqual(results[0].score, 5.0)
        self.assertEqual(results[0].metadata["retriever_sources"], "lexical,semantic")

    def test_low_resource_hybrid_uses_lexical_and_bm25_without_vector(self):
        adapter = select_retriever_adapter("hybrid:low_resource")

        self.assertIsInstance(adapter, HybridRetrieverAdapter)
        self.assertEqual(
            [retriever.name for retriever in adapter.retrievers],
            ["lexical", "bm25"],
        )

    def test_high_performance_hybrid_has_optional_vector_slot(self):
        adapter = select_retriever_adapter("hybrid:high_performance")

        self.assertIsInstance(adapter, HybridRetrieverAdapter)
        self.assertIn("chroma_bge_m3", [retriever.name for retriever in adapter.retrievers])

    def test_bm25_retriever_ranks_term_dense_card_first(self):
        retriever = BM25RetrieverAdapter(
            card_loader=lambda: [
                card("spring-n-plus-one", "N+1", "fetch join batch size n plus one", "n+1 fetch join"),
                card("java-stream", "Stream", "map filter collect", "stream"),
            ]
        )

        results = retriever.retrieve("n+1 fetch join", limit=2)

        self.assertEqual(results[0].concept_id, "spring-n-plus-one")
        self.assertEqual(results[0].metadata["retriever"], "bm25")

    def test_kiwipiepy_tokenizer_hook_falls_back_without_dependency(self):
        tokenizer = select_tokenizer("kiwipiepy")

        self.assertIn("n+1", tokenizer("N+1 fetch join"))

    def test_chroma_adapter_returns_empty_when_disabled_or_unavailable(self):
        adapter = ChromaBgeRetrieverAdapter(enabled=False)

        self.assertEqual(adapter.retrieve("n+1", limit=3), [])

    def test_flashrank_reranker_is_disabled_by_default(self):
        self.assertIsNone(build_optional_reranker("none"))

    def test_flashrank_reranker_falls_back_when_dependency_missing(self):
        reranker = FlashrankReranker(enabled=False)
        items = [RetrievedContext("a", "A", "alpha", 1.0, {})]

        self.assertEqual(reranker(items, "alpha"), items)

    def test_retrieves_n_plus_one_card_first(self):
        results = retrieve_context("N+1 문제 지연 로딩 추가 쿼리", limit=2)

        self.assertGreaterEqual(len(results), 1)
        self.assertEqual(results[0].concept_id, "spring-n-plus-one")

    def test_unknown_query_returns_empty_list(self):
        results = retrieve_context("unrelated grocery shopping", limit=2)

        self.assertEqual(results, [])

    def test_optional_reranker_can_reorder_results(self):
        def reverse_reranker(items: list[RetrievedContext], query: str) -> list[RetrievedContext]:
            self.assertIn("N+1", query)
            return list(reversed(items))

        baseline = retrieve_context("N+1 fetch join", limit=3)
        reranked = retrieve_context("N+1 fetch join", limit=3, reranker=reverse_reranker)

        self.assertGreaterEqual(len(baseline), 2)
        self.assertEqual(reranked[0].concept_id, baseline[-1].concept_id)

    def test_generic_korean_question_returns_empty_list(self):
        results = retrieve_context("이게 뭐야?", limit=3)

        self.assertEqual(results, [])

    def test_retrieves_generated_cards_by_aliases_and_typos(self):
        cases = [
            ("aria label 접근성", "frontend-aria-label"),
            ("aria lable 접근성", "frontend-aria-label"),
            ("ConrollerAdvice 예외 처리", "java-backend-controlleradvice"),
            ("Controller Advice Spring MVC", "java-backend-controlleradvice"),
        ]

        for query, expected_concept_id in cases:
            with self.subTest(query=query):
                results = retrieve_context(query, limit=3)

                self.assertTrue(results)
                self.assertEqual(results[0].concept_id, expected_concept_id)


if __name__ == "__main__":
    unittest.main()
