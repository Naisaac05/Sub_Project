from __future__ import annotations

import copy
import unittest

from app.scripts import dryrun_factchecked_next20_approval as dryrun


class DryrunFactcheckedNext20ApprovalTest(unittest.TestCase):
    def test_build_candidate_changes_payload_only(self):
        original = _card("approved")
        patch = {"payloads": copy.deepcopy(original["payloads"])}
        patch["payloads"]["EXAMPLE_REQUEST"]["explanation"] = "개선된 설명"

        candidate = dryrun.build_candidate(original, patch)

        self.assertEqual(candidate["payloads"], patch["payloads"])
        self.assertEqual(candidate["review"], original["review"])
        self.assertEqual(candidate["retrieval"], original["retrieval"])
        self.assertEqual(candidate["aliases"], original["aliases"])

    def test_strict_retrieval_requires_every_metric_diff_to_be_zero(self):
        self.assertEqual(dryrun.retrieval_diff_reasons({"production_hit1_diff": 0.0}), [])
        self.assertEqual(
            dryrun.retrieval_diff_reasons(
                {"production_hit1_diff": 0.0, "content_loo_diff": -0.0001}
            ),
            ["retrieval_changed:content_loo_diff"],
        )

    def test_approval_locked_eligibility_requires_approved_status_and_all_gates(self):
        self.assertEqual(
            dryrun.approval_decision("draft", []),
            {
                "eligible": False,
                "result": "quality_verified_retrieval_passed",
                "reasons": ["transition_requires_approved"],
            },
        )
        self.assertEqual(
            dryrun.approval_decision("approved", []),
            {"eligible": True, "result": "approved_locked_candidate", "reasons": []},
        )
        self.assertEqual(
            dryrun.approval_decision("approved", ["fake_example"]),
            {"eligible": False, "result": "retrieval_pending", "reasons": ["fake_example"]},
        )

    def test_quality_gate_summary_includes_manifest_quality_failures(self):
        self.assertTrue(dryrun.quality_gate_passed({"quality_reasons": []}))
        self.assertFalse(
            dryrun.quality_gate_passed({"quality_reasons": ["fake_example_score_nonzero"]})
        )


def _card(status: str) -> dict:
    return {
        "card_id": "sample",
        "category": "java",
        "term": "sample",
        "aliases": ["sample"],
        "source_question_ids": ["java:1"],
        "retrieval": {
            "embedding_text": "sample",
            "embedding_hash": "",
            "boost_keywords": ["sample"],
            "intent_types": [],
        },
        "payloads": {
            "CONCEPT_DEFINITION": {
                "content": "개념을 정의하고 원리와 예시를 구체적으로 설명한다.",
                "examples": ["작은 입력으로 확인한다."],
            },
            "ANSWER_REASON": {
                "why_correct": "원리를 적용하면 결론을 도출하며 다른 보기와 적용 범위가 다르다.",
                "key_points": ["개념"],
            },
            "WRONG_ANSWER_REASON": {
                "common_mistakes": [],
                "per_option": {
                    "a": {"text": "오답", "reason": "적용 범위가 달라 올바른 결론이 아니다."}
                },
            },
            "EXAMPLE_REQUEST": {
                "code_example": "int value = 1;\nvalue += 1;\nassert value == 2;",
                "explanation": "동작 검증",
            },
        },
        "review": {
            "card_status": status,
            "payload_status": {},
            "approved_at": None,
            "reviewer": None,
            "rejected_reason": None,
        },
        "related_card_ids": [],
        "tags": [],
        "created_at": "2026-06-12T00:00:00Z",
        "updated_at": "2026-06-12T00:00:00Z",
    }


if __name__ == "__main__":
    unittest.main()
