from __future__ import annotations

import copy
import unittest

from app.scripts import apply_and_lock_factchecked_next20 as apply


class ApplyAndLockFactcheckedNext20Test(unittest.TestCase):
    def test_candidate_requires_approved_source_status(self):
        candidate, reasons = apply.build_locked_candidate(_card("draft"), {"payloads": _card("draft")["payloads"]})

        self.assertIsNone(candidate)
        self.assertEqual(reasons, ["transition_requires_approved"])

    def test_candidate_changes_payload_review_and_updated_at_only(self):
        original = _card("approved")
        patch = {"payloads": copy.deepcopy(original["payloads"])}
        patch["payloads"]["EXAMPLE_REQUEST"]["explanation"] = "개선된 설명"

        candidate, reasons = apply.build_locked_candidate(original, patch, updated_at="2026-06-15T00:00:00Z")

        self.assertEqual(reasons, [])
        self.assertEqual(candidate["review"]["card_status"], "approved_locked")
        self.assertEqual(candidate["updated_at"], "2026-06-15T00:00:00Z")
        self.assertEqual(candidate["retrieval"], original["retrieval"])
        self.assertEqual(candidate["aliases"], original["aliases"])


def _card(status: str) -> dict:
    return {
        "card_id": "sample", "category": "java", "term": "sample", "aliases": ["sample"],
        "source_question_ids": ["java:1"],
        "retrieval": {"embedding_text": "sample", "embedding_hash": "", "boost_keywords": ["sample"], "intent_types": []},
        "payloads": {
            "CONCEPT_DEFINITION": {"content": "개념을 정의하고 원리와 예시를 구체적으로 설명한다.", "examples": ["작은 입력으로 확인한다."]},
            "ANSWER_REASON": {"why_correct": "원리를 적용하면 정답을 도출하며 오답과 적용 범위가 다르다.", "key_points": ["개념"]},
            "WRONG_ANSWER_REASON": {"common_mistakes": [], "per_option": {"a": {"text": "오답", "reason": "적용 범위가 달라 정답이 아니다."}}},
            "EXAMPLE_REQUEST": {"code_example": "int value = 1;\nvalue += 1;\nassert value == 2;", "explanation": "동작 검증"},
        },
        "review": {"card_status": status, "payload_status": {}, "approved_at": None, "reviewer": None, "rejected_reason": None},
        "related_card_ids": [], "tags": [],
        "created_at": "2026-06-12T00:00:00Z", "updated_at": "2026-06-12T00:00:00Z",
    }


if __name__ == "__main__":
    unittest.main()
