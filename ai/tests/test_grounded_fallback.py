import unittest
from unittest.mock import patch

from app.schemas.rag_card import RagCard
from app.workflow.grounded_fallback import (
    SAFE_GROUNDED_FALLBACK_ANSWER,
    build_grounded_answer_from_evidence,
    grounded_fallback_enabled,
    select_grounded_evidence,
    validate_grounded_answer,
)


def card(card_id: str, term: str, definition: str, *, status: str = "approved", aliases=None):
    return RagCard.model_validate({
        "card_id": card_id,
        "category": "java",
        "term": term,
        "aliases": aliases or [term],
        "source_question_ids": ["java:1"],
        "retrieval": {"embedding_text": term, "boost_keywords": [term], "intent_types": ["CONCEPT_DEFINITION"]},
        "payloads": {"CONCEPT_DEFINITION": {"content": definition, "examples": []}},
        "review": {"card_status": status, "payload_status": {"CONCEPT_DEFINITION": "approved" if status == "approved" else "draft"}},
    })


class GroundedFallbackTest(unittest.TestCase):
    def test_grounded_fallback_is_enabled_by_default_and_can_be_disabled(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertTrue(grounded_fallback_enabled())
        with patch.dict("os.environ", {"AI_REVIEW_GROUNDED_FALLBACK_ENABLED": "false"}):
            self.assertFalse(grounded_fallback_enabled())

    def test_selects_only_strong_approved_evidence(self):
        approved = card("java-copy", "CopyOnWriteArrayList", "쓰기 시 내부 배열을 복사하고 읽기는 스냅샷을 조회한다.")
        draft = card("java-copy-draft", "CopyOnWriteArrayList", "검토 전 설명", status="draft")

        evidence = select_grounded_evidence(
            "CopyOnWriteArrayList가 뭐야?",
            cards=[draft, approved],
        )

        self.assertIsNotNone(evidence)
        self.assertEqual(evidence.card_id, "java-copy")
        self.assertEqual(evidence.content, "쓰기 시 내부 배열을 복사하고 읽기는 스냅샷을 조회한다.")

    def test_rejects_weak_or_ambiguous_evidence(self):
        cards = [
            card("java-list-a", "ArrayList", "ArrayList 설명", aliases=["collection"]),
            card("java-list-b", "LinkedList", "LinkedList 설명", aliases=["collection"]),
        ]

        evidence = select_grounded_evidence("Java collection 사용법", cards=cards)

        self.assertIsNone(evidence)

    def test_quality_gate_accepts_grounded_complete_answer(self):
        evidence = select_grounded_evidence(
            "CopyOnWriteArrayList가 뭐야?",
            cards=[card("java-copy", "CopyOnWriteArrayList", "쓰기 작업마다 내부 배열을 복사하고 읽기는 스냅샷을 조회한다.")],
        )
        answer = "CopyOnWriteArrayList는 쓰기 작업마다 내부 배열을 복사하며, 읽기는 안정된 스냅샷을 조회합니다. 따라서 읽기가 많고 쓰기가 적은 환경에 적합합니다."

        result = validate_grounded_answer("CopyOnWriteArrayList가 뭐야?", answer, evidence)

        self.assertTrue(result.passed)
        self.assertEqual(result.reasons, ())

    def test_builds_grounded_answer_that_passes_quality_gate(self):
        evidence = select_grounded_evidence(
            "React key를 리스트에서 사용할 때 주의점을 설명해줘.",
            cards=[
                card(
                    "frontend-react-key",
                    "react-key",
                    "React의 key는 같은 부모 아래 리스트 형제 요소를 렌더링 사이에서 식별하는 안정적인 값이다.",
                    aliases=["react key", "list key"],
                )
            ],
        )

        answer = build_grounded_answer_from_evidence(
            "React key를 리스트에서 사용할 때 주의점을 설명해줘.",
            evidence,
        )
        result = validate_grounded_answer("React key를 리스트에서 사용할 때 주의점을 설명해줘.", answer, evidence)

        self.assertTrue(result.passed)
        self.assertIn("React key", answer)
        self.assertIn("리스트", answer)

    def test_builds_grounded_answer_with_concise_card_term_when_alias_is_question(self):
        evidence = select_grounded_evidence(
            "Python asyncio를 비동기 작업에서 사용할 때 핵심을 설명해줘.",
            cards=[
                card(
                    "python-asyncio",
                    "asyncio",
                    "asyncio는 Python에서 이벤트 루프와 코루틴으로 비동기 I/O 작업을 조율하는 표준 라이브러리이며, await는 코루틴을 일시 중단한다.",
                    aliases=["asyncio", "asyncio에서 await 키워드의 역할은?", "await", "python asyncio"],
                )
            ],
        )

        answer = build_grounded_answer_from_evidence(
            "Python asyncio를 비동기 작업에서 사용할 때 핵심을 설명해줘.",
            evidence,
        )

        self.assertTrue(answer.startswith("asyncio는 "))
        self.assertFalse(answer.startswith("asyncio는 asyncio는"))
        self.assertNotIn("역할은?는", answer)

    def test_quality_gate_rejects_ungrounded_or_incomplete_answer(self):
        evidence = select_grounded_evidence(
            "CopyOnWriteArrayList가 뭐야?",
            cards=[card("java-copy", "CopyOnWriteArrayList", "쓰기 작업마다 내부 배열을 복사하고 읽기는 스냅샷을 조회한다.")],
        )

        result = validate_grounded_answer(
            "CopyOnWriteArrayList가 뭐야?",
            "CopyOnWriteArrayList는 Spring 트랜잭션 기능으로",
            evidence,
        )

        self.assertFalse(result.passed)
        self.assertIn("insufficient_evidence_overlap", result.reasons)
        self.assertIn("incomplete_answer", result.reasons)
        self.assertNotIn("Spring", SAFE_GROUNDED_FALLBACK_ANSWER)


if __name__ == "__main__":
    unittest.main()
