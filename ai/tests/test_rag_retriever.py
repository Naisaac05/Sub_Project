import sys
import unittest
from unittest.mock import patch

from app.ollama.embeddings import EmbeddingError
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
from app.schemas.rag_card import RagCard, RagPayloads, RagReview, CardStatus, PayloadStatus, ConceptDefinitionPayload

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

    def retrieve(self, query: str, limit: int = 5, reranker=None) -> list[RetrievedContext]:
        self.calls.append((query, limit))
        return self.results[:limit]


class RerankingFallbackRetriever:
    def __init__(self, results: list[RetrievedContext]):
        self.results = results

    def retrieve(self, query: str, limit: int = 5, reranker=None) -> list[RetrievedContext]:
        results = self.results[:limit]
        if reranker is not None:
            results = reranker(results, query)
        return results[:limit]


def card(concept_id: str, title: str, body: str, keywords: str = "") -> RagCard:
    # Use RagCard structure
    # body -> concept_definition
    # keywords -> boost_keywords
    import datetime
    from app.schemas.rag_card import RagRetrieval

    cd = ConceptDefinitionPayload(content=body)
    payloads = RagPayloads(CONCEPT_DEFINITION=cd)
    review = RagReview(card_status=CardStatus.APPROVED, payload_status={"CONCEPT_DEFINITION": PayloadStatus.APPROVED})
    retrieval = RagRetrieval(boost_keywords=[keywords] if keywords else [])

    return RagCard(
        card_id=concept_id,
        category="test",
        term=title,
        aliases=[],
        source_question_ids=[],
        retrieval=retrieval,
        payloads=payloads,
        review=review,
        related_card_ids=[],
        tags=[]
    )


class RagRetrieverTest(unittest.TestCase):
    def test_exact_term_ranks_above_partial_specific_term(self):
        general = card("java-extends", "extends", "", "java extends")
        specific = card("java-extends-keyword", "extends-keyword", "", "java extends keyword")
        retriever = LexicalRetrieverAdapter(card_loader=lambda: [specific, general])

        results = retriever.retrieve("extends", limit=2)

        self.assertEqual(results[0].concept_id, "java-extends")

    def test_exact_alias_phrase_ranks_above_category_only_overlap(self):
        from app.schemas.rag_card import RagRetrieval

        cache = card("spring-cache-evict", "cache-evict", "", "spring cache cache evict")
        cache.aliases = ["spring cache", "cache eviction"]
        cache.retrieval = RagRetrieval(boost_keywords=["spring cache", "cache", "eviction"])
        scope = card("spring-bean-scope", "spring-bean-scope", "", "spring bean scope")
        retriever = LexicalRetrieverAdapter(card_loader=lambda: [scope, cache])

        results = retriever.retrieve("Spring cache", limit=2)

        self.assertEqual(results[0].concept_id, "spring-cache-evict")

    def test_korean_exact_alias_phrase_ranks_matching_card_first(self):
        primitive = card("java-primitive", "primitive", "", "primitive")
        primitive.aliases = ["Java 기본 자료형이 아닌 것은"]
        array = card("java-array-length", "array-length", "", "java array")
        array.aliases = ["Java 배열 길이"]
        retriever = LexicalRetrieverAdapter(card_loader=lambda: [array, primitive])

        results = retriever.retrieve("다음 중 Java의 기본 자료형이 아닌 것은?", limit=2)

        self.assertEqual(results[0].concept_id, "java-primitive")

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

    def test_retrieval_entrypoints_return_empty_for_nonpositive_limit(self):
        lexical = LexicalRetrieverAdapter(
            card_loader=lambda: [
                card("spring-n-plus-one", "N+1", "fetch join", "n+1"),
                card("spring-fetch-join", "Fetch Join", "fetch join", "fetch"),
            ]
        )
        bm25 = BM25RetrieverAdapter(
            card_loader=lambda: [
                card("spring-n-plus-one", "N+1", "fetch join", "n+1"),
                card("spring-fetch-join", "Fetch Join", "fetch join", "fetch"),
            ]
        )
        child = StaticRetriever(
            [
                RetrievedContext("child-a", "Child A", "child context", 1.0, {"source": "a"}),
                RetrievedContext("child-b", "Child B", "child context", 0.5, {"source": "b"}),
            ]
        )
        hybrid = HybridRetrieverAdapter([WeightedRetriever("child", 1.0, child.retrieve)])
        fake = FakeAdapter()

        for limit in (0, -1):
            with self.subTest(limit=limit):
                self.assertEqual(lexical.retrieve("n+1", limit=limit), [])
                self.assertEqual(bm25.retrieve("n+1", limit=limit), [])
                self.assertEqual(hybrid.retrieve("query", limit=limit), [])
                self.assertEqual(retrieve_context("query", limit=limit, adapter=fake), [])

    def test_default_and_lexical_selector_use_lexical_adapter(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertIsInstance(select_retriever_adapter(), LexicalRetrieverAdapter)

        self.assertIsInstance(select_retriever_adapter("lexical"), LexicalRetrieverAdapter)

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

    def test_lexical_retriever_copies_card_metadata(self):
        source_card = card("spring-n-plus-one", "N+1", "fetch join", "n+1")
        retriever = LexicalRetrieverAdapter(card_loader=lambda: [source_card])

        results = retriever.retrieve("n+1", limit=1)
        results[0].metadata["mutated"] = "yes"

        self.assertNotIn("mutated", source_card.metadata)

    def test_chroma_adapter_returns_empty_when_disabled_or_unavailable(self):
        adapter = ChromaBgeRetrieverAdapter(enabled=False)

        self.assertEqual(adapter.retrieve("n+1", limit=3), [])

    def test_flashrank_reranker_is_disabled_by_default(self):
        self.assertIsNone(build_optional_reranker("none"))

    def test_flashrank_reranker_falls_back_when_dependency_missing(self):
        reranker = FlashrankReranker(enabled=False)
        items = [RetrievedContext("a", "A", "alpha", 1.0, {})]

        self.assertEqual(reranker(items, "alpha"), items)

    def test_hybrid_child_exception_uses_configured_fallback(self):
        fallback_item = RetrievedContext(
            "fallback", "Fallback", "fallback context", 1.0, {"retriever": "fallback"}
        )
        fallback = StaticRetriever([fallback_item])

        def fail_retrieve(query: str, limit: int) -> list[RetrievedContext]:
            raise RuntimeError("child failed")

        adapter = HybridRetrieverAdapter(
            [WeightedRetriever("broken", 1.0, fail_retrieve)],
            fallback=fallback,
        )

        results = adapter.retrieve("query", limit=1)

        self.assertEqual(results, [fallback_item])
        self.assertEqual(fallback.calls, [("query", 1)])
        self.assertEqual(results[0].metadata, {"retriever": "fallback"})

    def test_unknown_query_returns_empty_list(self):
        results = retrieve_context("unrelated grocery shopping", limit=2)

        self.assertEqual(results, [])

    def test_generic_korean_question_returns_empty_list(self):
        results = retrieve_context("이게 뭐야?", limit=3)

        self.assertEqual(results, [])

if __name__ == "__main__":
    unittest.main()
