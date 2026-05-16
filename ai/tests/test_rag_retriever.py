import unittest

from app.rag.retriever import retrieve_context


class RagRetrieverTest(unittest.TestCase):
    def test_retrieves_n_plus_one_card_first(self):
        results = retrieve_context("N+1 문제에서 지연 로딩 때문에 추가 쿼리가 생기는 이유", limit=2)

        self.assertGreaterEqual(len(results), 1)
        self.assertEqual(results[0].concept_id, "spring-n-plus-one")

    def test_unknown_query_returns_empty_list(self):
        results = retrieve_context("완전히 관계없는 우주선 연료 문제", limit=2)

        self.assertEqual(results, [])


if __name__ == "__main__":
    unittest.main()

