import tempfile
import unittest
from pathlib import Path

from app.rag.documents import ConceptCard
from scripts.reindex_knowledge import (
    ChromaIndexDependencies,
    build_chroma_documents,
    index_chroma_knowledge,
)


def card(concept_id: str = "spring-n-plus-one") -> ConceptCard:
    return ConceptCard(
        path=Path("concepts/spring/n-plus-one.md"),
        concept_id=concept_id,
        metadata={"id": concept_id, "category": "spring-jpa"},
        title="N+1",
        sections={
            "핵심 설명": "N+1 happens when lazy loading causes repeated queries.",
            "평가 키워드": "fetch join batch size",
        },
    )


class FakeCollection:
    def __init__(self):
        self.upserts = []
        self.query_payload = {
            "ids": [["spring-n-plus-one"]],
            "documents": [["# N+1"]],
            "metadatas": [[{"concept_id": "spring-n-plus-one", "title": "N+1"}]],
            "distances": [[0.1]],
        }

    def upsert(self, **kwargs):
        self.upserts.append(kwargs)

    def query(self, **kwargs):
        self.last_query = kwargs
        return self.query_payload


class FakeClient:
    def __init__(self):
        self.collection = FakeCollection()

    def get_or_create_collection(self, name: str):
        self.collection_name = name
        return self.collection


class ChromaReindexTest(unittest.TestCase):
    def test_build_chroma_documents_uses_section_context_and_metadata(self):
        documents = build_chroma_documents([card()])

        self.assertEqual(len(documents), 1)
        self.assertEqual(documents[0]["id"], "spring-n-plus-one")
        self.assertIn("## 핵심 설명", documents[0]["document"])
        self.assertEqual(documents[0]["metadata"]["concept_id"], "spring-n-plus-one")

    def test_index_chroma_knowledge_upserts_embeddings_without_real_chroma(self):
        fake_client = FakeClient()
        deps = ChromaIndexDependencies(
            client_factory=lambda path: fake_client,
            embed=lambda texts: [[0.1, 0.2, 0.3] for _ in texts],
        )

        report = index_chroma_knowledge(
            cards=[card()],
            persist_path=Path("fake-chroma"),
            dependencies=deps,
        )

        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["indexed"], 1)
        upsert = fake_client.collection.upserts[0]
        self.assertEqual(upsert["ids"], ["spring-n-plus-one"])
        self.assertEqual(upsert["embeddings"], [[0.1, 0.2, 0.3]])

    def test_index_chroma_knowledge_can_reindex_one_concept_id(self):
        fake_client = FakeClient()
        deps = ChromaIndexDependencies(
            client_factory=lambda path: fake_client,
            embed=lambda texts: [[0.1, 0.2, 0.3] for _ in texts],
        )

        report = index_chroma_knowledge(
            cards=[card("spring-n-plus-one"), card("auto-review-pagination")],
            concept_ids=["auto-review-pagination"],
            persist_path=Path("fake-chroma"),
            dependencies=deps,
        )

        self.assertEqual(report["status"], "passed")
        self.assertEqual(report["indexed"], 1)
        upsert = fake_client.collection.upserts[0]
        self.assertEqual(upsert["ids"], ["auto-review-pagination"])

    def test_index_chroma_knowledge_reports_missing_filtered_concept_id(self):
        deps = ChromaIndexDependencies(
            client_factory=lambda path: FakeClient(),
            embed=lambda texts: [[0.1, 0.2, 0.3] for _ in texts],
        )

        report = index_chroma_knowledge(
            cards=[card("spring-n-plus-one")],
            concept_ids=["auto-review-pagination"],
            dependencies=deps,
        )

        self.assertEqual(report["status"], "failed")
        self.assertEqual(report["indexed"], 0)
        self.assertIn("auto-review-pagination", report["errors"][0])

    def test_index_chroma_knowledge_smoke_queries_after_upsert(self):
        fake_client = FakeClient()
        deps = ChromaIndexDependencies(
            client_factory=lambda path: fake_client,
            embed=lambda texts: [[0.1, 0.2, 0.3] for _ in texts],
        )

        report = index_chroma_knowledge(
            cards=[card()],
            persist_path=Path("fake-chroma"),
            dependencies=deps,
            smoke_query="N+1 fetch join",
        )

        self.assertEqual(report["smoke"]["status"], "passed")
        self.assertEqual(report["smoke"]["top_concept_id"], "spring-n-plus-one")

    def test_index_chroma_knowledge_reports_disabled_when_dependencies_missing(self):
        deps = ChromaIndexDependencies(
            client_factory=lambda path: (_ for _ in ()).throw(ImportError("chromadb missing")),
            embed=lambda texts: [[0.1] for _ in texts],
        )

        report = index_chroma_knowledge(cards=[card()], dependencies=deps)

        self.assertEqual(report["status"], "skipped")
        self.assertIn("chromadb missing", report["reason"])


if __name__ == "__main__":
    unittest.main()
