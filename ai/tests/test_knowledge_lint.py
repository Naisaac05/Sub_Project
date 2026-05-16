import tempfile
import unittest
from pathlib import Path

from app.rag.documents import ConceptCard
from scripts.lint_knowledge_cards import lint_cards


class KnowledgeLintTest(unittest.TestCase):
    def test_valid_bundled_cards_pass_lint(self):
        errors = lint_cards()
        self.assertEqual(errors, [])

    def test_duplicate_concept_ids_fail_lint(self):
        card_a = ConceptCard(
            path=Path("a.md"),
            concept_id="dup",
            metadata={
                "id": "dup",
                "category": "spring",
                "difficulty": "beginner",
                "version": "java17",
                "last_updated": "2026-05-16",
            },
            title="A",
            sections={
                "핵심 설명": "설명",
                "대표 해결": "- 해결",
                "흔한 오해": "- 오해",
                "평가 키워드": "- 하나\n- 둘",
            },
        )
        card_b = ConceptCard(
            path=Path("b.md"),
            concept_id="dup",
            metadata=card_a.metadata,
            title="B",
            sections=card_a.sections,
        )

        errors = lint_cards([card_a, card_b], approved_qa_root=Path(tempfile.mkdtemp()))

        self.assertTrue(any("duplicate concept id" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

