from __future__ import annotations

import copy
import unittest

from app.scripts import promote_factchecked_next20_ready as promote


class PromoteFactcheckedNext20ReadyTest(unittest.TestCase):
    def test_promotes_only_cards_passing_execution_and_example_quality(self):
        source = {
            "good": _draft(example_quality=0.75, execution_passed=True),
            "weak": _draft(example_quality=0.50, execution_passed=True),
            "failed": _draft(example_quality=1.0, execution_passed=False),
        }

        report = promote.build_ready_report(source, valid_sources={"course:1"})

        self.assertEqual(set(report["PATCHES_READY"]), {"good"})
        self.assertEqual(set(report["PREPARATION_BACKLOG"]), {"weak", "failed"})
        self.assertIn("example_quality_below_0_7", report["failed_review"]["weak"])
        self.assertIn("execution_failed", report["failed_review"]["failed"])

    def test_ready_patch_contains_payload_only_change_and_review_metadata(self):
        report = promote.build_ready_report({"good": _draft()}, valid_sources={"course:1"})
        patch = report["PATCHES_READY"]["good"]

        self.assertEqual(
            set(patch),
            {"payloads", "fact_check_notes", "patch_reason", "source_link", "quality_review"},
        )
        self.assertFalse(report["card_files_modified"])
        self.assertFalse(report["approval_status_changed"])
        self.assertFalse(report["execution_performed"])


def _draft(*, example_quality: float = 1.0, execution_passed: bool = True) -> dict:
    return {
        "course_id": "course",
        "test_id": 1,
        "question_id": 1,
        "source_question_id": "course:1",
        "payloads": {
            "CONCEPT_DEFINITION": {"content": "정의와 원리를 설명하고 짧은 예시를 제시한다.", "examples": ["작은 입력으로 확인한다."]},
            "ANSWER_REASON": {"why_correct": "개념 원리로 정답을 도출하고 다른 선택지와의 차이를 설명한다.", "key_points": ["개념", "정답"]},
            "WRONG_ANSWER_REASON": {
                "common_mistakes": ["역할을 혼동한다."],
                "per_option": {"a": {"text": "오답", "reason": "오답은 적용 대상이 달라 정답 개념이 아니다."}},
            },
            "EXAMPLE_REQUEST": {"code_example": "values = [1]\nvalues.append(2)\nassert values[-1] == 2", "explanation": "동작 확인"},
        },
        "fact_check_notes": ["정답 확인", "선택지 확인"],
        "patch_reason": "설명 개선",
        "quality": {
            "same_reason_ratio": 0.0,
            "example_metrics": {"example_quality": example_quality, "fake_example_score": 1.0 - example_quality, "print_answer": 0.0},
            "execution": {"passed": execution_passed, "reason": None if execution_passed else "failed"},
        },
    }


if __name__ == "__main__":
    unittest.main()
