import unittest

from app.schemas.rag_card import RagCard, RagRetrieval
from scripts.audit_rag_cards_v2 import audit_cards, audit_retrieval_queries


def card(card_id, category, term, aliases=None, keywords=None, source_question_ids=None):
    return RagCard(
        card_id=card_id,
        category=category,
        term=term,
        aliases=aliases or [],
        source_question_ids=source_question_ids or [],
        retrieval=RagRetrieval(
            embedding_text=" ".join([term, *(aliases or []), category, *(keywords or [])]),
            boost_keywords=keywords or [],
        ),
    )


class AuditRagCardsV2Test(unittest.TestCase):
    def test_holds_broad_and_duplicate_terms(self):
        cards = [
            card("frontend-key", "frontend", "key", ["react key"], ["key", "react", "reconciliation"]),
            card("java-equals", "java", "equals", ["object equality"], ["equals", "java", "equality"]),
            card("java-equals-2", "java", "equals", ["equals"], ["equals", "java", "comparison"]),
        ]

        report = audit_cards(cards)

        self.assertEqual(report.approved_candidate_ids, [])
        self.assertEqual(set(report.held_card_ids), {"frontend-key", "java-equals", "java-equals-2"})
        self.assertIn("broad_term", report.risks_by_card["frontend-key"])
        self.assertIn("duplicate_normalized_term", report.risks_by_card["java-equals"])

    def test_approves_specific_card_with_distinctive_retrieval_fields(self):
        specific = card(
            "spring-transactional-read-only",
            "spring",
            "transactional-read-only",
            ["read only transaction", "transaction optimization"],
            ["transactional", "read-only", "spring", "flush", "dirty-checking"],
        )

        report = audit_cards([specific])

        self.assertEqual(report.approved_candidate_ids, [specific.card_id])
        self.assertEqual(report.held_card_ids, [])

    def test_logs_top_five_for_priority_queries(self):
        cards = [
            card("frontend-react-key", "frontend", "react-key", ["react key"], ["react", "key", "reconciliation"]),
            card("java-object-equals", "java", "object-equals", ["java equals"], ["java", "equals", "equality"]),
        ]

        results = audit_retrieval_queries(cards, ["React key", "Java equals"])

        self.assertEqual(results["React key"][0]["card_id"], "frontend-react-key")
        self.assertEqual(results["Java equals"][0]["card_id"], "java-object-equals")

    def test_holds_card_with_mojibake_in_retrieval_fields(self):
        broken = card(
            "frontend-useeffect",
            "frontend",
            "useeffect",
            ["use effect", "react effect"],
            ["useeffect", "frontend", "留덉슫??"],
        )

        report = audit_cards([broken])

        self.assertIn("mojibake_retrieval_fields", report.risks_by_card[broken.card_id])

    def test_normal_korean_question_mark_is_not_mojibake(self):
        normal = card(
            "java-primitive",
            "java",
            "primitive",
            ["Java 기본 자료형이 아닌 것은?", "primitive type"],
            ["primitive", "기본 자료형", "String"],
        )

        report = audit_cards([normal])

        self.assertNotIn("mojibake_retrieval_fields", report.risks_by_card.get(normal.card_id, []))

    def test_holds_manually_confirmed_cross_concept_merge(self):
        merged = card(
            "frontend-project-tool",
            "frontend",
            "project-tool",
            ["useref"],
            ["project", "frontend", "maven", "useref"],
            ["frontend:69", "frontend:77"],
        )

        report = audit_cards([merged])

        self.assertIn("suspicious_cross_concept_merge", report.risks_by_card[merged.card_id])

if __name__ == "__main__":
    unittest.main()
