from __future__ import annotations

import copy
import unittest

from app.scripts import initialize_validation_policy_v212 as policy


def sample_card() -> dict:
    return {
        "card_id": "python-range",
        "category": "python",
        "term": "range",
        "source_question_ids": ["q1"],
        "retrieval": {
            "embedding_text": "Python range 종료값 제외",
            "embedding_hash": "hash",
            "intent_types": ["CONCEPT_DEFINITION"],
            "boost_keywords": ["range", "종료값"],
        },
        "aliases": ["range 함수"],
        "created_at": "2026-06-13T00:00:00Z",
        "related_card_ids": ["python-loop"],
        "payloads": {
            "CONCEPT_DEFINITION": {
                "content": "range는 시작값부터 종료값 직전까지 정수를 생성하며 반복 범위를 구성한다.",
                "examples": ["range(1, 4)는 1, 2, 3을 생성한다."],
            },
            "ANSWER_REASON": {
                "why_correct": "종료값은 포함되지 않으므로 마지막 값은 3이다. 4를 포함한다는 보기는 경계 규칙을 혼동한다.",
                "key_points": ["종료값 제외", "반복 범위"],
            },
            "WRONG_ANSWER_REASON": {
                "common_mistakes": ["종료값 포함 여부를 혼동한다."],
                "per_option": {
                    "a": {"text": "4를 포함한다", "reason": "종료값은 생성 범위에서 제외되므로 4는 포함되지 않는다."},
                    "b": {"text": "0부터 시작한다", "reason": "시작값을 1로 지정했으므로 0부터 시작하지 않는다."},
                },
            },
            "EXAMPLE_REQUEST": {
                "code_example": "values = list(range(1, 4))\nlast = values[-1]\nassert last == 3",
                "explanation": "종료값 직전까지 생성되는 동작을 검증한다.",
            },
        },
        "review": {"card_status": "approved"},
        "updated_at": "2026-06-13T00:00:00Z",
    }


class InitializeValidationPolicyV212Test(unittest.TestCase):
    def test_duplicate_score_ignores_source_option_text_similarity(self):
        card = sample_card()
        card["payloads"]["WRONG_ANSWER_REASON"]["per_option"] = {
            "option_0": {
                "text": "thenApply는 값 변환, thenCompose는 비동기 체이닝",
                "reason": "두 메서드가 같다고 보면 반환 구조의 차이를 설명하지 못한다.",
            },
            "option_1": {
                "text": "thenCompose는 값 변환, thenApply는 비동기 체이닝",
                "reason": "역할을 반대로 배치해 각 메서드가 받는 함수 형태와 맞지 않는다.",
            },
        }

        result = policy.validate_payload_quality(card)

        self.assertNotIn("duplicate_score_over_0_25", result["reasons"])

    def test_searchable_checksum_changes_only_when_searchable_input_changes(self):
        before = sample_card()
        allowed = copy.deepcopy(before)
        allowed["payloads"]["CONCEPT_DEFINITION"]["content"] = "개선 설명"
        allowed["review"]["reviewer"] = "dry-run"
        allowed["updated_at"] = "2026-06-14T00:00:00Z"

        self.assertEqual(policy.searchable_checksum(before), policy.searchable_checksum(allowed))

        allowed["aliases"].append("파이썬 범위")
        self.assertNotEqual(policy.searchable_checksum(before), policy.searchable_checksum(allowed))

    def test_validate_lock_allows_payload_review_updated_at_only(self):
        before = sample_card()
        after = copy.deepcopy(before)
        after["payloads"]["CONCEPT_DEFINITION"]["content"] = "개선 설명"
        after["review"]["reviewer"] = "dry-run"
        after["updated_at"] = "2026-06-14T00:00:00Z"

        self.assertEqual(policy.validate_lock(before, after), [])

    def test_validate_lock_reports_checksum_and_semantic_shift(self):
        before = sample_card()
        after = copy.deepcopy(before)
        after["retrieval"]["embedding_text"] = "다른 검색 의미"

        reasons = policy.validate_lock(before, after)

        self.assertIn("lock_violation:retrieval.embedding_text", reasons)
        self.assertIn("searchable_checksum_changed", reasons)
        self.assertIn("semantic_shift", reasons)

    def test_batch_transition_allows_zero_loss_dry_run(self):
        report = policy.empty_validation_report()

        decision = policy.batch_transition(report, current_batch=10)

        self.assertTrue(decision["allow_next_batch"])
        self.assertEqual(decision["next_batch"], 20)

    def test_batch_transition_freezes_when_factcheck_is_not_ready(self):
        report = policy.empty_validation_report()
        report["failure_flags"] = ["factcheck_not_ready"]

        decision = policy.batch_transition(report, current_batch=10)

        self.assertFalse(decision["allow_next_batch"])
        self.assertEqual(decision["next_batch"], 10)
        self.assertIn("factcheck_not_ready", decision["freeze_reasons"])

    def test_retrieval_abort_reasons_use_policy_thresholds(self):
        losses = {"production_hit1_diff": 0.011, "production_loo_diff": 0.006, "exact_diff": 0.006}

        reasons = policy.retrieval_abort_reasons(losses)

        self.assertEqual(
            reasons,
            [
                "hit1_decreased_over_1_percent",
                "loo_decreased_over_0_5_percent",
                "exact_decreased_over_0_5_percent",
            ],
        )

    def test_payload_quality_rejects_repeated_answer_and_fake_example(self):
        card = sample_card()
        card["payloads"]["ANSWER_REASON"]["why_correct"] = card["payloads"]["CONCEPT_DEFINITION"]["content"]
        for option in card["payloads"]["WRONG_ANSWER_REASON"]["per_option"].values():
            option["reason"] = "관련 없음. 정답이 아님."
        card["payloads"]["EXAMPLE_REQUEST"]["code_example"] = 'print("정답")'

        result = policy.validate_payload_quality(card)

        self.assertIn("answer_overlap_over_30_percent", result["reasons"])
        self.assertIn("same_reason_ratio_over_25_percent", result["reasons"])
        self.assertIn("fake_example", result["reasons"])

    def test_payload_quality_accepts_concrete_explanation_and_executable_example(self):
        result = policy.validate_payload_quality(sample_card())

        self.assertEqual(result["reasons"], [])
        self.assertLessEqual(result["answer_overlap"], 0.30)
        self.assertLessEqual(result["same_reason_ratio"], 0.25)
        self.assertEqual(result["fake_example_score"], 0.0)

    def test_answer_comparison_accepts_direct_wrong_option_reference(self):
        card = sample_card()
        card["payloads"]["ANSWER_REASON"]["why_correct"] = (
            "DFS는 최근 정점을 먼저 방문하므로 스택을 이용한다. 큐는 먼저 발견한 정점을 처리해 BFS 순서를 만든다."
        )
        card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]["a"]["text"] = "큐"

        result = policy.validate_payload_quality(card)

        self.assertNotIn("answer_wrong_comparison_missing", result["reasons"])

    def test_answer_comparison_accepts_distinctive_wrong_option_term(self):
        card = sample_card()
        card["payloads"]["ANSWER_REASON"]["why_correct"] = (
            "입력마다 한 번씩 처리하므로 O(n)이다. O(1)은 입력 크기와 무관하고 O(log n)은 범위를 절반씩 줄인다."
        )
        card["payloads"]["WRONG_ANSWER_REASON"]["per_option"]["a"]["text"] = "항상 일정한 시간인 O(1)"

        result = policy.validate_payload_quality(card)

        self.assertNotIn("answer_wrong_comparison_missing", result["reasons"])

    def test_auto_downgrade_allows_only_critical_failures(self):
        self.assertTrue(policy.auto_downgrade_allowed(["invalid_json"]))
        self.assertTrue(policy.auto_downgrade_allowed(["retrieval_break"]))
        self.assertFalse(policy.auto_downgrade_allowed(["payload_length_under_120"]))
        self.assertFalse(policy.auto_downgrade_allowed(["answer_overlap_over_30_percent"]))


if __name__ == "__main__":
    unittest.main()
